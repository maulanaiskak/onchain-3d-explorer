"use client";

import { create } from "zustand";
import { NodeDTO, EdgeDTO } from "@/app/lib/mock-data";

interface GraphState {
  nodes: NodeDTO[];
  edges: EdgeDTO[];
  selectedId: string | null;
  chain: string;
  setSelected: (id: string | null) => void;
  upsertNodes: (nodes: NodeDTO[]) => void;
  upsertEdges: (edges: EdgeDTO[]) => void;
  decayNodes: (ids: string[]) => void;
  decayEdges: (ids: string[]) => void;
  replaceGraph: (nodes: NodeDTO[], edges: EdgeDTO[]) => void;
}

export const useGraphStore = create<GraphState>((set) => ({
  nodes: [],
  edges: [],
  selectedId: null,
  chain: "solana",

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
      const set_ = new Set(ids);
      return { nodes: s.nodes.filter((n) => !set_.has(n.id)) };
    }),

  decayEdges: (ids) =>
    set((s) => {
      const set_ = new Set(ids);
      return { edges: s.edges.filter((e) => !set_.has(e.id)) };
    }),

  replaceGraph: (nodes, edges) => set({ nodes, edges }),
}));
