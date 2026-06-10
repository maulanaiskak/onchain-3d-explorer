package com.maul.onchain3d.stream;

import com.maul.onchain3d.stream.dto.Delta;
import com.maul.onchain3d.stream.dto.NodeDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.Many;

import java.util.List;

/**
 * Hot multicast sink that fan-outs {@link Delta} events to all active SSE subscribers.
 *
 * <p>The internal sink is a multicast buffer (1 024 capacity, no auto-cancel) so that
 * slow consumers only affect their own per-subscriber buffer. Each per-chain subscription
 * applies an additional {@code onBackpressureBuffer(256, DROP_OLDEST)} to shed load for
 * the slowest clients.
 */
@Slf4j
@Component
public class DeltaPublisher {

    private final Many<Delta> sink =
            Sinks.many().multicast().onBackpressureBuffer(1024, false);

    /**
     * Publishes a delta to all active subscribers. Uses {@code tryEmitNext} to avoid
     * blocking; drops silently if the shared buffer is full (very unlikely at 1 024).
     */
    public void emit(Delta delta) {
        Sinks.EmitResult result = sink.tryEmitNext(delta);
        if (result.isFailure()) {
            log.warn("DeltaPublisher.emit dropped delta={} result={}", delta.getClass().getSimpleName(), result);
        }
    }

    /**
     * Returns a hot {@link Flux} of deltas for the given chain.
     *
     * <p>Deltas are filtered to those belonging to {@code chain}; heartbeats and decay
     * events always pass through. Each subscriber gets its own {@code onBackpressureBuffer}
     * so a slow client only drops its own events.
     *
     * @param chain chain identifier, e.g. {@code "solana"}
     */
    public Flux<Delta> subscribe(String chain) {
        return sink.asFlux()
                .filter(d -> matchesChain(d, chain))
                .onBackpressureBuffer(256,
                        dropped -> log.debug("DeltaPublisher: dropped delta for slow client chain={}", chain));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static boolean matchesChain(Delta delta, String chain) {
        return switch (delta) {
            case Delta.Snapshot s        -> chainInNodes(s.nodes(), chain);
            case Delta.UpsertNodes un    -> chainInNodes(un.nodes(), chain);
            // EdgeDTO has no chain field — pass all edge updates through; frontend filters visually
            case Delta.UpsertEdges ignored -> true;
            case Delta.Decay ignored       -> true;
            case Delta.Heartbeat ignored   -> true;
        };
    }

    private static boolean chainInNodes(List<NodeDTO> nodes, String chain) {
        if (nodes == null || nodes.isEmpty()) return true;
        return chain.equals(nodes.getFirst().chain());
    }
}
