package com.maul.onchain3d.copilot.dto;

/**
 * Request body for the {@code POST /api/copilot} endpoint.
 *
 * @param intent natural-language user query
 * @param chain  chain identifier, e.g. {@code "solana"} or {@code "ethereum"}
 * @param window time window shorthand, e.g. {@code "1h"}, {@code "24h"} (nullable — defaults to {@code "1h"})
 */
public record CopilotRequest(String intent, String chain, String window) {}
