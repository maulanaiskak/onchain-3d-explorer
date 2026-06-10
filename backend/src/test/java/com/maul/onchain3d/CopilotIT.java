package com.maul.onchain3d;

import com.maul.onchain3d.copilot.EmbeddingService;
import com.maul.onchain3d.copilot.VertexLlmClient;
import com.maul.onchain3d.copilot.dto.CopilotResponse;
import com.maul.onchain3d.copilot.dto.SceneCommand;
import com.maul.onchain3d.ingest.EvmConnector;
import com.maul.onchain3d.ingest.SolanaConnector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

// Uses the docker-compose postgres already running on port 5433 (no Testcontainers needed).
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@TestPropertySource(properties = {
        "spring.r2dbc.url=r2dbc:postgresql://localhost:5433/onchain3d",
        "spring.datasource.url=jdbc:postgresql://localhost:5433/onchain3d",
        "spring.flyway.url=jdbc:postgresql://localhost:5433/onchain3d"
})
class CopilotIT {

    @Autowired
    WebTestClient webTestClient;

    @MockitoBean SolanaConnector solanaConnector;
    @MockitoBean EvmConnector evmConnector;
    @MockitoBean VertexLlmClient vertexLlmClient;
    @MockitoBean EmbeddingService embeddingService;

    @Test
    @Timeout(20)
    void copilotEndpointEmitsCommandsEvent() {
        CopilotResponse stubResponse = new CopilotResponse(
                "Resetting the 3D view.",
                List.of(new SceneCommand.ResetView()),
                true);

        when(vertexLlmClient.complete(anyString(), anyString()))
                .thenReturn(Mono.just(stubResponse));
        when(embeddingService.embedText(anyString()))
                .thenReturn(Mono.just(new float[768]));

        String firstEvent = webTestClient
                .mutate().responseTimeout(Duration.ofSeconds(10)).build()
                .post()
                .uri("/api/copilot")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"intent\":\"test\",\"chain\":\"solana\",\"window\":\"1h\"}")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .returnResult(String.class)
                .getResponseBody()
                .blockFirst(Duration.ofSeconds(10));

        assertThat(firstEvent).isNotNull();
        assertThat(firstEvent).contains("commands");
    }
}
