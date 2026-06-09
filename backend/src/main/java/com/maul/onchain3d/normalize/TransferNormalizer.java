package com.maul.onchain3d.normalize;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.maul.onchain3d.config.AppProperties;
import com.maul.onchain3d.ingest.RawTxEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Normalises raw chain events into canonical {@link Edge} records.
 *
 * <p>Handles Solana (native SOL via pre/post balances) and EVM chains (native ETH +
 * ERC-20 Transfer log events). Applies value floor filtering, self-transfer rejection,
 * burn-address rejection, and in-memory deduplication.
 */
@Slf4j
@Component
public class TransferNormalizer {

    private static final Set<String> BURN_ADDRESSES = Set.of(
            "0x000000000000000000000000000000000000dead",
            "0x0000000000000000000000000000000000000000",
            "11111111111111111111111111111111"  // Solana system program / burn
    );

    private static final BigDecimal LAMPORTS_PER_SOL = BigDecimal.valueOf(1_000_000_000L);
    private static final BigDecimal WEI_PER_ETH      = BigDecimal.TEN.pow(18);

    /** In-memory dedup cache keyed on {@code "txHash:logIndex"}. */
    private final Cache<String, Boolean> seenCache = Caffeine.newBuilder()
            .maximumSize(50_000)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build();

    private final double valueFloor;

    public TransferNormalizer(AppProperties props) {
        this.valueFloor = props.ingest().valueFloorNorm();
    }

    /**
     * Normalises a single raw event into zero or more edges.
     */
    public Flux<Edge> normalize(RawTxEvent raw) {
        return switch (raw.chain()) {
            case "solana" -> normalizeSolana(raw);
            default       -> normalizeEvm(raw);
        };
    }

    // -------------------------------------------------------------------------
    // Solana
    // -------------------------------------------------------------------------

    private Flux<Edge> normalizeSolana(RawTxEvent raw) {
        Map<String, Object> data = raw.raw();

        @SuppressWarnings("unchecked")
        List<Number> preBalances  = (List<Number>) data.get("preBalances");
        @SuppressWarnings("unchecked")
        List<Number> postBalances = (List<Number>) data.get("postBalances");
        @SuppressWarnings("unchecked")
        List<String> accountKeys  = (List<String>) data.get("accountKeys");

        if (preBalances == null || postBalances == null || accountKeys == null) {
            log.debug("normalizeSolana: missing balance data for txHash={}", raw.txHash());
            return Flux.empty();
        }

        int count = Math.min(Math.min(preBalances.size(), postBalances.size()), accountKeys.size());

        // Find the primary sender (largest negative delta) and all recipients (positive delta)
        String sender = null;
        long maxOut = 0;
        for (int i = 0; i < count; i++) {
            long delta = postBalances.get(i).longValue() - preBalances.get(i).longValue();
            if (delta < 0 && -delta > maxOut) {
                maxOut = -delta;
                sender = accountKeys.get(i);
            }
        }
        if (sender == null) return Flux.empty();

        final String from = sender;
        Flux<Edge> edges = Flux.empty();
        int logIdx = 0;
        for (int i = 0; i < count; i++) {
            long delta = postBalances.get(i).longValue() - preBalances.get(i).longValue();
            if (delta <= 0) continue;

            String to = accountKeys.get(i);
            BigDecimal valueRaw  = BigDecimal.valueOf(delta);
            double     valueNorm = valueRaw.divide(LAMPORTS_PER_SOL, 9, RoundingMode.HALF_UP).doubleValue();

            Edge edge = new Edge(raw.txHash(), logIdx++, "solana", from, to,
                    "SOL", valueRaw, valueNorm, raw.blockTime());
            Flux<Edge> candidate = Flux.just(edge);
            edges = edges.concatWith(candidate);
        }

        return edges.filter(e -> accept(e, "solana"));
    }

    // -------------------------------------------------------------------------
    // EVM
    // -------------------------------------------------------------------------

    private Flux<Edge> normalizeEvm(RawTxEvent raw) {
        Map<String, Object> data = raw.raw();
        String eventType = (String) data.getOrDefault("eventType", "nativeTransfer");

        return switch (eventType) {
            case "erc20Transfer" -> normalizeErc20(raw, data);
            default              -> normalizeNativeEth(raw, data);
        };
    }

