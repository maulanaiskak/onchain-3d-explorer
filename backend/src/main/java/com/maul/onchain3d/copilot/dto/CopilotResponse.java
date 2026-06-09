package com.maul.onchain3d.copilot.dto;

import java.util.List;

/**
 * Structured response returned by the copilot LLM client.
 *
 * @param narrative human-readable explanation from the model
 * @param commands  list of structured 3-D scene commands to execute
 * @param grounded  true when the response is backed by hard on-chain facts
 */
public record CopilotResponse(String narrative, List<SceneCommand> commands, boolean grounded) {}
