package com.maul.onchain3d;

import com.maul.onchain3d.copilot.EmbeddingService;
import com.maul.onchain3d.copilot.VertexLlmClient;
import com.maul.onchain3d.copilot.dto.CopilotResponse;
import com.maul.onchain3d.copilot.dto.SceneCommand;
import com.maul.onchain3d.ingest.EvmConnector;
import com.maul.onchain3d.ingest.SolanaConnector;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Integration tests for the {@code POST /api/copilot} SSE endpoint.
 *
 * <p>Uses Testcontainers to start a real pgvector Postgres instance.
 * {@link VertexLlmClient} and {@link EmbeddingService} are mocked to prevent
 * outbound network calls in CI.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Testcontainers
class CopilotIT {

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

    @MockBean
    SolanaConnector solanaConnector;

    @MockBean
    EvmConnector evmConnector;

    @MockBean
    VertexLlmClient vertexLlmClient;

    @MockBean
    EmbeddingService embeddingService;

    @Test
    void copilotEndpointEmitsCommandsEvent() {
        CopilotResponse stubResponse = new CopilotResponse(
                "Resetting the 3D view.",
                List.of(new SceneCommand.ResetView()),
                true);

        when(vertexLlmClient.complete(anyString(), anyString()))
                .thenReturn(Mono.just(stubResponse));
        when(embeddingService.embedText(anyString()))
                .thenReturn(Mono.just(new float[768]));

        String body = """
                {"intent":"test","chain":"solana","window":"1h"}
                """;

        String firstEvent = webTestClient
                .mutate().responseTimeout(Duration.ofSeconds(10)).build()
                .post()
                .uri("/api/copilot")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .returnResult(String.class)
                .getResponseBody()
                .blockFirst(Duration.ofSeconds(10));

        assertThat(firstEvent).isNotNull();
        assertThat(firstEvent).contains("commands").doesNotContain("\"event\":\"error\"");
    }
}
