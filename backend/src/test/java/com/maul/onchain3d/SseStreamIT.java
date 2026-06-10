package com.maul.onchain3d;

import com.maul.onchain3d.copilot.EmbeddingService;
import com.maul.onchain3d.copilot.VertexLlmClient;
import com.maul.onchain3d.ingest.EvmConnector;
import com.maul.onchain3d.ingest.SolanaConnector;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the {@code GET /api/stream} SSE endpoint.
 *
 * <p>Uses Testcontainers to start a real pgvector Postgres instance.
 * All external chain connectors and LLM clients are mocked to prevent
 * outbound network calls in CI.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Testcontainers
class SseStreamIT {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("pgvector/pgvector:pg16")
                    .withDatabaseName("onchain3d_test");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () ->
                "r2dbc:postgresql://" + postgres.getHost() + ":" + postgres.getMappedPort(5432) + "/onchain3d_test");
        registry.add("spring.r2dbc.username", postgres::getUsername);
        registry.add("spring.r2dbc.password", postgres::getPassword);
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
    }

    @Autowired
    WebTestClient webTestClient;

    // External dependencies are mocked in test application context via @MockBean below.
    // Spring Boot will auto-wire stubs for beans declared in subclasses or via
    // @MockBean — we use a shared abstract base class pattern here so both ITs
    // can share the same container. The @MockBean annotations are placed at field
    // level for clarity.
    @MockBean
    SolanaConnector solanaConnector;

    @MockBean
    EvmConnector evmConnector;

    @MockBean
    VertexLlmClient vertexLlmClient;

    @MockBean
    EmbeddingService embeddingService;

    @Test
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
    void heartbeatEventArrivesWithinTwentySeconds() {
        String firstEvent = webTestClient
                .mutate().responseTimeout(Duration.ofSeconds(20)).build()
                .get()
                .uri("/api/stream?chain=solana")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .returnResult(String.class)
                .getResponseBody()
                .blockFirst(Duration.ofSeconds(20));

        assertThat(firstEvent).isNotNull();
    }
}
