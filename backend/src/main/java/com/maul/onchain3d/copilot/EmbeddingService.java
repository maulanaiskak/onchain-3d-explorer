package com.maul.onchain3d.copilot;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.auth.oauth2.GoogleCredentials;
import com.maul.onchain3d.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.util.PGobject;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Generates 768-dimensional text embeddings via Vertex AI {@code text-embedding-004}
 * and upserts them into the {@code address_embedding} table for RAG-based retrieval.
 *
 * <p>Authentication reuses the shared {@link GoogleCredentials} bean from {@code VertexConfig}.
 */
@Slf4j
@Service
public class EmbeddingService {

    private static final String ENDPOINT_TMPL =
            "https://%s-aiplatform.googleapis.com/v1/projects/%s/locations/%s/publishers/google/models/%s:predict";

    private final WebClient webClient;
    private final GoogleCredentials credentials;
    private final JdbcTemplate jdbc;
    private final AppProperties props;

    public EmbeddingService(@Qualifier("vertexWebClient") WebClient webClient,
                            GoogleCredentials credentials,
                            JdbcTemplate jdbc,
                            AppProperties props) {
        this.webClient   = webClient;
        this.credentials = credentials;
        this.jdbc        = jdbc;
        this.props       = props;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Generates a 768-dimensional embedding for the given text.
     *
     * @param text the text to embed
     * @return {@code Mono<float[]>} with 768 dimensions
     */
    public Mono<float[]> embedText(String text) {
        return Mono.fromCallable(this::refreshedToken)
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(token -> callEmbedEndpoint(token, text))
                .doOnError(e -> log.error("EmbeddingService.embedText failed", e));
    }

    /**
     * Builds a summary text for the address, embeds it, and upserts to
     * {@code address_embedding}.
     *
     * @param address wallet address
     * @param chain   chain identifier
     * @param window  time window shorthand
     */
    public Mono<Void> refreshAddressEmbedding(String address, String chain, String window) {
        return Mono.fromCallable(() -> fetchSummaryParams(address, window))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(params -> {
                    String summary = buildSummary(address, chain, window, params);
                    return embedText(summary)
                            .flatMap(embedding -> Mono.fromCallable(() -> {
                                upsertEmbedding(address, summary, embedding);
                                return null;
                            }).subscribeOn(Schedulers.boundedElastic()));
                })
                .doOnError(e -> log.error("refreshAddressEmbedding failed address={}", address, e))
                .then();
    }

    /**
     * Scheduled refresh every 5 minutes for addresses updated in the last 5 minutes.
     */
    @Scheduled(fixedDelay = 300_000)
    public void refreshAll() {
        log.debug("EmbeddingService.refreshAll triggered");
        Mono.fromCallable(() ->
                        jdbc.queryForList(
                                """
                                SELECT DISTINCT address
                                FROM address_stats
                                WHERE updated_at > now() - interval '5 minutes'
                                """,
                                String.class))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapIterable(addresses -> addresses)
                .flatMap(address ->
                        refreshAddressEmbedding(address, "solana", "1h")
                                .onErrorResume(e -> {
                                    log.warn("refreshAll: failed for address={}", address, e);
                                    return Mono.empty();
                                }))
                .subscribe(
                        v -> {},
                        err -> log.error("EmbeddingService.refreshAll error", err));
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private String refreshedToken() throws IOException {
        credentials.refreshIfExpired();
        return credentials.getAccessToken().getTokenValue();
    }

    private Mono<float[]> callEmbedEndpoint(String bearerToken, String text) {
        String location  = props.vertex().location();
        String projectId = props.vertex().projectId();
        String model     = props.vertex().embedModel();

        String url = ENDPOINT_TMPL.formatted(location, projectId, location, model);

        Map<String, Object> body = Map.of(
                "instances",  List.of(Map.of("content", text)),
                "parameters", Map.of("outputDimensionality", 768));

        return webClient.post()
                .uri(url)
                .header("Authorization", "Bearer " + bearerToken)
                .header("Content-Type", "application/json")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(this::parseEmbeddingResponse)
                .doOnError(e -> log.error("EmbeddingService: HTTP call to Vertex failed", e));
    }

    private float[] parseEmbeddingResponse(JsonNode root) {
        JsonNode values = root
                .path("predictions").path(0)
                .path("embeddings").path("values");

        if (!values.isArray() || values.isEmpty()) {
            log.warn("EmbeddingService: empty or missing embeddings in response");
            return new float[768];
        }

        float[] result = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            result[i] = (float) values.get(i).asDouble();
        }
        return result;
    }

    private record StatsParams(double outValue, int txCount) {}

    private StatsParams fetchSummaryParams(String address, String window) {
        try {
            return jdbc.queryForObject(
                    "SELECT out_value, tx_count FROM address_stats WHERE address = ? AND window_label = ?",
                    (rs, i) -> new StatsParams(rs.getDouble("out_value"), rs.getInt("tx_count")),
                    address, window);
        } catch (Exception e) {
            log.debug("fetchSummaryParams: no stats for address={} window={}", address, window);
            return new StatsParams(0.0, 0);
        }
    }

    private static String buildSummary(String address, String chain, String window, StatsParams params) {
        return "Address %s on %s: sent %.4f across %d transactions in last %s."
                .formatted(address, chain, params.outValue(), params.txCount(), window);
    }

    private void upsertEmbedding(String address, String summary, float[] embedding) {
        try {
            PGobject vec = toVectorParam(embedding);
            jdbc.update(
                    """
                    INSERT INTO address_embedding (address, summary, embedding, updated_at)
                    VALUES (?, ?, ?::vector, now())
                    ON CONFLICT (address) DO UPDATE
                        SET summary    = EXCLUDED.summary,
                            embedding  = EXCLUDED.embedding,
                            updated_at = now()
                    """,
                    address, summary, vec);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to build vector PGobject for address=" + address, e);
        }
    }

    private static PGobject toVectorParam(float[] embedding) throws SQLException {
        String vectorStr = Arrays.toString(embedding).replace(" ", "");
        PGobject pgObj = new PGobject();
        pgObj.setType("vector");
        pgObj.setValue(vectorStr);
        return pgObj;
    }
}
