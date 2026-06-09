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
 * HTTP client for Vertex AI Gemini 1.5 Flash (structured JSON output).
 *
 * <p>Uses Google Application Default Credentials (ADC) for authentication.
 * Locally: {@code gcloud auth application-default login}.
 * On Cloud Run: service-account credentials are injected automatically.
 */
@Slf4j
@Component
public class VertexLlmClient {

    private static final String ENDPOINT_TMPL =
            "https://%s-aiplatform.googleapis.com/v1/projects/%s/locations/%s/publishers/google/models/%s:generateContent";

    private static final CopilotResponse FALLBACK =
            new CopilotResponse("Unable to process request", List.of(), false);

    private static final String RESPONSE_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "narrative": { "type": "string" },
                "commands":  { "type": "array", "items": { "type": "object" } },
                "grounded":  { "type": "boolean" }
              },
              "required": ["narrative", "commands", "grounded"]
            }
            """;

    private final WebClient webClient;
    private final GoogleCredentials credentials;
    private final ObjectMapper objectMapper;
    private final AppProperties props;

    public VertexLlmClient(@Qualifier("vertexWebClient") WebClient webClient,
                           GoogleCredentials credentials,
                           ObjectMapper objectMapper,
                           AppProperties props) {
        this.webClient    = webClient;
        this.credentials  = credentials;
        this.objectMapper = objectMapper;
        this.props        = props;
    }

    /**
     * Sends a prompt to Vertex AI Gemini and returns a structured copilot response.
     *
     * @param systemPrompt instructions / context for the model
     * @param userPrompt   the user's natural-language query
     */
    public Mono<CopilotResponse> complete(String systemPrompt, String userPrompt) {
        return Mono.fromCallable(this::refreshedToken)
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(token -> callVertex(token, systemPrompt, userPrompt))
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(2))
                        .maxBackoff(Duration.ofSeconds(16))
                        .filter(e -> e.getMessage() != null && e.getMessage().contains("429"))
                        .doBeforeRetry(rs -> log.warn("VertexLlmClient: rate-limited — retrying (attempt {})", rs.totalRetries() + 1)))
                .onErrorResume(e -> {
                    log.error("VertexLlmClient: request failed — returning fallback", e);
                    return Mono.just(FALLBACK);
                });
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private String refreshedToken() throws IOException {
        credentials.refreshIfExpired();
        return credentials.getAccessToken().getTokenValue();
    }

    private Mono<CopilotResponse> callVertex(String bearerToken, String systemPrompt, String userPrompt) {
        String location  = props.vertex().location();
        String projectId = props.vertex().projectId();
        String model     = props.vertex().llmModel();

        String url = ENDPOINT_TMPL.formatted(location, projectId, location, model);

        String combinedPrompt = "SYSTEM:\n" + systemPrompt + "\n\nUSER:\n" + userPrompt;

        Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                        Map.of("role", "user",
                               "parts", List.of(Map.of("text", combinedPrompt)))),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json",
                        "responseSchema",   parseJson(RESPONSE_SCHEMA),
                        "temperature",      0.2,
                        "maxOutputTokens",  1024));

        return webClient.post()
                .uri(url)
                .header("Authorization", "Bearer " + bearerToken)
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(this::parseResponse)
                .onErrorResume(e -> {
                    log.error("VertexLlmClient: HTTP call failed", e);
                    return Mono.just(FALLBACK);
                });
    }

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

            JsonNode parsed = objectMapper.readTree(text);
            String narrative = parsed.path("narrative").asText("No narrative.");
            boolean grounded = parsed.path("grounded").asBoolean(false);

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

    private Object parseJson(String json) {
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception e) {
            log.warn("VertexLlmClient: failed to parse schema JSON — sending raw string");
            return json;
        }
    }
}
