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
 * EVM chain connector (defaults to Base mainnet).
 *
 * <p>When an Alchemy API key is configured, connects via websocket to Alchemy and
 * subscribes to {@code newHeads} + ERC-20 Transfer logs. Falls back to polling
 * the public Base RPC at {@code https://mainnet.base.org}.
 */
@Slf4j
public class EvmConnector implements ChainConnector {

    /** ERC-20 Transfer(address indexed from, address indexed to, uint256 value) */
    private static final String ERC20_TRANSFER_TOPIC =
            "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef";

    private static final String PUBLIC_BASE_URL     = "https://mainnet.base.org";
    private static final String ALCHEMY_WS_TMPL     = "wss://base-mainnet.g.alchemy.com/v2/%s";

    private static final String SUBSCRIBE_NEW_HEADS = """
            {"jsonrpc":"2.0","id":1,"method":"eth_subscribe","params":["newHeads"]}
            """;
    private static final String SUBSCRIBE_LOGS_TMPL = """
            {"jsonrpc":"2.0","id":2,"method":"eth_subscribe","params":["logs",{"topics":["%s"]}]}
            """.formatted(ERC20_TRANSFER_TOPIC);

    private final String alchemyApiKey;
    private final String chainId;
    private final WebSocketClient wsClient;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public EvmConnector(String alchemyApiKey, String chainId,
                        WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.alchemyApiKey = alchemyApiKey == null ? "" : alchemyApiKey.strip();
        this.chainId       = chainId == null || chainId.isBlank() ? "evm:base" : chainId;
        this.wsClient      = new ReactorNettyWebSocketClient();
        this.webClient     = webClientBuilder.baseUrl(PUBLIC_BASE_URL).build();
        this.objectMapper  = objectMapper;
    }

    @Override
    public String chain() {
        return chainId;
    }

    @Override
    public Flux<RawTxEvent> connect() {
        if (alchemyApiKey.isBlank()) {
            log.info("EvmConnector: no Alchemy key — using polling fallback for chain={}", chainId);
            return pollFallback();
        }
        log.info("EvmConnector: connecting via Alchemy websocket for chain={}", chainId);
        return connectWebSocket()
                .retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(2))
                        .maxBackoff(Duration.ofSeconds(30))
                        .doBeforeRetry(rs -> log.warn("EvmConnector: retrying (attempt {})", rs.totalRetries() + 1)));
    }

    // -------------------------------------------------------------------------
    // WebSocket path
    // -------------------------------------------------------------------------

    private Flux<RawTxEvent> connectWebSocket() {
        URI uri = URI.create(ALCHEMY_WS_TMPL.formatted(alchemyApiKey));
        Sinks.Many<RawTxEvent> sink = Sinks.many().unicast().onBackpressureBuffer();

        Mono<Void> wsSession = wsClient.execute(uri, ws -> {
            Flux<WebSocketMessage> send = Flux.just(
                    ws.textMessage(SUBSCRIBE_NEW_HEADS),
                    ws.textMessage(SUBSCRIBE_LOGS_TMPL));
            Flux<Void> receive = ws.receive()
                    .map(WebSocketMessage::getPayloadAsText)
                    .flatMap(payload -> Flux.fromIterable(parseWsMessage(payload)))
                    .doOnNext(sink::tryEmitNext)
                    .then()
                    .flux();
            return ws.send(send).thenMany(receive).then();
        });

        return sink.asFlux().mergeWith(wsSession.cast(RawTxEvent.class));
    }

    private Iterable<RawTxEvent> parseWsMessage(String payload) {
        try {
            JsonNode root   = objectMapper.readTree(payload);
            JsonNode method = root.path("method");
            String methodStr = method.asText("");

            return switch (methodStr) {
                case "eth_subscription" -> parseSubscriptionEvent(root);
                default -> java.util.List.of();
            };
        } catch (Exception e) {
            log.debug("EvmConnector: failed to parse ws message: {}", e.getMessage());
            return java.util.List.of();
        }
    }

    private Iterable<RawTxEvent> parseSubscriptionEvent(JsonNode root) {
        JsonNode result = root.path("params").path("result");

        // newHeads event — has "hash" field
        if (result.has("hash")) {
            String blockHash   = result.path("hash").asText();
            String blockNumber = result.path("number").asText("0x0");
            Map<String, Object> raw = new HashMap<>();
            raw.put("blockHash",   blockHash);
            raw.put("blockNumber", blockNumber);
            raw.put("eventType",   "newHead");
            return java.util.List.of(new RawTxEvent(blockHash, chainId, Instant.now(), raw));
        }

        // logs event — has "transactionHash" and "topics"
        if (result.has("transactionHash")) {
            String txHash = result.path("transactionHash").asText();
            Map<String, Object> raw = new HashMap<>();
            raw.put("txHash",      txHash);
            raw.put("address",     result.path("address").asText());
            raw.put("data",        result.path("data").asText());
            raw.put("topics",      objectMapper.convertValue(result.path("topics"), java.util.List.class));
            raw.put("logIndex",    result.path("logIndex").asText("0x0"));
            raw.put("eventType",   "erc20Transfer");
            int logIndex = hexToInt(result.path("logIndex").asText("0x0"));
            return java.util.List.of(new RawTxEvent(txHash + ":" + logIndex, chainId, Instant.now(), raw));
        }

        return java.util.List.of();
    }

    // -------------------------------------------------------------------------
    // Polling fallback
    // -------------------------------------------------------------------------

    private Flux<RawTxEvent> pollFallback() {
        return Flux.interval(Duration.ofSeconds(2))
                .flatMap(tick -> fetchLatestBlock())
                .retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(2))
                        .maxBackoff(Duration.ofSeconds(30))
                        .doBeforeRetry(rs -> log.warn("EvmConnector poll: retrying (attempt {})", rs.totalRetries() + 1)));
    }

    private Flux<RawTxEvent> fetchLatestBlock() {
        String body = """
                {"jsonrpc":"2.0","id":1,"method":"eth_getBlockByNumber","params":["latest",true]}
                """;
        return webClient.post()
                .uri("")
                .header("Content-Type", "application/json")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .flatMapMany(root -> {
                    JsonNode block = root.path("result");
                    if (block.isNull() || block.isMissingNode()) return Flux.empty();
                    JsonNode txs = block.path("transactions");
                    if (!txs.isArray()) return Flux.empty();

                    Flux<RawTxEvent> events = Flux.empty();
                    for (JsonNode tx : txs) {
                        String value = tx.path("value").asText("0x0");
                        // Only emit native ETH transfers with value > 0
                        if ("0x0".equals(value) || "0x".equals(value)) continue;

                        String txHash = tx.path("hash").asText(null);
                        if (txHash == null) continue;

                        Map<String, Object> raw = new HashMap<>();
                        raw.put("from",        tx.path("from").asText());
                        raw.put("to",          tx.path("to").asText());
                        raw.put("value",       value);
                        raw.put("input",       tx.path("input").asText("0x"));
                        raw.put("eventType",   "nativeTransfer");
                        raw.put("blockNumber", block.path("number").asText());
                        events = events.concatWith(
                                Flux.just(new RawTxEvent(txHash, chainId, Instant.now(), raw)));
                    }
                    return events;
                })
                .onErrorResume(e -> {
                    log.debug("EvmConnector poll error: {}", e.getMessage());
                    return Flux.empty();
                });
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static int hexToInt(String hex) {
        try {
            String clean = hex.startsWith("0x") || hex.startsWith("0X")
                    ? hex.substring(2) : hex;
            return clean.isBlank() ? 0 : Integer.parseInt(clean, 16);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
