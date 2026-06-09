package com.maul.onchain3d.config;

import com.google.auth.oauth2.GoogleCredentials;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.util.List;

/**
 * Spring configuration for Vertex AI infrastructure beans.
 *
 * <p>Provides a {@link GoogleCredentials} bean using Application Default Credentials (ADC)
 * and a dedicated {@link WebClient} for Vertex AI API calls.
 */
@Slf4j
@Configuration
public class VertexConfig {

    private static final String CLOUD_PLATFORM_SCOPE =
            "https://www.googleapis.com/auth/cloud-platform";

    /**
     * Google ADC credentials scoped to Cloud Platform.
     *
     * <p>Falls back to a no-op stub when credentials are unavailable (e.g. CI, local dev
     * without {@code gcloud auth application-default login}).
     */
    @Bean
    public GoogleCredentials googleCredentials() {
        try {
            return GoogleCredentials.getApplicationDefault()
                    .createScoped(List.of(CLOUD_PLATFORM_SCOPE));
        } catch (IOException e) {
            log.warn("VertexConfig: ADC credentials not available — Vertex AI calls will fail. "
                    + "Run 'gcloud auth application-default login' for local dev.");
            // Return a null-safe stub so the context starts without credentials on dev/CI
            return GoogleCredentials.create(null);
        }
    }

    /**
     * WebClient pre-configured for Vertex AI generativelanguage endpoints.
     * Uses a dedicated qualifier so it doesn't conflict with other WebClient beans.
     */
    @Bean("vertexWebClient")
    public WebClient vertexWebClient(WebClient.Builder builder) {
        return builder
                .codecs(c -> c.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();
    }
}
