package com.maul.onchain3d.graph;

import com.maul.onchain3d.stream.DeltaPublisher;
import com.maul.onchain3d.stream.dto.Delta;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

/**
 * Scheduled component that detects stale nodes and edges and emits {@link Delta.Decay}
 * events so the frontend can fade them out of the 3-D scene.
 *
 * <p>{@link DeltaPublisher} is injected lazily to break the Spring circular-dependency
 * chain between this component and the publisher.
 */
@Slf4j
@Component
public class DecayScheduler {

    private final JdbcTemplate jdbc;
    private final DeltaPublisher publisher;
    private final String decayInterval;

    public DecayScheduler(JdbcTemplate jdbc,
                          @Lazy DeltaPublisher publisher,
                          @Value("${app.ingest.decay-interval:1 hour}") String decayInterval) {
        this.jdbc          = jdbc;
        this.publisher     = publisher;
        this.decayInterval = decayInterval;
    }

    /**
     * Runs every 30 s; queries stale addresses and transfers, then emits a
     * {@link Delta.Decay} if any are found.
     */
    @Scheduled(fixedDelay = 30_000)
    public void runDecay() {
        Mono.fromCallable(this::findStale)
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        decay -> {
                            if (!decay.nodeIds().isEmpty() || !decay.edgeIds().isEmpty()) {
                                log.debug("DecayScheduler emitting decay nodes={} edges={}",
                                        decay.nodeIds().size(), decay.edgeIds().size());
                                publisher.emit(decay);
                            }
                        },
                        err -> log.error("DecayScheduler query failed", err));
    }

    private Delta.Decay findStale() {
        List<String> staleNodes = jdbc.queryForList(
                """
                SELECT address FROM address
                WHERE last_seen_at < now() - ?::interval
                """,
                String.class,
                decayInterval);

        List<String> staleEdges = jdbc.queryForList(
                """
                SELECT tx_hash || '-' || log_index
                FROM transfer
                WHERE block_time < now() - ?::interval
                """,
                String.class,
                decayInterval);

        return new Delta.Decay(staleNodes, staleEdges);
    }
}
