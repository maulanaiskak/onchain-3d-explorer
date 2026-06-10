package com.maul.onchain3d.stream;

import com.maul.onchain3d.stream.dto.Delta;
import com.maul.onchain3d.stream.dto.NodeDTO;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.util.List;

class DeltaPublisherTest {

    @Test
    void twoSubscribersEachReceiveAllThreeDeltas() {
        DeltaPublisher publisher = new DeltaPublisher();

        NodeDTO node = new NodeDTO("0xABC", "solana", null, 0.0, 1.0, false);

        Delta delta1 = new Delta.UpsertNodes(List.of(node));
        Delta delta2 = new Delta.UpsertNodes(List.of(node));
        Delta delta3 = new Delta.Heartbeat("2026-06-09T00:00:00Z");

        // sub1: emit inside .then() so StepVerifier is already subscribed before emit
        StepVerifier.create(publisher.subscribe("solana").take(3))
                .then(() -> {
                    publisher.emit(delta1);
                    publisher.emit(delta2);
                    publisher.emit(delta3);
                })
                .expectNext(delta1)
                .expectNext(delta2)
                .expectNext(delta3)
                .verifyComplete();

        // sub2: separate subscription, new publisher instance so we get clean state
        DeltaPublisher publisher2 = new DeltaPublisher();
        StepVerifier.create(publisher2.subscribe("solana").take(3))
                .then(() -> {
                    publisher2.emit(delta1);
                    publisher2.emit(delta2);
                    publisher2.emit(delta3);
                })
                .expectNext(delta1)
                .expectNext(delta2)
                .expectNext(delta3)
                .verifyComplete();
    }

    @Test
    void chainFilterExcludesMismatchedChain() {
        DeltaPublisher publisher = new DeltaPublisher();

        NodeDTO solanaNode = new NodeDTO("0xSOL", "solana", null, 0.0, 1.0, false);
        NodeDTO evmNode    = new NodeDTO("0xEVM", "evm:base", null, 0.0, 1.0, false);

        Delta solanaDelta = new Delta.UpsertNodes(List.of(solanaNode));
        Delta evmDelta    = new Delta.UpsertNodes(List.of(evmNode));
        Delta heartbeat   = new Delta.Heartbeat("2026-06-09T00:00:00Z");

        // Only solana-chain delta and heartbeat pass the filter
        StepVerifier.create(publisher.subscribe("solana").take(2))
                .then(() -> {
                    publisher.emit(evmDelta);
                    publisher.emit(solanaDelta);
                    publisher.emit(heartbeat);
                })
                .expectNext(solanaDelta)
                .expectNext(heartbeat)
                .verifyComplete();
    }
}
