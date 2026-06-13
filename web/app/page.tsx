"use client";

import dynamic from "next/dynamic";
import { useEffect, useState } from "react";
import { useGraphStore } from "@/app/store/graph-store";
import { MOCK_NODES, MOCK_EDGES } from "@/app/lib/mock-data";
import { useSseStream, ConnectionStatus } from "@/app/lib/use-sse-stream";
import NodePanel from "@/app/components/graph/NodePanel";
import Hud from "@/app/components/graph/Hud";
import CopilotPanel from "@/app/components/copilot/CopilotPanel";
import Legend from "@/app/components/graph/Legend";
import ActivityFeed from "@/app/components/graph/ActivityFeed";
import HelpPanel from "@/app/components/graph/HelpPanel";

const GraphCanvas = dynamic(() => import("@/app/components/graph/GraphCanvas"), {
  ssr: false,
  loading: () => (
    <div className="w-full h-full flex items-center justify-center text-cyan-400 font-mono text-sm animate-pulse">
      Loading graph…
    </div>
  ),
});

const USE_MOCK = process.env.NEXT_PUBLIC_USE_MOCK === "true";

export default function Home() {
  const { upsertNodes, upsertEdges, chain } = useGraphStore();
  const [status, setStatus] = useState<ConnectionStatus>("connecting");

  useEffect(() => {
    if (USE_MOCK) {
      upsertNodes(MOCK_NODES);
      upsertEdges(MOCK_EDGES);
      setStatus("connected");
    }
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => { setStatus("connecting"); }, [chain]);

  useSseStream(chain, "1h", setStatus, USE_MOCK);

  return (
    <main className="relative w-screen h-screen overflow-hidden bg-[#050914]">
      <GraphCanvas />
      <Hud status={status} />
      <NodePanel />
      <ActivityFeed />
      <Legend />
      <HelpPanel />
      <CopilotPanel />
    </main>
  );
}
