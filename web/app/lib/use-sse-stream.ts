"use client";

import { useEffect, useRef } from "react";
import { useGraphStore } from "@/app/store/graph-store";
import { NodeDTO, EdgeDTO } from "./mock-data";

type DeltaType = "snapshot" | "upsertNodes" | "upsertEdges" | "decay" | "heartbeat";

interface SnapshotPayload   { type: "snapshot";    nodes: NodeDTO[]; edges: EdgeDTO[] }
interface UpsertNodesPayload { type: "upsertNodes"; nodes: NodeDTO[] }
interface UpsertEdgesPayload { type: "upsertEdges"; edges: EdgeDTO[] }
interface DecayPayload       { type: "decay";       nodeIds: string[]; edgeIds: string[] }

type Delta =
  | SnapshotPayload
  | UpsertNodesPayload
  | UpsertEdgesPayload
  | DecayPayload
  | { type: "heartbeat" };

export type ConnectionStatus = "connecting" | "connected" | "disconnected" | "error";

// Flush batched node/edge upserts at most 4× per second to avoid flooding React
const FLUSH_MS = 250;

export function useSseStream(
  chain: string,
  window: string,
  onStatusChange: (s: ConnectionStatus) => void,
  disabled = false
) {
  const { upsertNodes, upsertEdges, decayNodes, decayEdges, replaceGraph } = useGraphStore();
  const esRef       = useRef<EventSource | null>(null);
  const retryRef    = useRef<ReturnType<typeof setTimeout> | null>(null);
  const retryDelay  = useRef(1000);
  const nodesBuf    = useRef<NodeDTO[]>([]);
  const edgesBuf    = useRef<EdgeDTO[]>([]);
  const flushRef    = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    if (disabled) return;
    let cancelled = false;

    function flush() {
      if (nodesBuf.current.length) { upsertNodes(nodesBuf.current); nodesBuf.current = []; }
      if (edgesBuf.current.length) { upsertEdges(edgesBuf.current); edgesBuf.current = []; }
      flushRef.current = null;
    }

    function scheduleFlush() {
      if (!flushRef.current) flushRef.current = setTimeout(flush, FLUSH_MS);
    }

    function connect() {
      if (cancelled) return;
      onStatusChange("connecting");

      const base = process.env.NEXT_PUBLIC_BACKEND_URL ?? "http://localhost:8080";
      const url  = `${base}/api/stream?chain=${encodeURIComponent(chain)}&window=${encodeURIComponent(window)}`;
      const es   = new EventSource(url);
      esRef.current = es;

      const handleEvent = (type: DeltaType) => (e: MessageEvent) => {
        try {
          const payload = JSON.parse(e.data) as Delta;
          switch (payload.type ?? type) {
            case "snapshot":
              // Flush any pending batches first, then replace
              flush();
              replaceGraph((payload as SnapshotPayload).nodes, (payload as SnapshotPayload).edges);
              break;
            case "upsertNodes":
              nodesBuf.current.push(...(payload as UpsertNodesPayload).nodes);
              scheduleFlush();
              break;
            case "upsertEdges":
              edgesBuf.current.push(...(payload as UpsertEdgesPayload).edges);
              scheduleFlush();
              break;
            case "decay":
              flush();
              decayNodes((payload as DecayPayload).nodeIds);
              decayEdges((payload as DecayPayload).edgeIds);
              break;
          }
        } catch { /* malformed — ignore */ }
      };

      es.onopen = () => { retryDelay.current = 1000; onStatusChange("connected"); };

      es.addEventListener("snapshot",    handleEvent("snapshot"));
      es.addEventListener("upsertNodes", handleEvent("upsertNodes"));
      es.addEventListener("upsertEdges", handleEvent("upsertEdges"));
      es.addEventListener("decay",       handleEvent("decay"));
      es.addEventListener("heartbeat",   () => {});

      es.onerror = () => {
        if (cancelled) return;
        es.close();
        onStatusChange("disconnected");
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
      if (retryRef.current)  clearTimeout(retryRef.current);
      if (flushRef.current)  clearTimeout(flushRef.current);
      onStatusChange("disconnected");
    };
  }, [chain, window]); // eslint-disable-line react-hooks/exhaustive-deps
}
