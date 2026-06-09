package com.maul.onchain3d.stream.dto;

/**
 * SSE wire format for a graph edge (token transfer).
 *
 * @param id        unique edge identifier (typically "{txHash}-{logIndex}")
 * @param from      source address
 * @param to        destination address
 * @param asset     token/asset identifier (e.g. "SOL", "USDC")
 * @param valueNorm normalised transfer value
 * @param blockTime ISO-8601 timestamp of the containing block
 */
public record EdgeDTO(String id, String from, String to, String asset, double valueNorm, String blockTime) {}
