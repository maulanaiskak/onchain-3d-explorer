"use client";

import { create } from "zustand";
import { NodeDTO, EdgeDTO } from "@/app/lib/mock-data";

export interface CopilotOverlay {
  highlightedIds: Set<string>;
  focusedId: string | null;
  annotations: Map<string, string>;
  assetFilter: string | null;
}

interface GraphState {
  nodes: NodeDTO[];
  edges: EdgeDTO[];
  selectedId: string | null;
  chain: string;
  overlay: CopilotOverlay;

  setSelected: (id: string | null) => void;
  upsertNodes: (nodes: NodeDTO[]) => void;
  upsertEdges: (edges: EdgeDTO[]) => void;
  decayNodes: (ids: string[]) => void;
  decayEdges: (ids: string[]) => void;
  replaceGraph: (nodes: NodeDTO[], edges: EdgeDTO[]) => void;

  // Copilot scene commands
  highlightNodes: (ids: string[]) => void;
  focusNode: (id: string) => void;
  annotateNode: (id: string, label: string) => void;
  filterByAsset: (asset: string | null) => void;
  resetOverlay: () => void;
}

const defaultOverlay: CopilotOverlay = {
  highlightedIds: new Set(),
  focusedId: null,
  annotations: new Map(),
  assetFilter: null,
};

export const useGraphStore = create<GraphState>((set) => ({
  nodes: [],
  edges: [],
  selectedId: null,
  chain: "solana",
  overlay: defaultOverlay,

  setSelected: (id) => set({ selectedId: id }),

  upsertNodes: (incoming) =>
    set((s) => {
      const map = new Map(s.nodes.map((n) => [n.id, n]));
      incoming.forEach((n) => map.set(n.id, n));
      return { nodes: Array.from(map.values()) };
    }),

  upsertEdges: (incoming) =>
    set((s) => {
      const map = new Map(s.edges.map((e) => [e.id, e]));
      incoming.forEach((e) => map.set(e.id, e));
      return { edges: Array.from(map.values()) };
    }),

  decayNodes: (ids) =>
    set((s) => {
      const gone = new Set(ids);
      return { nodes: s.nodes.filter((n) => !gone.has(n.id)) };
    }),

  decayEdges: (ids) =>
    set((s) => {
      const gone = new Set(ids);
      return { edges: s.edges.filter((e) => !gone.has(e.id)) };
    }),

  replaceGraph: (nodes, edges) => set({ nodes, edges }),

  highlightNodes: (ids) =>
    set((s) => ({
      overlay: { ...s.overlay, highlightedIds: new Set(ids) },
    })),

  focusNode: (id) =>
    set((s) => ({
      overlay: { ...s.overlay, focusedId: id },
      selectedId: id,
    })),

  annotateNode: (id, label) =>
    set((s) => {
      const annotations = new Map(s.overlay.annotations);
      annotations.set(id, label);
      return { overlay: { ...s.overlay, annotations } };
    }),

  filterByAsset: (asset) =>
    set((s) => ({ overlay: { ...s.overlay, assetFilter: asset } })),

  resetOverlay: () => set({ overlay: defaultOverlay, selectedId: null }),
}));
