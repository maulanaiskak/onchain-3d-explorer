package com.maul.onchain3d.normalize;

import com.maul.onchain3d.config.AppProperties;
import com.maul.onchain3d.ingest.RawTxEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class TransferNormalizerTest {

    private TransferNormalizer normalizer;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties(
                new AppProperties.Vertex("project", "us-central1", "gemini-1.5-flash", "text-embedding-004"),
                new AppProperties.Ingest("", "", "1h", 0.001),
                new AppProperties.Copilot(3));
        normalizer = new TransferNormalizer(props);
    }

    // -------------------------------------------------------------------------
    // Solana
    // -------------------------------------------------------------------------

    @Test
    void solana_nativeTransfer_emitsEdge() {
        Map<String, Object> raw = new HashMap<>();
        raw.put("preBalances",  List.of(1_000_000_000L, 0L));         // 1 SOL, 0 SOL
        raw.put("postBalances", List.of(900_000_000L,   100_000_000L)); // sender sends 0.1 SOL
        raw.put("accountKeys",  List.of("FromAddr1111", "ToAddr22222"));

        RawTxEvent event = new RawTxEvent("sig1", "solana", Instant.now(), raw);

        StepVerifier.create(normalizer.normalize(event))
                .expectNextMatches(e ->
                        "FromAddr1111".equals(e.fromAddr())
                        && "ToAddr22222".equals(e.toAddr())
                        && e.valueNorm() > 0)
                .verifyComplete();
    }

    @Test
    void solana_selfTransfer_filtered() {
        Map<String, Object> raw = new HashMap<>();
        raw.put("preBalances",  List.of(1_000_000_000L, 0L));
        raw.put("postBalances", List.of(900_000_000L,   100_000_000L));
        raw.put("accountKeys",  List.of("SameAddr1111", "SameAddr1111")); // same address

        RawTxEvent event = new RawTxEvent("sig2", "solana", Instant.now(), raw);

        StepVerifier.create(normalizer.normalize(event))
                .verifyComplete();
    }

    @Test
    void solana_zeroValue_filtered() {
        Map<String, Object> raw = new HashMap<>();
        raw.put("preBalances",  List.of(1_000_000_000L, 0L));
        raw.put("postBalances", List.of(1_000_000_000L, 0L)); // no change
        raw.put("accountKeys",  List.of("FromAddr3333", "ToAddr44444"));

        RawTxEvent event = new RawTxEvent("sig3", "solana", Instant.now(), raw);

        StepVerifier.create(normalizer.normalize(event))
                .verifyComplete();
    }

    @Test
    void solana_missingBalances_emitsEmpty() {
        Map<String, Object> raw = new HashMap<>();
        // no balance arrays

        RawTxEvent event = new RawTxEvent("sig4", "solana", Instant.now(), raw);

        StepVerifier.create(normalizer.normalize(event))
                .verifyComplete();
    }

    // -------------------------------------------------------------------------
    // EVM — native ETH
    // -------------------------------------------------------------------------

    @Test
    void evm_nativeTransfer_emitsEdge() {
        Map<String, Object> raw = new HashMap<>();
        raw.put("from",      "0xaaaa");
        raw.put("to",        "0xbbbb");
        raw.put("value",     "0xDE0B6B3A7640000"); // 1 ETH in wei
        raw.put("input",     "0x");
        raw.put("eventType", "nativeTransfer");

        RawTxEvent event = new RawTxEvent("0xtxhash1", "evm:base", Instant.now(), raw);

        StepVerifier.create(normalizer.normalize(event))
                .expectNextMatches(e -> "0xaaaa".equals(e.fromAddr())
                        && "0xbbbb".equals(e.toAddr())
                        && e.valueNorm() > 0)
                .verifyComplete();
    }

    @Test
    void evm_zeroValue_filtered() {
        Map<String, Object> raw = new HashMap<>();
        raw.put("from",      "0xaaaa");
        raw.put("to",        "0xbbbb");
        raw.put("value",     "0x0");
        raw.put("eventType", "nativeTransfer");

        RawTxEvent event = new RawTxEvent("0xtxhash2", "evm:base", Instant.now(), raw);

        StepVerifier.create(normalizer.normalize(event))
                .verifyComplete();
    }

    @Test
    void evm_burnAddress_filtered() {
        Map<String, Object> raw = new HashMap<>();
        raw.put("from",      "0xaaaa");
        raw.put("to",        "0x000000000000000000000000000000000000dead");
        raw.put("value",     "0xDE0B6B3A7640000");
        raw.put("eventType", "nativeTransfer");

        RawTxEvent event = new RawTxEvent("0xtxhash3", "evm:base", Instant.now(), raw);

        StepVerifier.create(normalizer.normalize(event))
                .verifyComplete();
    }

    // -------------------------------------------------------------------------
    // Dedup
    // -------------------------------------------------------------------------

    @Test
    void dedup_sameKeyTwice_secondFiltered() {
        Map<String, Object> raw = new HashMap<>();
        raw.put("preBalances",  List.of(1_000_000_000L, 0L));
        raw.put("postBalances", List.of(900_000_000L,   100_000_000L));
        raw.put("accountKeys",  List.of("FromAddrDup1", "ToAddrDup22"));

        RawTxEvent event = new RawTxEvent("sigDup", "solana", Instant.now(), raw);

        // First call — should emit
        Flux<Edge> first = normalizer.normalize(event);
        StepVerifier.create(first).expectNextCount(1).verifyComplete();

        // Second call with same txHash — should be deduped
        Flux<Edge> second = normalizer.normalize(event);
        StepVerifier.create(second).verifyComplete();
    }

    // -------------------------------------------------------------------------
    // ERC-20
    // -------------------------------------------------------------------------

    @Test
    void evm_erc20Transfer_emitsEdge() {
        String from = "0x" + "0".repeat(24) + "aaaa0000bbbb0000cccc";
        String to   = "0x" + "0".repeat(24) + "dddd0000eeee0000ffff";

        Map<String, Object> raw = new HashMap<>();
        raw.put("eventType", "erc20Transfer");
        raw.put("address",   "0xcontractaddr");
        raw.put("topics",    List.of(
                "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef",
                from, to));
        raw.put("data",      "0xDE0B6B3A7640000"); // 1 token (18 decimals)

        RawTxEvent event = new RawTxEvent("0xtxerc20:0", "evm:base", Instant.now(), raw);

        StepVerifier.create(normalizer.normalize(event))
                .expectNextMatches(e -> e.asset().startsWith("ERC20:"))
                .verifyComplete();
    }
}
