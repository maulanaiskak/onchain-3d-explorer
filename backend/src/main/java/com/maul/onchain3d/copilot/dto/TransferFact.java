package com.maul.onchain3d.copilot.dto;

/**
 * Lightweight transfer fact used to ground copilot responses with hard on-chain data.
 */
public record TransferFact(String fromAddr, String toAddr, String asset, double valueNorm, String blockTime) {}
