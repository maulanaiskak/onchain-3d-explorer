package com.maul.onchain3d.copilot;

import com.maul.onchain3d.copilot.dto.CopilotRequest;
import com.maul.onchain3d.stream.SseMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * WebFlux controller that streams copilot responses as Server-Sent Events.
 *
 * <p>Clients receive one {@code commands} SSE event per request containing the full
 * {@code CopilotResponse} JSON. On error, an {@code error} event is emitted instead.
 *
 * <p>Example:
 * <pre>
 * curl -N -X POST \
 *   -H 'Content-Type: application/json' \
 *   -d '{"intent":"show whales","chain":"solana","window":"1h"}' \
 *   http://localhost:8080/api/copilot
 * </pre>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class CopilotController {

    private static final String DEFAULT_WINDOW = "1h";

    private final CopilotOrchestrator orchestrator;
    private final SseMapper sseMapper;

    /**
     * Processes a copilot intent and returns a validated response as an SSE stream.
     *
     * @param req request containing intent, chain, and optional window
     * @return {@code Flux<ServerSentEvent>} with {@code commands} or {@code error} event
     */
    @PostMapping(value = "/api/copilot", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> copilot(@RequestBody CopilotRequest req) {
        String window = req.window() != null ? req.window() : DEFAULT_WINDOW;

        log.info("CopilotController: intent='{}' chain={} window={}", req.intent(), req.chain(), window);

        return orchestrator.process(req.intent(), req.chain(), window)
                .map(resp -> ServerSentEvent.<String>builder()
                        .event("commands")
                        .data(sseMapper.toJson(resp))
                        .build())
                .flux()
                .onErrorResume(e -> {
                    log.error("CopilotController: processing error intent='{}'", req.intent(), e);
                    return Flux.just(
                            ServerSentEvent.<String>builder()
                                    .event("error")
                                    .data("{\"message\":\"" + sanitise(e.getMessage()) + "\"}")
                                    .build());
                });
    }

    private static String sanitise(String message) {
        if (message == null) return "internal error";
        return message.replace("\"", "'").replace("\n", " ");
    }
}
