package com.maul.onchain3d.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maul.onchain3d.config.AppProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Spring configuration that wires {@link ChainConnector} beans into the application context.
 *
 * <p>API keys are read from {@link AppProperties} (bound from {@code application.yml} /
 * environment variables). When a key is absent the connector automatically falls back to
 * the free public RPC endpoint.
 */
@Configuration
public class IngestConfig {

    @Bean
    public SolanaConnector solanaConnector(AppProperties props,
                                           WebClient.Builder webClientBuilder,
                                           ObjectMapper objectMapper) {
        return new SolanaConnector(
                props.ingest().heliusApiKey(),
                webClientBuilder,
                objectMapper);
    }

    @Bean
    public EvmConnector evmConnector(AppProperties props,
                                     WebClient.Builder webClientBuilder,
                                     ObjectMapper objectMapper) {
        // Default chain id from environment; falls back to "evm:base"
        String chainId = System.getenv().getOrDefault("EVM_CHAIN", "evm:base");
        return new EvmConnector(
                props.ingest().alchemyApiKey(),
                chainId,
                webClientBuilder,
                objectMapper);
    }
}
