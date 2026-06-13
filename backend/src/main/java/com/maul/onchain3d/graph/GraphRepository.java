package com.maul.onchain3d.graph;

import com.maul.onchain3d.copilot.dto.TransferFact;
import com.maul.onchain3d.normalize.Edge;
import com.maul.onchain3d.stream.dto.Delta;
import com.maul.onchain3d.stream.dto.EdgeDTO;
import com.maul.onchain3d.stream.dto.NodeDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Primary persistence layer for graph nodes (addresses) and edges (transfers).
 *
 * <p>Uses {@link DatabaseClient} (R2DBC) for hot reactive paths (upsert, insert)
 * and {@link JdbcTemplate} wrapped in {@code Schedulers.boundedElastic()} for
 * complex read queries that are not on the critical event-loop path.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class GraphRepository {

    private final DatabaseClient db;
    private final JdbcTemplate jdbc;

    // -------------------------------------------------------------------------
    // Write paths — R2DBC reactive
    // -------------------------------------------------------------------------

    /**
     * Upserts an address row; creates it on first sight and bumps {@code last_seen_at} on replay.
     */
    public Mono<Void> upsertAddress(String address, String chain) {
        return db.sql("""
                        INSERT INTO address (address, chain, last_seen_at)
                        VALUES (:address, :chain, now())
                        ON CONFLICT (address) DO UPDATE SET last_seen_at = now()
                        """)
                .bind("address", address)
                .bind("chain", chain)
                .then()
                .doOnError(e -> log.error("upsertAddress failed addr={} chain={}", address, chain, e));
    }

    /**
     * Inserts a transfer row; silently ignores duplicates on {@code (tx_hash, log_index)}.
     */
    public Mono<Void> insertTransfer(Edge edge) {
        return db.sql("""
                        INSERT INTO transfer
                            (tx_hash, log_index, chain, from_addr, to_addr, asset,
                             value_raw, value_norm, block_time)
                        VALUES
                            (:txHash, :logIndex, :chain, :fromAddr, :toAddr, :asset,
                             :valueRaw, :valueNorm, :blockTime)
                        ON CONFLICT (tx_hash, log_index) DO NOTHING
                        """)
                .bind("txHash",    edge.txHash())
                .bind("logIndex",  edge.logIndex())
                .bind("chain",     edge.chain())
                .bind("fromAddr",  edge.fromAddr())
                .bind("toAddr",    edge.toAddr())
                .bind("asset",     edge.asset())
                .bind("valueRaw",  edge.valueRaw())
                .bind("valueNorm", edge.valueNorm())
                .bind("blockTime", edge.blockTime().atOffset(ZoneOffset.UTC))
                .then()
                .doOnError(e -> log.error("insertTransfer failed txHash={}", edge.txHash(), e));
    }

    // -------------------------------------------------------------------------
    // Read paths — JdbcTemplate on boundedElastic
    // -------------------------------------------------------------------------

    /**
     * Builds a full snapshot of the graph for the given chain and time window.
     * Intended for SSE initial-connect delivery.
     *
     * @param chain  chain identifier, e.g. {@code "solana"} or {@code "evm:base"}
     * @param window PostgreSQL interval expression, e.g. {@code "1 hour"}
     */
    public Mono<Delta.Snapshot> snapshot(String chain, String window) {
        return Mono.fromCallable(() -> buildSnapshot(chain, window))
                .subscribeOn(Schedulers.boundedElastic())
                .doOnError(e -> log.error("snapshot failed chain={} window={}", chain, window, e));
    }

    private Delta.Snapshot buildSnapshot(String chain, String window) {
        String intervalExpr = toIntervalExpr(window);

        // --- nodes with computed metrics ---------------------------------
        // Subquery JOIN avoids a dynamic IN-clause with thousands of parameters.
        // weight  = address's total out_value normalised to [0,1] against the window max.
        // recency = how recently the address was seen, decayed linearly from 1→0 over the window.
        List<NodeDTO> nodes = jdbc.query(
                """
                WITH window_transfers AS (
                    SELECT from_addr, to_addr, value_norm, block_time
                    FROM transfer
                    WHERE chain = ?
                      AND block_time > now() - ?::interval
                ),
                addr_stats AS (
                    SELECT addr,
                           SUM(value_norm)                       AS out_value,
                           MAX(block_time)                       AS last_seen
                    FROM (
                        SELECT from_addr AS addr, value_norm, block_time FROM window_transfers
                        UNION ALL
                        SELECT to_addr   AS addr, value_norm, block_time FROM window_transfers
                    ) t
                    GROUP BY addr
                ),
                max_val AS (SELECT COALESCE(MAX(out_value), 1) AS mv FROM addr_stats),
                window_secs AS (SELECT EXTRACT(EPOCH FROM ?::interval) AS ws)
                SELECT a.address,
                       a.chain,
                       a.label,
                       COALESCE(s.out_value / NULLIF(mx.mv, 0), 0)           AS weight,
                       GREATEST(0, 1 - EXTRACT(EPOCH FROM (now() - s.last_seen)) / ws.ws) AS recency,
                       COALESCE(s.out_value / NULLIF(mx.mv, 0), 0) > 0.5     AS is_whale
                FROM addr_stats s
                JOIN address a ON a.address = s.addr
                CROSS JOIN max_val mx
                CROSS JOIN window_secs ws
                ORDER BY weight DESC
                LIMIT 500
                """,
                (rs, i) -> new NodeDTO(
                        rs.getString("address"),
                        rs.getString("chain"),
                        rs.getString("label"),
                        rs.getDouble("weight"),
                        rs.getDouble("recency"),
                        rs.getBoolean("is_whale")),
                chain, intervalExpr, intervalExpr);

        // Collect the node IDs that made the cut, then fetch only their edges
        java.util.Set<String> nodeIds = new java.util.LinkedHashSet<>();
        nodes.forEach(n -> nodeIds.add(n.id()));

        // --- edges -------------------------------------------------------
        List<EdgeDTO> edges;
        if (nodeIds.isEmpty()) {
            edges = List.of();
        } else {
            edges = jdbc.query(
                    """
                    SELECT tx_hash, log_index, from_addr, to_addr, asset, value_norm, block_time
                    FROM transfer
                    WHERE chain = ?
                      AND block_time > now() - ?::interval
                    ORDER BY block_time DESC
                    LIMIT 2000
                    """,
                    (rs, i) -> new EdgeDTO(
                            rs.getString("tx_hash") + "-" + rs.getInt("log_index"),
                            rs.getString("from_addr"),
                            rs.getString("to_addr"),
                            rs.getString("asset"),
                            rs.getDouble("value_norm"),
                            rs.getTimestamp("block_time").toInstant().toString(),
                            chain),
                    chain, intervalExpr);
        }

        String serverTime = Instant.now().toString();
        return new Delta.Snapshot(nodes, edges, window, serverTime);
    }

    /**
     * Returns the top transfers by value within the window (for copilot hard facts).
     * Runs synchronously on the caller's thread — must be invoked on a bounded-elastic
     * scheduler or called from a blocking context.
     */
    public List<TransferFact> topTransfers(String chain, String window, int limit) {
        String intervalExpr = toIntervalExpr(window);
        return jdbc.query(
                """
                SELECT from_addr, to_addr, asset, value_norm, block_time
                FROM transfer
                WHERE chain = ?
                  AND block_time > now() - ?::interval
                ORDER BY value_norm DESC
                LIMIT ?
                """,
                (rs, i) -> new TransferFact(
                        rs.getString("from_addr"),
                        rs.getString("to_addr"),
                        rs.getString("asset"),
                        rs.getDouble("value_norm"),
                        rs.getTimestamp("block_time").toInstant().toString()),
                chain, intervalExpr, limit);
    }

    /**
     * Returns addresses connected to {@code address} in the specified {@code direction}.
     * Runs synchronously — wrap in {@code Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())}
     * when called from a reactive pipeline.
     *
     * @param direction {@code "in"}, {@code "out"}, or {@code "both"}
     */
    public List<String> neighborAddresses(String address, String direction, int limit) {
        return switch (direction) {
            case "in" -> jdbc.queryForList(
                    "SELECT DISTINCT from_addr FROM transfer WHERE to_addr = ? LIMIT ?",
                    String.class, address, limit);
            case "out" -> jdbc.queryForList(
                    "SELECT DISTINCT to_addr FROM transfer WHERE from_addr = ? LIMIT ?",
                    String.class, address, limit);
            default -> jdbc.queryForList(
                    """
                    SELECT DISTINCT addr FROM (
                        SELECT from_addr AS addr FROM transfer WHERE to_addr   = ?
                        UNION
                        SELECT to_addr   AS addr FROM transfer WHERE from_addr = ?
                    ) t
                    LIMIT ?
                    """,
                    String.class, address, address, limit);
        };
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Converts shorthand window strings like {@code "1h"}, {@code "24h"}, {@code "7d"}
     * into PostgreSQL interval expressions.
     */
    private static String toIntervalExpr(String window) {
        if (window == null || window.isBlank()) return "1 hour";
        return switch (window.toLowerCase()) {
            case "1h"  -> "1 hour";
            case "6h"  -> "6 hours";
            case "24h" -> "24 hours";
            case "7d"  -> "7 days";
            case "30d" -> "30 days";
            default    -> window; // pass-through for raw interval strings
        };
    }
}
