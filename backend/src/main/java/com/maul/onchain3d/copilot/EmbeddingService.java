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
import reactor.util.retry.Retry;

import java.io.IOException;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Generates 3072-dimensional text embeddings via Google's gemini-embedding-001 model.
 *
 * <p>Uses Google AI Studio (generativelanguage.googleapis.com) when a
 * {@code GEMINI_API_KEY} is configured — no billing required.
 * Falls back to Vertex AI (aiplatform.googleapis.com) with ADC otherwise.
 */
@Slf4j
@Service
public class EmbeddingService {

    // AI Studio endpoint (free, API-key auth)
    private static final String AI_STUDIO_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:embedContent?key=%s";

    // Vertex AI endpoint (billing required, ADC auth)
    private static final String VERTEX_URL =
            "https://%s-aiplatform.googleapis.com/v1/projects/%s/locations/%s/publishers/google/models/%s:predict";

    private final WebClient webClient;
    private final GoogleCredentials credentials;
    private final JdbcTemplate jdbc;
    private final AppProperties props;
    private final boolean useAiStudio;

    public EmbeddingService(@Qualifier("vertexWebClient") WebClient webClient,
                            GoogleCredentials credentials,
                            JdbcTemplate jdbc,
                            AppProperties props) {
        this.webClient    = webClient;
        this.credentials  = credentials;
        this.jdbc         = jdbc;
        this.props        = props;
        this.useAiStudio  = !props.vertex().geminiApiKey().isBlank();
        log.info("EmbeddingService: using {} backend", useAiStudio ? "AI Studio (free)" : "Vertex AI");
    }

    public Mono<float[]> embedText(String text) {
        Mono<float[]> call = useAiStudio
                ? callAiStudio(text)
                : Mono.fromCallable(this::refreshedToken)
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMap(token -> callVertex(token, text));

        return call
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(4))
                        .maxBackoff(Duration.ofSeconds(30))
                        .filter(e -> e.getMessage() != null && e.getMessage().contains("429"))
                        .doBeforeRetry(rs -> log.warn("EmbeddingService: rate-limited — retry #{}", rs.totalRetries() + 1)))
                .doOnError(e -> log.error("EmbeddingService.embedText failed", e));
    }

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

    // Runs every 30 minutes; caps at 15 addresses and throttles to 1/sec to stay within free-tier limits.
    @Scheduled(fixedDelay = 1_800_000)
    public void refreshAll() {
        log.debug("EmbeddingService.refreshAll triggered");
        Mono.fromCallable(() ->
                        jdbc.queryForList(
                                """
                                SELECT address FROM address_stats
                                WHERE updated_at > now() - interval '30 minutes'
                                ORDER BY tx_count DESC LIMIT 15
                                """,
                                String.class))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapIterable(a -> a)
                .delayElements(Duration.ofSeconds(1))
                .flatMap(address ->
                        refreshAddressEmbedding(address, "evm:base", "1h")
                                .onErrorResume(e -> {
                                    log.warn("refreshAll: failed for address={}", address, e);
                                    return Mono.empty();
                                }), 1)
                .subscribe(v -> {}, err -> log.error("EmbeddingService.refreshAll error", err));
    }

    // -------------------------------------------------------------------------
    // AI Studio path
    // -------------------------------------------------------------------------

    private Mono<float[]> callAiStudio(String text) {
        String url = AI_STUDIO_URL.formatted(props.vertex().embedModel(), props.vertex().geminiApiKey());

        Map<String, Object> body = Map.of(
                "model",   "models/" + props.vertex().embedModel(),
                "content", Map.of("parts", List.of(Map.of("text", text))));

        return webClient.post()
                .uri(url)
                .header("Content-Type", "application/json")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(this::parseAiStudioResponse)
                .doOnError(e -> log.error("EmbeddingService: AI Studio call failed", e));
    }

    private float[] parseAiStudioResponse(JsonNode root) {
        JsonNode values = root.path("embedding").path("values");
        if (!values.isArray() || values.isEmpty()) {
            log.warn("EmbeddingService: empty embeddings from AI Studio");
            return new float[3072];
        }
        float[] result = new float[values.size()];
        for (int i = 0; i < values.size(); i++) result[i] = (float) values.get(i).asDouble();
        return result;
    }

    // -------------------------------------------------------------------------
    // Vertex AI path (fallback)
    // -------------------------------------------------------------------------

    private Mono<float[]> callVertex(String bearerToken, String text) {
        String loc = props.vertex().location();
        String url = VERTEX_URL.formatted(loc, props.vertex().projectId(), loc, props.vertex().embedModel());
        Map<String, Object> body = Map.of(
                "instances",  List.of(Map.of("content", text)),
                "parameters", Map.of("outputDimensionality", 3072));
        return webClient.post()
                .uri(url)
                .header("Authorization", "Bearer " + bearerToken)
                .header("Content-Type", "application/json")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(this::parseVertexResponse)
                .doOnError(e -> log.error("EmbeddingService: Vertex call failed", e));
    }

    private float[] parseVertexResponse(JsonNode root) {
        JsonNode values = root.path("predictions").path(0).path("embeddings").path("values");
        if (!values.isArray() || values.isEmpty()) return new float[3072];
        float[] result = new float[values.size()];
        for (int i = 0; i < values.size(); i++) result[i] = (float) values.get(i).asDouble();
        return result;
    }

    private String refreshedToken() throws IOException {
        credentials.refreshIfExpired();
        return credentials.getAccessToken().getTokenValue();
    }

    // -------------------------------------------------------------------------
    // DB helpers
    // -------------------------------------------------------------------------

    private record StatsParams(double outValue, int txCount) {}

    private StatsParams fetchSummaryParams(String address, String window) {
        try {
            return jdbc.queryForObject(
                    "SELECT out_value, tx_count FROM address_stats WHERE address = ? AND window_label = ?",
                    (rs, i) -> new StatsParams(rs.getDouble("out_value"), rs.getInt("tx_count")),
                    address, window);
        } catch (Exception e) {
            return new StatsParams(0.0, 0);
        }
    }

    private static String buildSummary(String address, String chain, String window, StatsParams p) {
        return "Address %s on %s: sent %.4f across %d transactions in last %s."
                .formatted(address, chain, p.outValue(), p.txCount(), window);
    }

    private void upsertEmbedding(String address, String summary, float[] embedding) {
        try {
            String vectorStr = Arrays.toString(embedding).replace(" ", "");
            PGobject vec = new PGobject();
            vec.setType("vector"); vec.setValue(vectorStr);
            jdbc.update("""
                    INSERT INTO address_embedding (address, summary, embedding, updated_at)
                    VALUES (?, ?, ?::vector, now())
                    ON CONFLICT (address) DO UPDATE
                        SET summary = EXCLUDED.summary, embedding = EXCLUDED.embedding, updated_at = now()
                    """, address, summary, vec);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to upsert embedding for address=" + address, e);
        }
    }
}
