package com.maul.onchain3d.graph;

import com.maul.onchain3d.copilot.dto.TransferFact;
import com.maul.onchain3d.normalize.Edge;
import com.maul.onchain3d.stream.dto.Delta;
import com.maul.onchain3d.stream.dto.EdgeDTO;
import com.maul.onchain3d.stream.dto.NodeDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

/**
 * Application-level service that orchestrates graph persistence and DTO assembly.
 *
 * <p>Wraps {@link GraphRepository} to add business logic (address upsert on both
 * sides of an edge, DTO mapping) and exposes higher-level reactive operations
 * consumed by SSE and copilot handlers.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GraphService {

    private final GraphRepository repository;

    /**
     * Persists an edge and upserts both endpoint addresses atomically (best-effort R2DBC).
     */
    public Mono<Void> persistEdge(Edge edge) {
        return Mono.when(
                        repository.upsertAddress(edge.fromAddr(), edge.chain()),
                        repository.upsertAddress(edge.toAddr(),   edge.chain()))
                .then(repository.insertTransfer(edge))
                .doOnSuccess(v -> log.debug("Persisted edge txHash={} logIndex={}", edge.txHash(), edge.logIndex()))
                .doOnError(e -> log.error("Failed to persist edge txHash={}", edge.txHash(), e));
    }

    /**
     * Returns a full graph snapshot for SSE initial connection.
     */
    public Mono<Delta.Snapshot> snapshot(String chain, String window) {
        return repository.snapshot(chain, window);
    }

    /**
     * Returns top transfers by value for copilot grounding (blocking — run on boundedElastic).
     */
    public Mono<List<TransferFact>> topTransfers(String chain, String window, int limit) {
        return Mono.fromCallable(() -> repository.topTransfers(chain, window, limit))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Returns neighbour addresses for copilot {@code expandNeighbors} command (blocking — run on boundedElastic).
     */
    public Mono<List<String>> neighborAddresses(String address, String direction, int limit) {
        return Mono.fromCallable(() -> repository.neighborAddresses(address, direction, limit))
                .subscribeOn(Schedulers.boundedElastic());
    }

    // -------------------------------------------------------------------------
    // DTO mappers
    // -------------------------------------------------------------------------

    /** Maps an {@link Address} entity to the SSE wire DTO. */
    public static NodeDTO toNodeDTO(Address a) {
        return new NodeDTO(a.getAddress(), a.getChain(), a.getLabel(), 0.0, 1.0, false);
    }

    /** Maps a {@link Transfer} entity to the SSE wire DTO. */
    public static EdgeDTO toEdgeDTO(Transfer t) {
        String blockTimeStr = t.getBlockTime() != null ? t.getBlockTime().toInstant().toString() : null;
        return new EdgeDTO(
                t.getTxHash() + "-" + t.getLogIndex(),
                t.getFromAddr(),
                t.getToAddr(),
                t.getAsset(),
                t.getValueNorm(),
                blockTimeStr);
    }
}
