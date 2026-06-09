package com.maul.onchain3d.ingest;

import reactor.core.publisher.Flux;

/**
 * Strategy interface for chain data sources.
 *
 * <p>Each implementation establishes a connection to a blockchain data provider
 * (websocket or polling) and emits a continuous stream of raw transaction events.
 * Implementations must handle reconnection internally via {@code retryWhen}.
 */
public interface ChainConnector {

    /**
     * Opens the connection and returns a never-ending stream of raw transaction events.
     * The returned {@link Flux} is cold — a new connection is established per subscription.
     */
    Flux<RawTxEvent> connect();

    /**
     * Returns the chain identifier this connector targets, e.g. {@code "solana"} or {@code "evm:base"}.
     */
    String chain();
}
