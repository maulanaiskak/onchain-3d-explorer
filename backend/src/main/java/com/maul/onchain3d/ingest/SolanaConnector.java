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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Solana chain connector using the free public Solana mainnet RPC.
 *
 * <p>Subscribes to {@code logsSubscribe} via WebSocket to receive confirmed
 * transaction signatures in real time, then fetches each full transaction via
 * {@code getTransaction} over HTTP to extract pre/post balances for normalisation.
 *
 * <p>An optional Helius API key is supported for higher rate limits but is
 * not required — the public {@code api.mainnet-beta.solana.com} endpoint works
 * without any credentials.
 */
@Slf4j
public class SolanaConnector implements ChainConnector {

    private static final String PUBLIC_WS_URL   = "wss://solana.publicnode.com";
    private static final String PUBLIC_REST_URL  = "https://api.mainnet-beta.solana.com";
    private static final String HELIUS_WS_TMPL  = "wss://mainnet.helius-rpc.com/?api-key=%s";
    private static final String HELIUS_REST_TMPL = "https://mainnet.helius-rpc.com/?api-key=%s";

    private static final String LOGS_SUBSCRIBE = """
            {"jsonrpc":"2.0","id":1,"method":"logsSubscribe",\
            "params":["all",{"commitment":"confirmed"}]}""";

    private final String heliusApiKey;
    private final WebSocketClient wsClient;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public SolanaConnector(String heliusApiKey, WebClient.Builder webClientBuilder,
                           ObjectMapper objectMapper) {
        this.heliusApiKey = heliusApiKey == null ? "" : heliusApiKey.strip();
        this.wsClient     = new ReactorNettyWebSocketClient();
        String restUrl    = this.heliusApiKey.isBlank()
                ? PUBLIC_REST_URL
                : HELIUS_REST_TMPL.formatted(this.heliusApiKey);
        this.webClient    = webClientBuilder.baseUrl(restUrl).build();
        this.objectMapper = objectMapper;
    }

    @Override
    public String chain() { return "solana"; }

    @Override
    public Flux<RawTxEvent> connect() {
        String wsUrl = heliusApiKey.isBlank()
                ? PUBLIC_WS_URL
                : HELIUS_WS_TMPL.formatted(heliusApiKey);

        log.info("SolanaConnector: connecting to {}", wsUrl);

        return Flux.defer(() -> openWebSocket(wsUrl)
                        .flatMap(sig -> fetchTransaction(sig), 8)
                        .filter(e -> e != null))
                .retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(3))
                        .maxBackoff(Duration.ofSeconds(30))
                        .doBeforeRetry(rs -> log.warn(
                                "SolanaConnector: reconnecting (attempt #{})", rs.totalRetries() + 1)))
                .repeat();
    }

    // -------------------------------------------------------------------------
    // WebSocket — emit signatures
    // -------------------------------------------------------------------------

    private Flux<String> openWebSocket(String wsUrl) {
        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();

        Mono<Void> session = wsClient.execute(URI.create(wsUrl), ws -> {
            Flux<WebSocketMessage> send = Flux.just(ws.textMessage(LOGS_SUBSCRIBE));
            Flux<Void> receive = ws.receive()
                    .map(WebSocketMessage::getPayloadAsText)
                    .flatMap(msg -> Flux.fromIterable(extractSignature(msg)))
                    .doOnNext(sink::tryEmitNext)
                    .then().flux();
            return ws.send(send).thenMany(receive).then();
        });

        return sink.asFlux().mergeWith(session.cast(String.class));
    }

    private List<String> extractSignature(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            if (!"logsNotification".equals(root.path("method").asText())) return List.of();
            String sig = root.path("params").path("result").path("value")
                    .path("signature").asText(null);
            if (sig == null || sig.isBlank()) return List.of();
            return List.of(sig);
        } catch (Exception e) {
            log.debug("SolanaConnector: extractSignature error — {}", e.getMessage());
            return List.of();
        }
    }

    // -------------------------------------------------------------------------
    // HTTP — fetch full transaction
    // -------------------------------------------------------------------------

    private Mono<RawTxEvent> fetchTransaction(String signature) {
        String body = """
                {"jsonrpc":"2.0","id":1,"method":"getTransaction",\
                "params":["%s",{"encoding":"jsonParsed","maxSupportedTransactionVersion":0}]}
                """.formatted(signature);

        return webClient.post()
                .uri("")
                .header("Content-Type", "application/json")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .flatMap(root -> {
                    JsonNode result = root.path("result");
                    if (result.isNull() || result.isMissingNode()) return Mono.empty();

                    JsonNode meta = result.path("meta");
                    JsonNode tx   = result.path("transaction");

                    List<Number> pre  = jsonArrayToNumbers(meta.path("preBalances"));
                    List<Number> post = jsonArrayToNumbers(meta.path("postBalances"));
                    List<String> keys = jsonArrayToStrings(
                            tx.path("message").path("accountKeys"));

                    if (pre.isEmpty() || post.isEmpty() || keys.isEmpty()) return Mono.empty();

                    Map<String, Object> raw = new HashMap<>();
                    raw.put("preBalances",  pre);
                    raw.put("postBalances", post);
                    raw.put("accountKeys",  keys);

                    return Mono.just(new RawTxEvent(signature, "solana", Instant.now(), raw));
                })
                .onErrorResume(e -> {
                    log.debug("SolanaConnector: fetchTransaction error sig={} — {}", signature, e.getMessage());
                    return Mono.empty();
                });
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static List<Number> jsonArrayToNumbers(JsonNode arr) {
        List<Number> list = new ArrayList<>();
        if (arr.isArray()) arr.forEach(n -> list.add(n.longValue()));
        return list;
    }

    private static List<String> jsonArrayToStrings(JsonNode arr) {
        List<String> list = new ArrayList<>();
        if (arr.isArray()) {
            arr.forEach(node -> {
                // jsonParsed accountKeys are objects with a "pubkey" field
                if (node.isObject()) list.add(node.path("pubkey").asText());
                else list.add(node.asText());
            });
        }
        return list;
    }
}
