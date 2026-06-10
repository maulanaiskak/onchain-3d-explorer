package com.maul.onchain3d.copilot;

import com.maul.onchain3d.copilot.dto.CopilotResponse;
import com.maul.onchain3d.copilot.dto.ValidationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Orchestrates the full copilot pipeline:
 * <ol>
 *   <li>Embed user intent via {@link EmbeddingService}.</li>
 *   <li>Retrieve grounded context via {@link RetrievalService}.</li>
 *   <li>Build system + user prompts via {@link PromptBuilder}.</li>
 *   <li>Call Vertex AI via {@link VertexLlmClient}.</li>
 *   <li>Validate and ground commands via {@link CommandValidator}.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CopilotOrchestrator {

    private final EmbeddingService embeddingService;
    private final RetrievalService retrievalService;
    private final VertexLlmClient vertexLlmClient;
    private final PromptBuilder promptBuilder;
    private final CommandValidator commandValidator;

    /**
     * Processes a copilot intent and returns a validated {@link CopilotResponse}.
     *
     * @param intent natural-language user query
     * @param chain  chain identifier, e.g. {@code "solana"}
     * @param window time window shorthand, e.g. {@code "1h"}
     * @return {@code Mono<CopilotResponse>} with grounded, validated commands
     */
    public Mono<CopilotResponse> process(String intent, String chain, String window) {
        return embeddingService.embedText(intent)
                .flatMap(vec -> retrievalService.getGroundedContext(vec, chain, window))
                .flatMap(ctx -> {
                    String systemPrompt = PromptBuilder.SYSTEM_PROMPT;
                    String userPrompt   = promptBuilder.buildUserPrompt(intent, ctx);

                    log.debug("CopilotOrchestrator: calling LLM chain={} window={} allowedNodes={}",
                            chain, window, ctx.allowedNodeIds().size());

                    return vertexLlmClient.complete(systemPrompt, userPrompt)
                            .map(raw -> {
                                ValidationResult validated = commandValidator.validate(
                                        raw.commands(), ctx.allowedNodeIds());

                                log.info("CopilotOrchestrator: grounded={} commands={}",
                                        validated.grounded(), validated.commands().size());

                                return new CopilotResponse(
                                        raw.narrative(),
                                        validated.commands(),
                                        validated.grounded());
                            });
                });
    }
}
