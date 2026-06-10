package com.maul.onchain3d.copilot;

import com.maul.onchain3d.copilot.dto.AddressSummary;
import com.maul.onchain3d.copilot.dto.TransferFact;
import com.maul.onchain3d.graph.GraphRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.util.PGobject;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Combines pgvector semantic search with SQL hard-fact queries to build a grounded
 * {@link RetrievalContext} for each copilot request.
 *
 * <p>All JDBC calls are wrapped in {@code Mono.fromCallable} and scheduled on
 * {@code Schedulers.boundedElastic()} to avoid blocking the WebFlux event loop.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RetrievalService {

    private final JdbcTemplate jdbc;
    private final GraphRepository graphRepository;

    /**
     * Retrieves a grounded context for the given query embedding, chain, and time window.
     *
     * <p>Performs two concurrent queries:
     * <ol>
     *   <li>Semantic cosine search against {@code address_embedding} via pgvector.</li>
     *   <li>Hard-fact SQL query for top transfers by value from {@code GraphRepository}.</li>
     * </ol>
     *
     * @param queryEmbedding 768-dimensional query vector
     * @param chain          chain identifier, e.g. {@code "solana"}
     * @param window         time window shorthand, e.g. {@code "1h"}
     * @return {@code Mono<RetrievalContext>} that never errors — returns an empty context on failure
     */
    public Mono<RetrievalContext> getGroundedContext(float[] queryEmbedding, String chain, String window) {
        return Mono.fromCallable(() -> buildContext(queryEmbedding, chain, window))
                .subscribeOn(Schedulers.boundedElastic())
                .doOnError(e -> log.error("RetrievalService.getGroundedContext failed chain={} window={}", chain, window, e))
                .onErrorReturn(emptyContext());
    }

    // -------------------------------------------------------------------------
    // Blocking retrieval (runs on boundedElastic)
    // -------------------------------------------------------------------------

    private RetrievalContext buildContext(float[] queryEmbedding, String chain, String window) {
        List<AddressSummary> neighbors = semanticSearch(queryEmbedding, chain, window);
        List<TransferFact> topTransfers = graphRepository.topTransfers(chain, window, 20);

        Set<String> allowedNodeIds = new HashSet<>();
        neighbors.forEach(n -> allowedNodeIds.add(n.address()));
        topTransfers.forEach(t -> {
            allowedNodeIds.add(t.fromAddr());
            allowedNodeIds.add(t.toAddr());
        });

        return new RetrievalContext(neighbors, topTransfers, allowedNodeIds);
    }

    private List<AddressSummary> semanticSearch(float[] queryEmbedding, String chain, String window) {
        try {
            PGobject vec = toVectorParam(queryEmbedding);
            String intervalExpr = toIntervalExpr(window);

            return jdbc.query(
                    """
                    SELECT ae.address,
                           ae.summary,
                           1 - (ae.embedding <=> ?::vector) AS similarity
                    FROM address_embedding ae
                    WHERE ae.address IN (
                        SELECT DISTINCT from_addr FROM transfer
                        WHERE chain = ? AND block_time > now() - ?::interval
                        UNION
                        SELECT DISTINCT to_addr FROM transfer
                        WHERE chain = ? AND block_time > now() - ?::interval
                    )
                    ORDER BY ae.embedding <=> ?::vector
                    LIMIT 10
                    """,
                    (rs, i) -> new AddressSummary(
                            rs.getString("address"),
                            rs.getString("summary"),
                            rs.getDouble("similarity")),
                    vec, chain, intervalExpr, chain, intervalExpr, vec);

        } catch (Exception e) {
            // Gracefully handle missing address_embedding table or empty data
            log.warn("RetrievalService.semanticSearch failed (likely empty address_embedding) chain={}: {}",
                    chain, e.getMessage());
            return List.of();
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static PGobject toVectorParam(float[] embedding) throws SQLException {
        String vectorStr = Arrays.toString(embedding)
                .replace(" ", "");
        PGobject pgObj = new PGobject();
        pgObj.setType("vector");
        pgObj.setValue(vectorStr);
        return pgObj;
    }

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

    private static RetrievalContext emptyContext() {
        return new RetrievalContext(List.of(), List.of(), Set.of());
    }
}
