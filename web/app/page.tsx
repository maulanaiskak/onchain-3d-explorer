"use client";

import dynamic from "next/dynamic";
import { useEffect } from "react";
import { useGraphStore } from "@/app/store/graph-store";
import { MOCK_NODES, MOCK_EDGES } from "@/app/lib/mock-data";
import NodePanel from "@/app/components/graph/NodePanel";
import Hud from "@/app/components/graph/Hud";

// R3F Canvas must be client-only (no SSR)
const GraphCanvas = dynamic(() => import("@/app/components/graph/GraphCanvas"), {
  ssr: false,
  loading: () => (
    <div className="w-full h-full flex items-center justify-center text-cyan-400 font-mono text-sm animate-pulse">
      Loading graph…
    </div>
  ),
});

export default function Home() {
  const { upsertNodes, upsertEdges } = useGraphStore();

  useEffect(() => {
    upsertNodes(MOCK_NODES);
    upsertEdges(MOCK_EDGES);
  }, [upsertNodes, upsertEdges]);

  return (
    <main className="relative w-screen h-screen overflow-hidden bg-[#050914]">
      <GraphCanvas />
      <Hud />
      <NodePanel />
    </main>
  );
}
