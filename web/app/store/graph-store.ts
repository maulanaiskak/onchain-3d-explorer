"use client";

import { create } from "zustand";
import { NodeDTO, EdgeDTO } from "@/app/lib/mock-data";

// Hard caps — keep recent data visible without freezing the browser
const MAX_NODES = 150;
const MAX_EDGES = 300;

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

  setChain: (chain: string) => void;
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
  chain: "evm:base",
  overlay: defaultOverlay,

  setChain: (chain) => set({ chain, nodes: [], edges: [] }),

  setSelected: (id) => set({ selectedId: id }),

  upsertNodes: (incoming) =>
    set((s) => {
      const map = new Map(s.nodes.map((n) => [n.id, n]));
      incoming.forEach((n) => map.set(n.id, n));
      let all = Array.from(map.values());
      // Keep highest-weight nodes when over cap
      if (all.length > MAX_NODES) {
        all.sort((a, b) => b.weight - a.weight);
        all = all.slice(0, MAX_NODES);
      }
      return { nodes: all };
    }),

  upsertEdges: (incoming) =>
    set((s) => {
      const map = new Map(s.edges.map((e) => [e.id, e]));
      incoming.forEach((e) => map.set(e.id, e));
      const all = Array.from(map.values());
      // Sliding window: drop oldest entries (inserted first) when over cap
      return { edges: all.length > MAX_EDGES ? all.slice(all.length - MAX_EDGES) : all };
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

  replaceGraph: (nodes, edges) => set({
    nodes: nodes.slice(0, MAX_NODES),
    edges: edges.slice(0, MAX_EDGES),
  }),

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
