package com.maul.onchain3d.copilot;

import com.maul.onchain3d.copilot.dto.AddressSummary;
import com.maul.onchain3d.copilot.dto.TransferFact;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Assembles system and user prompts for the Vertex AI copilot request.
 *
 * <p>The system prompt instructs the model to produce grounded JSON only.
 * The user prompt injects hard SQL facts, semantic neighbours, and the
 * allowed node-ID whitelist so the model cannot hallucinate addresses.
 */
@Component
public class PromptBuilder {

    /** System prompt constant injected into every LLM request. */
    public static final String SYSTEM_PROMPT =
            """
            You translate a user's intent into a narrative + scene commands for a 3D on-chain graph explorer.
            You MAY ONLY reference node IDs present in <context>. Never invent addresses.
            Output strictly valid JSON matching the provided schema. If you cannot ground the answer, set grounded=false and commands=[].
            """;

    private static final int MAX_NODE_IDS = 100;

    /**
     * Builds the user-facing prompt that provides retrieval context and the user's intent.
     *
     * @param intent natural-language user query
     * @param ctx    grounded retrieval context from the vector + SQL layers
     * @return formatted prompt string
     */
    public String buildUserPrompt(String intent, RetrievalContext ctx) {
        String hardFacts = formatTransfers(ctx.topTransfers());
        String neighbors = formatNeighbors(ctx.neighbors());
        String nodeIds   = ctx.allowedNodeIds().stream()
                .limit(MAX_NODE_IDS)
                .collect(Collectors.joining(","));

        return """
                <hard_facts>
                %s
                </hard_facts>
                <neighbors>
                %s
                </neighbors>
                <node_ids>%s</node_ids>
                USER INTENT: %s
                """.formatted(hardFacts, neighbors, nodeIds, intent);
    }

    // -------------------------------------------------------------------------
    // Formatting helpers
    // -------------------------------------------------------------------------

    private String formatTransfers(List<TransferFact> transfers) {
        if (transfers == null || transfers.isEmpty()) {
            return "(no transfers in window)";
        }
        return transfers.stream()
                .map(t -> "%s → %s  asset=%s  value=%.4f  time=%s"
                        .formatted(t.fromAddr(), t.toAddr(), t.asset(), t.valueNorm(), t.blockTime()))
                .collect(Collectors.joining("\n"));
    }

    private String formatNeighbors(List<AddressSummary> neighbors) {
        if (neighbors == null || neighbors.isEmpty()) {
            return "(no semantic neighbours found)";
        }
        return neighbors.stream()
                .map(n -> "[%.3f] %s — %s".formatted(n.similarity(), n.address(), n.summary()))
                .collect(Collectors.joining("\n"));
    }
}
