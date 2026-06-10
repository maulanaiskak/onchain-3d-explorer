package com.maul.onchain3d.copilot.dto;

import java.util.List;

/**
 * Result of command validation: the filtered, capped command list and a grounding flag.
 *
 * @param commands  commands that passed node-ID grounding and are capped at 12
 * @param grounded  true when all raw commands passed validation and the input was non-empty
 */
public record ValidationResult(List<SceneCommand> commands, boolean grounded) {}
