"use client";

import { useEffect, useRef } from "react";
import { useGraphStore } from "@/app/store/graph-store";
import { NodeDTO, EdgeDTO } from "./mock-data";

type DeltaType = "snapshot" | "upsertNodes" | "upsertEdges" | "decay" | "heartbeat";

interface SnapshotPayload  { type: "snapshot";    nodes: NodeDTO[]; edges: EdgeDTO[] }
interface UpsertNodesPayload { type: "upsertNodes"; nodes: NodeDTO[] }
interface UpsertEdgesPayload { type: "upsertEdges"; edges: EdgeDTO[] }
interface DecayPayload      { type: "decay";        nodeIds: string[]; edgeIds: string[] }
interface HeartbeatPayload  { type: "heartbeat";    serverTime: string }

type Delta =
  | SnapshotPayload
  | UpsertNodesPayload
  | UpsertEdgesPayload
  | DecayPayload
  | HeartbeatPayload;

export type ConnectionStatus = "connecting" | "connected" | "disconnected" | "error";

export function useSseStream(
  chain: string,
  window: string,
  onStatusChange: (s: ConnectionStatus) => void,
  disabled = false
) {
  const { upsertNodes, upsertEdges, decayNodes, decayEdges, replaceGraph } = useGraphStore();
  const esRef = useRef<EventSource | null>(null);
  const retryRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const retryDelay = useRef(1000);

  useEffect(() => {
    if (disabled) return;
    let cancelled = false;

    function connect() {
      if (cancelled) return;
      onStatusChange("connecting");

      const base = process.env.NEXT_PUBLIC_BACKEND_URL ?? "http://localhost:8080";
      const url = `${base}/api/stream?chain=${encodeURIComponent(chain)}&window=${encodeURIComponent(window)}`;
      const es = new EventSource(url);
      esRef.current = es;

      const handleEvent = (type: DeltaType) => (e: MessageEvent) => {
        try {
          const payload = JSON.parse(e.data) as Delta;

          switch (payload.type ?? type) {
            case "snapshot":
              replaceGraph((payload as SnapshotPayload).nodes, (payload as SnapshotPayload).edges);
              break;
            case "upsertNodes":
              upsertNodes((payload as UpsertNodesPayload).nodes);
              break;
            case "upsertEdges":
              upsertEdges((payload as UpsertEdgesPayload).edges);
              break;
            case "decay":
              decayNodes((payload as DecayPayload).nodeIds);
              decayEdges((payload as DecayPayload).edgeIds);
              break;
            case "heartbeat":
              // no-op, just confirms connection is alive
              break;
          }
        } catch {
          // malformed event — ignore
        }
      };

      // EventSource doesn't expose an onopen-equivalent for named events,
      // so mark connected as soon as the first event of any type arrives.
      // For named-event SSE, onopen fires when the HTTP connection is established.
      es.onopen = () => {
        retryDelay.current = 1000;
        onStatusChange("connected");
      };

      es.addEventListener("snapshot",    handleEvent("snapshot"));
      es.addEventListener("upsertNodes", handleEvent("upsertNodes"));
      es.addEventListener("upsertEdges", handleEvent("upsertEdges"));
      es.addEventListener("decay",       handleEvent("decay"));
      es.addEventListener("heartbeat",   handleEvent("heartbeat"));

      es.onerror = () => {
        if (cancelled) return;
        es.close();
        onStatusChange("disconnected");
        // exponential backoff capped at 30s
        retryRef.current = setTimeout(() => {
          retryDelay.current = Math.min(retryDelay.current * 2, 30_000);
          connect();
        }, retryDelay.current);
      };
    }

    connect();

    return () => {
      cancelled = true;
      esRef.current?.close();
      if (retryRef.current) clearTimeout(retryRef.current);
      onStatusChange("disconnected");
    };
  }, [chain, window]); // eslint-disable-line react-hooks/exhaustive-deps
}
