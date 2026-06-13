package com.maul.onchain3d.ingest;

import com.maul.onchain3d.graph.GraphService;
import com.maul.onchain3d.normalize.Edge;
import com.maul.onchain3d.normalize.TransferNormalizer;
import com.maul.onchain3d.stream.DeltaPublisher;
import com.maul.onchain3d.stream.dto.Delta;
import com.maul.onchain3d.stream.dto.EdgeDTO;
import com.maul.onchain3d.stream.dto.NodeDTO;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Wires together all {@link ChainConnector} instances into a single reactive ingest pipeline:
 * <pre>
 *   connector.connect() → normalize → persist → publish delta
 * </pre>
 *
 * <p>A subscription is created per connector at application startup via {@link PostConstruct}.
 * Errors are logged and do not terminate the subscription.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IngestPipeline {

    private final List<ChainConnector> connectors;
    private final TransferNormalizer normalizer;
    private final GraphService graphService;
    private final DeltaPublisher publisher;

    @PostConstruct
    void start() {
        if (connectors.isEmpty()) {
            log.warn("IngestPipeline: no ChainConnector beans found — pipeline is idle");
            return;
        }

        connectors.forEach(connector -> {
            log.info("IngestPipeline: starting connector chain={}", connector.chain());
            var flux = connector.connect();
            if (flux == null) {
                log.warn("IngestPipeline: connector chain={} returned null flux — skipping", connector.chain());
                return;
            }
            flux.flatMap(normalizer::normalize)
                    .flatMap(edge -> graphService.persistEdge(edge).thenReturn(edge))
                    .doOnNext(this::emitDeltas)
                    .onErrorContinue((err, obj) ->
                            log.warn("IngestPipeline: skipping item due to error chain={}: {}",
                                    connector.chain(), err.getMessage()))
                    .subscribe(
                            edge -> log.trace("IngestPipeline: processed edge txHash={}", edge.txHash()),
                            err  -> log.error("IngestPipeline: connector chain={} pipeline terminated",
                                    connector.chain(), err));
        });
    }

    // -------------------------------------------------------------------------
    // Delta construction
    // -------------------------------------------------------------------------

    private void emitDeltas(Edge edge) {
        // Emit nodes for both sides of the transfer
        NodeDTO fromNode = new NodeDTO(edge.fromAddr(), edge.chain(), null, 0.0, 1.0, false);
        NodeDTO toNode   = new NodeDTO(edge.toAddr(),   edge.chain(), null, 0.0, 1.0, false);
        publisher.emit(new Delta.UpsertNodes(List.of(fromNode, toNode)));

        // Emit the edge itself
        EdgeDTO edgeDto = new EdgeDTO(
                edge.txHash() + "-" + edge.logIndex(),
                edge.fromAddr(),
                edge.toAddr(),
                edge.asset(),
                edge.valueNorm(),
                edge.blockTime().toString(),
                edge.chain());
        publisher.emit(new Delta.UpsertEdges(List.of(edgeDto)));
    }
}
