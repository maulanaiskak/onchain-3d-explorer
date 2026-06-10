package com.maul.onchain3d.stream;

import com.maul.onchain3d.graph.GraphService;
import com.maul.onchain3d.stream.dto.Delta;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.Instant;

/**
 * WebFlux SSE controller that streams the live on-chain graph to browser clients.
 *
 * <p>On connection the client receives:
 * <ol>
 *   <li>A {@code snapshot} event with the current windowed graph.</li>
 *   <li>Continuous {@code upsertNodes} / {@code upsertEdges} / {@code decay} events.</li>
 *   <li>A {@code heartbeat} keep-alive every 15 s.</li>
 * </ol>
 *
 * <p>Disconnecting the client automatically cancels the upstream subscription (WebFlux
 * honours {@code ServerSentEvent} cancellation propagation).
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class SseController {

    private final GraphService graphService;
    private final DeltaPublisher deltaPublisher;
    private final SseMapper sseMapper;

    /**
     * Opens an SSE stream for the given chain and time window.
     *
     * @param chain  chain identifier (e.g. {@code "solana"})
     * @param window time window shorthand (e.g. {@code "1h"}, default {@code "1h"})
     */
    @GetMapping(value = "/api/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(
            @RequestParam String chain,
            @RequestParam(defaultValue = "1h") String window) {

        log.info("SSE client connected chain={} window={}", chain, window);

        Flux<ServerSentEvent<String>> snapshot = graphService.snapshot(chain, window)
                .map(s -> toSse("snapshot", s))
                .flux()
                .doOnNext(e -> log.debug("SSE snapshot sent chain={}", chain));

        Flux<ServerSentEvent<String>> live = deltaPublisher.subscribe(chain)
                .map(d -> toSse(deltaType(d), d))
                .doOnError(e -> log.error("SSE live stream error chain={}", chain, e));

        Flux<ServerSentEvent<String>> heartbeat = Flux.interval(Duration.ofSeconds(15))
                .map(t -> toSse("heartbeat", new Delta.Heartbeat(Instant.now().toString())));

        return Flux.merge(snapshot, live, heartbeat)
                .doOnCancel(() -> log.info("SSE client disconnected chain={}", chain));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private ServerSentEvent<String> toSse(String event, Object data) {
        return ServerSentEvent.<String>builder()
                .event(event)
                .data(sseMapper.toJson(data))
                .build();
    }

    private static String deltaType(Delta delta) {
        return switch (delta) {
            case Delta.Snapshot ignored      -> "snapshot";
            case Delta.UpsertNodes ignored   -> "upsertNodes";
            case Delta.UpsertEdges ignored   -> "upsertEdges";
            case Delta.Decay ignored         -> "decay";
            case Delta.Heartbeat ignored     -> "heartbeat";
        };
    }
}
