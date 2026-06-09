package com.maul.onchain3d.stream.dto;

/**
 * SSE wire format for a graph node (wallet address).
 *
 * @param id       wallet address (unique node identifier)
 * @param chain    chain identifier (e.g. "solana", "ethereum")
 * @param label    optional human-readable label; may be null
 * @param weight   aggregated transfer value weight for visual sizing
 * @param recency  recency score in [0, 1] — 1 = just seen, decays over time
 * @param isWhale  true when the address is classified as a whale
 */
public record NodeDTO(String id, String chain, String label, double weight, double recency, boolean isWhale) {}
