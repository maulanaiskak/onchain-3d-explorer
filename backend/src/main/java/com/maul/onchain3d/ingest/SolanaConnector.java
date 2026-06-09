package com.maul.onchain3d.ingest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.util.retry.Retry;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Solana chain connector.
 *
 * <p>When a Helius API key is configured, connects via websocket to
 * {@code wss://mainnet.helius-rpc.com/?api-key=KEY} and subscribes to
 * {@code logsSubscribe}. Falls back to polling the public Solana RPC when no
 * key is present.
 */
@Slf4j
public class SolanaConnector implements ChainConnector {

    private static final String PUBLIC_WS_URI    = "wss://api.mainnet-beta.solana.com";
    private static final String HELIUS_WS_TMPL   = "wss://mainnet.helius-rpc.com/?api-key=%s";
    private static final String PUBLIC_REST_URL   = "https://api.mainnet-beta.solana.com";

    private static final String SUBSCRIBE_MSG = """
            {"jsonrpc":"2.0","id":1,"method":"logsSubscribe","params":["all",{"commitment":"confirmed"}]}
            """;

    private final String heliusApiKey;
    private final WebSocketClient wsClient;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public SolanaConnector(String heliusApiKey, WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.heliusApiKey = heliusApiKey == null ? "" : heliusApiKey.strip();
        this.wsClient     = new ReactorNettyWebSocketClient();
        this.webClient    = webClientBuilder.baseUrl(PUBLIC_REST_URL).build();
        this.objectMapper = objectMapper;
    }

    @Override
    public String chain() {
        return "solana";
    }

    @Override
    public Flux<RawTxEvent> connect() {
        if (heliusApiKey.isBlank()) {
            log.info("SolanaConnector: no Helius key — using polling fallback");
            return pollFallback();
        }
        log.info("SolanaConnector: connecting via Helius websocket");
        return connectWebSocket()
                .retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(2))
                        .maxBackoff(Duration.ofSeconds(30))
                        .doBeforeRetry(rs -> log.warn("SolanaConnector: retrying (attempt {})", rs.totalRetries() + 1)));
    }

    // -------------------------------------------------------------------------
    // WebSocket path
    // -------------------------------------------------------------------------

    private Flux<RawTxEvent> connectWebSocket() {
        URI uri = URI.create(heliusApiKey.isBlank() ? PUBLIC_WS_URI
                : HELIUS_WS_TMPL.formatted(heliusApiKey));

        Sinks.Many<RawTxEvent> sink = Sinks.many().unicast().onBackpressureBuffer();

        Mono<Void> session = wsClient.execute(uri, ws -> {
            Flux<WebSocketMessage> send = Flux.just(ws.textMessage(SUBSCRIBE_MSG));
            Flux<Void> receive = ws.receive()
                    .map(WebSocketMessage::getPayloadAsText)
                    .flatMap(payload -> Flux.fromIterable(parseWsMessage(payload)))
                    .doOnNext(event -> sink.tryEmitNext(event))
                    .then()
                    .flux();
            return ws.send(send).thenMany(receive).then();
        });

        return sink.asFlux().mergeWith(session.cast(RawTxEvent.class));
    }

    private Iterable<RawTxEvent> parseWsMessage(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            // logsSubscribe notifications have "method":"logsNotification"
            JsonNode method = root.path("method");
            if (!"logsNotification".equals(method.asText())) {
                return java.util.List.of();
            }
            JsonNode value  = root.path("params").path("result").path("value");
            String txHash   = value.path("signature").asText(null);
            if (txHash == null || txHash.isBlank()) return java.util.List.of();

            Map<String, Object> raw = new HashMap<>();
            raw.put("logs", objectMapper.convertValue(value.path("logs"), java.util.List.class));
            raw.put("err",  value.path("err").isNull() ? null : value.path("err").asText());
            return java.util.List.of(new RawTxEvent(txHash, "solana", Instant.now(), raw));
        } catch (Exception e) {
            log.debug("SolanaConnector: failed to parse ws message: {}", e.getMessage());
            return java.util.List.of();
        }
    }

    // -------------------------------------------------------------------------
    // Polling fallback
    // -------------------------------------------------------------------------

    private Flux<RawTxEvent> pollFallback() {
        return Flux.interval(Duration.ofSeconds(3))
                .flatMap(tick -> fetchRecentSignatures())
                .retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(2))
                        .maxBackoff(Duration.ofSeconds(30))
                        .doBeforeRetry(rs -> log.warn("SolanaConnector poll: retrying (attempt {})", rs.totalRetries() + 1)));
    }

    private Flux<RawTxEvent> fetchRecentSignatures() {
        String body = """
                {"jsonrpc":"2.0","id":1,"method":"getRecentPerformanceSamples","params":[5]}
                """;
        return webClient.post()
                .uri("")
                .header("Content-Type", "application/json")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .flatMapMany(root -> {
                    JsonNode samples = root.path("result");
                    if (!samples.isArray()) return Flux.empty();
                    Flux<RawTxEvent> events = Flux.empty();
                    for (JsonNode sample : samples) {
                        String slot = sample.path("slot").asText("0");
                        Map<String, Object> raw = new HashMap<>();
                        raw.put("slot",         slot);
                        raw.put("numTransactions", sample.path("numTransactions").asInt(0));
                        String syntheticHash = "slot-" + slot + "-" + Instant.now().toEpochMilli();
                        events = events.concatWith(Flux.just(
                                new RawTxEvent(syntheticHash, "solana", Instant.now(), raw)));
                    }
                    return events;
                })
                .onErrorResume(e -> {
                    log.debug("SolanaConnector poll error: {}", e.getMessage());
                    return Flux.empty();
                });
    }
}
