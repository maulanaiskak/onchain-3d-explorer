package com.maul.onchain3d.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed configuration properties bound from the {@code app.*} namespace in application.yml.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Vertex vertex, Ingest ingest, Copilot copilot) {

    public record Vertex(
            String projectId,
            String location,
            String llmModel,
            String embedModel) {
    }

    public record Ingest(
            String heliusApiKey,
            String alchemyApiKey,
            String windowDefault,
            double valueFloorNorm) {
    }

    public record Copilot(int maxAgentSteps) {
    }
}
