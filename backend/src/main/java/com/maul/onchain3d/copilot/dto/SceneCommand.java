package com.maul.onchain3d.copilot.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;

/**
 * Sealed interface representing structured 3-D scene commands emitted by the copilot.
 *
 * <p>Jackson polymorphic serialisation uses the {@code type} discriminator so that
 * TypeScript clients can {@code switch(cmd.type)} to dispatch each command.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = SceneCommand.Filter.class,          name = "filter"),
        @JsonSubTypes.Type(value = SceneCommand.Highlight.class,       name = "highlight"),
        @JsonSubTypes.Type(value = SceneCommand.FlyTo.class,           name = "flyTo"),
        @JsonSubTypes.Type(value = SceneCommand.Focus.class,           name = "focus"),
        @JsonSubTypes.Type(value = SceneCommand.ExpandNeighbors.class, name = "expandNeighbors"),
        @JsonSubTypes.Type(value = SceneCommand.Annotate.class,        name = "annotate"),
        @JsonSubTypes.Type(value = SceneCommand.ResetView.class,       name = "resetView"),
})
public sealed interface SceneCommand
        permits SceneCommand.Filter,
                SceneCommand.Highlight,
                SceneCommand.FlyTo,
                SceneCommand.Focus,
                SceneCommand.ExpandNeighbors,
                SceneCommand.Annotate,
                SceneCommand.ResetView {

    /** Filter the scene to transfers within {@code window} with optional value and asset constraints. */
    record Filter(String window, Double minValue, String asset) implements SceneCommand {}

    /** Highlight a set of nodes with a given visual style (e.g. "pulse", "glow"). */
    record Highlight(List<String> nodeIds, String style) implements SceneCommand {}

    /** Animate camera to fly to a target node or address. */
    record FlyTo(String target, Integer durationMs) implements SceneCommand {}

    /** Focus the graph on a specific node, expanding up to {@code depth} hops. */
    record Focus(String nodeId, Integer depth) implements SceneCommand {}

    /** Expand neighbour addresses of a node in a given direction. */
    record ExpandNeighbors(String nodeId, String direction, Integer limit) implements SceneCommand {}

    /** Add a text annotation label to a node in the 3-D scene. */
    record Annotate(String nodeId, String label) implements SceneCommand {}

    /** Reset the camera and filters to the default view. */
    record ResetView() implements SceneCommand {}
}
