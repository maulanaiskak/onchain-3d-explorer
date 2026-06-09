package com.maul.onchain3d.normalize;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Normalised in-flight transfer record produced by the normalisation layer.
 * Not persisted directly — maps 1-to-1 to a {@code transfer} table row after persistence.
 */
public record Edge(
        String txHash,
        int logIndex,
        String chain,
        String fromAddr,
        String toAddr,
        String asset,
        BigDecimal valueRaw,
        double valueNorm,
        Instant blockTime) {}
