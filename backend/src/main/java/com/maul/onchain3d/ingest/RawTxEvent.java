package com.maul.onchain3d.ingest;

import java.time.Instant;
import java.util.Map;

/**
 * Raw transaction event as received from a chain data provider (Helius, Alchemy, etc.)
 * before any normalisation. Carries the original provider payload in {@code raw}.
 */
public record RawTxEvent(String txHash, String chain, Instant blockTime, Map<String, Object> raw) {}
