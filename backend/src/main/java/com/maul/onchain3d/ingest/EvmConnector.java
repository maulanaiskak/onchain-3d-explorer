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
import java.util.List;
import java.util.Map;

/**
 * EVM chain connector using the free public Base mainnet WebSocket.
 *
 * <p>Connects to {@code wss://mainnet.base.org} and subscribes to:
 * <ul>
 *   <li>{@code eth_subscribe newHeads} — new block headers</li>
 *   <li>{@code eth_subscribe logs} filtered to ERC-20 Transfer events</li>
 * </ul>
 *
 * <p>An optional Alchemy API key can be provided for higher rate limits,
 * but the connector works without one using Base's own public endpoint.
 */
@Slf4j
public class EvmConnector implements ChainConnector {

    /** ERC-20 Transfer(address indexed from, address indexed to, uint256 value) */
    private static final String TRANSFER_TOPIC =
            "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef";

    private static final String PUBLIC_WS_URL   = "wss://base.publicnode.com";
    private static final String ALCHEMY_WS_TMPL = "wss://base-mainnet.g.alchemy.com/v2/%s";

    private static final String SUB_HEADS = """
            {"jsonrpc":"2.0","id":1,"method":"eth_subscribe","params":["newHeads"]}""";
    private static final String SUB_LOGS  = """
            {"jsonrpc":"2.0","id":2,"method":"eth_subscribe",\
            "params":["logs",{"topics":["%s"]}]}""".formatted(TRANSFER_TOPIC);

    private final String alchemyApiKey;
    private final String chainId;
    private final WebSocketClient wsClient;
    private final ObjectMapper objectMapper;

    public EvmConnector(String alchemyApiKey, String chainId,
                        WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.alchemyApiKey = alchemyApiKey == null ? "" : alchemyApiKey.strip();
        this.chainId       = chainId == null || chainId.isBlank() ? "evm:base" : chainId;
        this.wsClient      = new ReactorNettyWebSocketClient();
        this.objectMapper  = objectMapper;
    }

    @Override
    public String chain() { return chainId; }

    @Override
    public Flux<RawTxEvent> connect() {
        String wsUrl = alchemyApiKey.isBlank()
                ? PUBLIC_WS_URL
                : ALCHEMY_WS_TMPL.formatted(alchemyApiKey);

        log.info("EvmConnector: connecting to {} for chain={}", wsUrl, chainId);

        return Flux.defer(() -> openWebSocket(wsUrl))
                .retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(3))
                        .maxBackoff(Duration.ofSeconds(30))
                        .doBeforeRetry(rs -> log.warn(
                                "EvmConnector: reconnecting (attempt #{})", rs.totalRetries() + 1)))
                .repeat();
    }

    // -------------------------------------------------------------------------
    // WebSocket
    // -------------------------------------------------------------------------

    private Flux<RawTxEvent> openWebSocket(String wsUrl) {
        Sinks.Many<RawTxEvent> sink = Sinks.many().unicast().onBackpressureBuffer();

        Mono<Void> session = wsClient.execute(URI.create(wsUrl), ws -> {
            Flux<WebSocketMessage> send = Flux.just(
                    ws.textMessage(SUB_HEADS),
                    ws.textMessage(SUB_LOGS));
            Flux<Void> receive = ws.receive()
                    .map(WebSocketMessage::getPayloadAsText)
                    .flatMap(msg -> Flux.fromIterable(parseMessage(msg)))
                    .doOnNext(sink::tryEmitNext)
                    .then().flux();
            return ws.send(send).thenMany(receive).then();
        });

        return sink.asFlux().mergeWith(session.cast(RawTxEvent.class));
    }

    private List<RawTxEvent> parseMessage(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            if (!"eth_subscription".equals(root.path("method").asText())) return List.of();

            JsonNode result = root.path("params").path("result");

            // newHeads: has "hash" field
            if (result.has("hash")) {
                String blockHash = result.path("hash").asText();
                Map<String, Object> raw = new HashMap<>();
                raw.put("blockHash",   blockHash);
                raw.put("blockNumber", result.path("number").asText("0x0"));
                raw.put("eventType",   "newHead");
                return List.of(new RawTxEvent(blockHash, chainId, Instant.now(), raw));
            }

            // ERC-20 Transfer log: has "transactionHash"
            if (result.has("transactionHash")) {
                String txHash = result.path("transactionHash").asText();
                int logIndex  = hexToInt(result.path("logIndex").asText("0x0"));
                Map<String, Object> raw = new HashMap<>();
                raw.put("txHash",    txHash);
                raw.put("address",   result.path("address").asText());
                raw.put("data",      result.path("data").asText());
                raw.put("topics",    objectMapper.convertValue(result.path("topics"), List.class));
                raw.put("logIndex",  logIndex);
                raw.put("eventType", "erc20Transfer");
                return List.of(new RawTxEvent(txHash + "-" + logIndex, chainId, Instant.now(), raw));
            }

            return List.of();
        } catch (Exception e) {
            log.debug("EvmConnector: parse error — {}", e.getMessage());
            return List.of();
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static int hexToInt(String hex) {
        try {
            String s = hex.startsWith("0x") || hex.startsWith("0X") ? hex.substring(2) : hex;
            return s.isBlank() ? 0 : Integer.parseInt(s, 16);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
