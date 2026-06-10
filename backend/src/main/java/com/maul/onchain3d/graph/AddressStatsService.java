package com.maul.onchain3d.graph;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Aggregates rolling per-address statistics (in/out value, tx count, whale flag)
 * from the {@code transfer} table into the {@code address_stats} table.
 *
 * <p>All JDBC work runs on {@code Schedulers.boundedElastic()} to avoid blocking
 * the WebFlux event loop.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AddressStatsService {

    private final JdbcTemplate jdbc;

    /**
     * Refreshes {@code address_stats} for the given chain and time window.
     * Runs two aggregate passes (in-value from {@code to_addr}, out-value from
     * {@code from_addr}), then updates the whale flag based on the P95 threshold.
     *
     * @param chain  chain identifier, e.g. {@code "solana"}
     * @param window PostgreSQL interval shorthand, e.g. {@code "1h"} or {@code "24h"}
     * @return {@code Mono<Void>} that completes when the refresh is done
     */
    public Mono<Void> refreshStats(String chain, String window) {
        return Mono.fromCallable(() -> {
                    doRefresh(chain, window);
                    return null;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess(v -> log.debug("refreshStats completed chain={} window={}", chain, window))
                .doOnError(e -> log.error("refreshStats failed chain={} window={}", chain, window, e))
                .then();
    }

    private void doRefresh(String chain, String window) {
        String intervalExpr = toIntervalExpr(window);

        // Pass 1 — aggregate in-value (receiver side)
        jdbc.update("""
                INSERT INTO address_stats (address, window_label, in_value, out_value, tx_count, is_whale, updated_at)
                SELECT to_addr,
                       ?,
                       SUM(value_norm),
                       0,
                       COUNT(*),
                       false,
                       now()
                FROM transfer
                WHERE chain = ?
                  AND block_time > now() - ?::interval
                GROUP BY to_addr
                ON CONFLICT (address) DO UPDATE
                    SET in_value   = EXCLUDED.in_value,
                        tx_count   = address_stats.tx_count + EXCLUDED.tx_count,
                        updated_at = now()
                """,
                window, chain, intervalExpr);

        // Pass 2 — aggregate out-value (sender side)
        jdbc.update("""
                INSERT INTO address_stats (address, window_label, in_value, out_value, tx_count, is_whale, updated_at)
                SELECT from_addr,
                       ?,
                       0,
                       SUM(value_norm),
                       COUNT(*),
                       false,
                       now()
                FROM transfer
                WHERE chain = ?
                  AND block_time > now() - ?::interval
                GROUP BY from_addr
                ON CONFLICT (address) DO UPDATE
                    SET out_value  = EXCLUDED.out_value,
                        tx_count   = address_stats.tx_count + EXCLUDED.tx_count,
                        updated_at = now()
                """,
                window, chain, intervalExpr);

        // Pass 3 — mark whales (out_value > P95 within window)
        jdbc.update("""
                UPDATE address_stats
                SET is_whale = true
                WHERE window_label = ?
                  AND out_value > (
                      SELECT percentile_cont(0.95) WITHIN GROUP (ORDER BY out_value)
                      FROM address_stats
                      WHERE window_label = ?
                  )
                """,
                window, window);

        log.debug("doRefresh done chain={} window={}", chain, window);
    }

    /**
     * Scheduled auto-refresh every 60 s (configurable via {@code app.ingest.stats-refresh-interval}).
     * Refreshes the default {@code 1h} window for both supported chains.
     */
    @Scheduled(fixedDelayString = "${app.ingest.stats-refresh-interval:60000}")
    public void scheduledRefresh() {
        log.debug("scheduledRefresh triggered");
        refreshStats("solana", "1h").subscribe();
        refreshStats("evm:base", "1h").subscribe();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String toIntervalExpr(String window) {
        if (window == null || window.isBlank()) return "1 hour";
        return switch (window.toLowerCase()) {
            case "1h"  -> "1 hour";
            case "6h"  -> "6 hours";
            case "24h" -> "24 hours";
            case "7d"  -> "7 days";
            case "30d" -> "30 days";
            default    -> window;
        };
    }
}