    private Flux<Edge> normalizeNativeEth(RawTxEvent raw, Map<String, Object> data) {
        String from  = (String) data.get("from");
        String to    = (String) data.get("to");
        String value = (String) data.getOrDefault("value", "0x0");

        if (from == null || to == null) return Flux.empty();

        BigDecimal valueRaw;
        try {
            String hexVal = value.startsWith("0x") || value.startsWith("0X")
                    ? value.substring(2) : value;
            valueRaw = hexVal.isBlank() ? BigDecimal.ZERO : new BigDecimal(new java.math.BigInteger(hexVal, 16));
        } catch (NumberFormatException e) {
            log.debug("normalizeNativeEth: bad value hex={} txHash={}", value, raw.txHash());
            return Flux.empty();
        }

        double valueNorm = valueRaw.divide(WEI_PER_ETH, 18, RoundingMode.HALF_UP).doubleValue();
        Edge edge = new Edge(raw.txHash(), 0, raw.chain(), from.toLowerCase(), to.toLowerCase(),
                "ETH", valueRaw, valueNorm, raw.blockTime());

        return Flux.just(edge).filter(e -> accept(e, raw.chain()));
    }

    private Flux<Edge> normalizeErc20(RawTxEvent raw, Map<String, Object> data) {
        @SuppressWarnings("unchecked")
        List<String> topics = (List<String>) data.get("topics");
        String dataHex = (String) data.getOrDefault("data", "0x");

        if (topics == null || topics.size() < 3) return Flux.empty();

        // topics[0] = event sig, topics[1] = from (padded), topics[2] = to (padded)
        String from = stripPadding(topics.get(1));
        String to   = stripPadding(topics.get(2));

        // Extract log index from txHash (stored as "txHash:logIndex" in connector)
        int logIndex = 0;
        String txHashKey = raw.txHash();
        int colonIdx = txHashKey.lastIndexOf(':');
        if (colonIdx >= 0) {
            try { logIndex = Integer.parseInt(txHashKey.substring(colonIdx + 1)); } catch (NumberFormatException ignored) {}
            txHashKey = txHashKey.substring(0, colonIdx);
        }

        BigDecimal valueRaw;
        try {
            String hexVal = dataHex.startsWith("0x") || dataHex.startsWith("0X")
                    ? dataHex.substring(2) : dataHex;
            valueRaw = hexVal.isBlank() ? BigDecimal.ZERO : new BigDecimal(new java.math.BigInteger(hexVal, 16));
        } catch (NumberFormatException e) {
            log.debug("normalizeErc20: bad data hex txHash={}", raw.txHash());
            return Flux.empty();
        }

        // ERC-20 uses 18 decimals by default; exact decimals would require a contract call
        double valueNorm = valueRaw.divide(WEI_PER_ETH, 18, RoundingMode.HALF_UP).doubleValue();
        String contractAddr = ((String) data.getOrDefault("address", "unknown")).toLowerCase();

        Edge edge = new Edge(txHashKey, logIndex, raw.chain(),
                from.toLowerCase(), to.toLowerCase(),
                "ERC20:" + contractAddr, valueRaw, valueNorm, raw.blockTime());

        return Flux.just(edge).filter(e -> accept(e, raw.chain()));
    }

    // -------------------------------------------------------------------------
    // Shared filters
    // -------------------------------------------------------------------------

    private boolean accept(Edge edge, String chain) {
        // Self-transfer
        if (edge.fromAddr().equalsIgnoreCase(edge.toAddr())) {
            log.trace("filter: self-transfer txHash={}", edge.txHash());
            return false;
        }

        // Burn / null addresses
        if (BURN_ADDRESSES.contains(edge.fromAddr().toLowerCase())
                || BURN_ADDRESSES.contains(edge.toAddr().toLowerCase())) {
            log.trace("filter: burn address txHash={}", edge.txHash());
            return false;
        }

        // Value floor
        if (edge.valueNorm() < valueFloor) {
            log.trace("filter: below floor ({}) txHash={}", edge.valueNorm(), edge.txHash());
            return false;
        }

        // Dedup
        String key = edge.txHash() + ":" + edge.logIndex();
        if (seenCache.getIfPresent(key) != null) {
            log.trace("filter: dedup key={}", key);
            return false;
        }
        seenCache.put(key, Boolean.TRUE);
        return true;
    }

    private static String stripPadding(String paddedHex) {
        if (paddedHex == null) return "";
        // Topics are 32-byte hex; address is last 20 bytes
        String hex = paddedHex.startsWith("0x") || paddedHex.startsWith("0X")
                ? paddedHex.substring(2) : paddedHex;
        if (hex.length() > 40) hex = hex.substring(hex.length() - 40);
        return "0x" + hex;
    }
}
