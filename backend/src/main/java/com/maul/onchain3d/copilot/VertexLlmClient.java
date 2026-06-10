package com.maul.onchain3d.copilot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maul.onchain3d.config.AppProperties;
import com.maul.onchain3d.copilot.dto.CopilotResponse;
import com.maul.onchain3d.copilot.dto.SceneCommand;
import com.google.auth.oauth2.GoogleCredentials;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * LLM client for Gemini 1.5 Flash structured JSON output.
 *
 * <p>Uses Google AI Studio (generativelanguage.googleapis.com) when a
 * {@code GEMINI_API_KEY} is set — free tier, no billing required, 15 RPM.
 * Falls back to Vertex AI with ADC when no key is present.
 */
@Slf4j
@Component
public class VertexLlmClient {

    // AI Studio
    private static final String AI_STUDIO_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";

    // Vertex AI
    private static final String VERTEX_URL =
            "https://%s-aiplatform.googleapis.com/v1/projects/%s/locations/%s/publishers/google/models/%s:generateContent";

    private static final CopilotResponse FALLBACK =
            new CopilotResponse("Unable to process request at this time.", List.of(), false);

    private final WebClient webClient;
    private final GoogleCredentials credentials;
    private final ObjectMapper objectMapper;
    private final AppProperties props;
    private final boolean useAiStudio;

    public VertexLlmClient(@Qualifier("vertexWebClient") WebClient webClient,
                           GoogleCredentials credentials,
                           ObjectMapper objectMapper,
                           AppProperties props) {
        this.webClient    = webClient;
        this.credentials  = credentials;
        this.objectMapper = objectMapper;
        this.props        = props;
        this.useAiStudio  = !props.vertex().geminiApiKey().isBlank();
        log.info("VertexLlmClient: using {} backend", useAiStudio ? "AI Studio (free)" : "Vertex AI");
    }

    public Mono<CopilotResponse> complete(String systemPrompt, String userPrompt) {
        Mono<CopilotResponse> call = useAiStudio
                ? callAiStudio(systemPrompt, userPrompt)
                : Mono.fromCallable(this::refreshedToken)
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMap(token -> callVertex(token, systemPrompt, userPrompt));

        return call
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(2))
                        .maxBackoff(Duration.ofSeconds(16))
                        .filter(e -> e.getMessage() != null && e.getMessage().contains("429"))
                        .doBeforeRetry(rs -> log.warn("VertexLlmClient: rate-limited — retry #{}", rs.totalRetries() + 1)))
                .onErrorResume(e -> {
                    log.error("VertexLlmClient: request failed — returning fallback", e);
                    return Mono.just(FALLBACK);
                });
    }

    // -------------------------------------------------------------------------
    // AI Studio path
    // -------------------------------------------------------------------------

    private Mono<CopilotResponse> callAiStudio(String systemPrompt, String userPrompt) {
        String url = AI_STUDIO_URL.formatted(props.vertex().llmModel(), props.vertex().geminiApiKey());

        Map<String, Object> body = Map.of(
                "system_instruction", Map.of("parts", List.of(Map.of("text", systemPrompt))),
                "contents", List.of(
                        Map.of("role", "user",
                               "parts", List.of(Map.of("text", userPrompt)))),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json",
                        "temperature",      0.2,
                        "maxOutputTokens",  1024));

        return webClient.post()
                .uri(url)
                .header("Content-Type", "application/json")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(this::parseResponse)
                .doOnError(e -> log.error("VertexLlmClient: AI Studio call failed", e));
    }

    // -------------------------------------------------------------------------
    // Vertex AI path (fallback)
    // -------------------------------------------------------------------------

    private Mono<CopilotResponse> callVertex(String bearerToken, String systemPrompt, String userPrompt) {
        String loc = props.vertex().location();
        String url = VERTEX_URL.formatted(loc, props.vertex().projectId(), loc, props.vertex().llmModel());

        String combined = "SYSTEM:\n" + systemPrompt + "\n\nUSER:\n" + userPrompt;
        Map<String, Object> body = Map.of(
                "contents", List.of(
                        Map.of("role", "user",
                               "parts", List.of(Map.of("text", combined)))),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json",
                        "temperature",      0.2,
                        "maxOutputTokens",  1024));

        return webClient.post()
                .uri(url)
                .header("Authorization", "Bearer " + bearerToken)
                .header("Content-Type", "application/json")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(this::parseResponse)
                .doOnError(e -> log.error("VertexLlmClient: Vertex call failed", e));
    }

    // -------------------------------------------------------------------------
    // Shared response parsing
    // -------------------------------------------------------------------------

    private CopilotResponse parseResponse(JsonNode root) {
        try {
            String text = root
                    .path("candidates").path(0)
                    .path("content").path("parts").path(0)
                    .path("text").asText(null);

            if (text == null || text.isBlank()) {
                log.warn("VertexLlmClient: empty text in response");
                return FALLBACK;
            }

            JsonNode parsed   = objectMapper.readTree(text);
            String narrative  = parsed.path("narrative").asText("No narrative.");
            boolean grounded  = parsed.path("grounded").asBoolean(false);

            List<SceneCommand> commands = List.of();
            JsonNode cmdsNode = parsed.path("commands");
            if (cmdsNode.isArray()) {
                commands = objectMapper.readerForListOf(SceneCommand.class).readValue(cmdsNode);
            }

            return new CopilotResponse(narrative, commands, grounded);
        } catch (Exception e) {
            log.error("VertexLlmClient: failed to parse LLM response", e);
            return FALLBACK;
        }
    }

    private String refreshedToken() throws IOException {
        credentials.refreshIfExpired();
        return credentials.getAccessToken().getTokenValue();
    }
}
