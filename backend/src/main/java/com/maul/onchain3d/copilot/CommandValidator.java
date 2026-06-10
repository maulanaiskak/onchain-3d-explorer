package com.maul.onchain3d.copilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maul.onchain3d.copilot.dto.SceneCommand;
import com.maul.onchain3d.copilot.dto.ValidationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validates raw {@link SceneCommand} lists from the LLM against the grounded node-ID whitelist.
 *
 * <p>Commands whose node IDs are not in {@code allowedNodeIds} are dropped silently.
 * The result is capped at 12 commands. {@code grounded} is {@code true} only when every
 * raw command survived filtering and the input was non-empty.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommandValidator {

    private static final int MAX_COMMANDS = 12;

    @SuppressWarnings("unused")
    private final ObjectMapper objectMapper;

    /**
     * Filters and grounds the raw command list.
     *
     * @param rawCommands    commands produced by the LLM (may reference ungrounded node IDs)
     * @param allowedNodeIds node IDs present in the retrieval context
     * @return {@link ValidationResult} with grounded, capped commands
     */
    public ValidationResult validate(List<SceneCommand> rawCommands, Set<String> allowedNodeIds) {
        if (rawCommands == null || rawCommands.isEmpty()) {
            return new ValidationResult(List.of(), false);
        }

        List<SceneCommand> filtered = rawCommands.stream()
                .filter(cmd -> {
                    Set<String> ids = extractNodeIds(cmd);
                    boolean passes = ids.isEmpty() || allowedNodeIds.containsAll(ids);
                    if (!passes) {
                        log.debug("CommandValidator: dropping command {} — unknown node IDs {}", cmd.getClass().getSimpleName(), ids);
                    }
                    return passes;
                })
                .limit(MAX_COMMANDS)
                .toList();

        boolean grounded = filtered.size() == rawCommands.size();
        return new ValidationResult(filtered, grounded);
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private Set<String> extractNodeIds(SceneCommand cmd) {
        return switch (cmd) {
            case SceneCommand.Highlight h       -> new HashSet<>(h.nodeIds());
            case SceneCommand.FlyTo f           -> Set.of(f.target());
            case SceneCommand.Focus f           -> Set.of(f.nodeId());
            case SceneCommand.ExpandNeighbors e -> Set.of(e.nodeId());
            case SceneCommand.Annotate a        -> Set.of(a.nodeId());
            default                             -> Set.of();
        };
    }
}
