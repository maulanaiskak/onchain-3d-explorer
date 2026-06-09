package com.maul.onchain3d.stream.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;

/**
 * Sealed SSE delta message sent to connected frontend clients.
 *
 * <p>Jackson polymorphic serialisation uses the {@code type} discriminator field so that
 * TypeScript clients can {@code switch(delta.type)} to handle each variant.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = Delta.Snapshot.class,     name = "snapshot"),
        @JsonSubTypes.Type(value = Delta.UpsertNodes.class,  name = "upsertNodes"),
        @JsonSubTypes.Type(value = Delta.UpsertEdges.class,  name = "upsertEdges"),
        @JsonSubTypes.Type(value = Delta.Decay.class,        name = "decay"),
        @JsonSubTypes.Type(value = Delta.Heartbeat.class,    name = "heartbeat"),
})
public sealed interface Delta
        permits Delta.Snapshot, Delta.UpsertNodes, Delta.UpsertEdges, Delta.Decay, Delta.Heartbeat {

    /** Full graph snapshot sent on initial connection or after reconnect. */
    record Snapshot(List<NodeDTO> nodes, List<EdgeDTO> edges, String window, String serverTime)
            implements Delta {}

    /** Partial update — upsert one or more nodes. */
    record UpsertNodes(List<NodeDTO> nodes) implements Delta {}

    /** Partial update — upsert one or more edges. */
    record UpsertEdges(List<EdgeDTO> edges) implements Delta {}

    /** Signal to fade/remove stale nodes and edges from the 3-D scene. */
    record Decay(List<String> nodeIds, List<String> edgeIds) implements Delta {}

    /** Keep-alive ping carrying server timestamp. */
    record Heartbeat(String serverTime) implements Delta {}
}
