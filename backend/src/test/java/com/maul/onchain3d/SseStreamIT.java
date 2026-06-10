package com.maul.onchain3d;

import com.maul.onchain3d.copilot.EmbeddingService;
import com.maul.onchain3d.copilot.VertexLlmClient;
import com.maul.onchain3d.ingest.EvmConnector;
import com.maul.onchain3d.ingest.SolanaConnector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

// Uses the docker-compose postgres already running on port 5433 (no Testcontainers needed).
// Start with: docker-compose up -d from project root before running tests.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@TestPropertySource(properties = {
        "spring.r2dbc.url=r2dbc:postgresql://localhost:5433/onchain3d",
        "spring.datasource.url=jdbc:postgresql://localhost:5433/onchain3d",
        "spring.flyway.url=jdbc:postgresql://localhost:5433/onchain3d"
})
class SseStreamIT {

    @Autowired
    WebTestClient webTestClient;

    @MockitoBean
    SolanaConnector solanaConnector;

    @MockitoBean
    EvmConnector evmConnector;

    @MockitoBean
    VertexLlmClient vertexLlmClient;

    @MockitoBean
    EmbeddingService embeddingService;

    @Test
    @Timeout(20)
    void streamEndpointReturns200WithTextEventStreamContentType() {
        webTestClient
                .mutate().responseTimeout(Duration.ofSeconds(5)).build()
                .get()
                .uri("/api/stream?chain=solana")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM);
    }

    @Test
    @Timeout(20)
    void firstEventIsSnapshot() {
        String firstEvent = webTestClient
                .mutate().responseTimeout(Duration.ofSeconds(10)).build()
                .get()
                .uri("/api/stream?chain=solana")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .returnResult(String.class)
                .getResponseBody()
                .blockFirst(Duration.ofSeconds(10));

        assertThat(firstEvent).contains("snapshot");
    }
}
