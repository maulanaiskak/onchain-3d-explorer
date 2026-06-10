"use client";

import { useGraphStore } from "@/app/store/graph-store";

export default function Hud() {
  const { nodes, edges } = useGraphStore();
  const flagged = nodes.filter((n) => n.flagged).length;

  return (
    <div className="absolute top-4 left-4 flex flex-col gap-2 pointer-events-none">
      {/* Title */}
      <div className="flex items-center gap-2">
        <div className="w-2 h-2 rounded-full bg-cyan-400 animate-pulse" />
        <span className="text-cyan-400 font-mono text-sm font-bold tracking-wider">
          ON-CHAIN 3D EXPLORER
        </span>
      </div>

      {/* Stats */}
      <div className="flex gap-3 text-xs font-mono text-gray-400">
        <Stat label="NODES" value={nodes.length} />
        <Stat label="EDGES" value={edges.length} />
        {flagged > 0 && <Stat label="FLAGGED" value={flagged} className="text-red-400" />}
      </div>
    </div>
  );
}

function Stat({ label, value, className }: { label: string; value: number; className?: string }) {
  return (
    <span className={className ?? "text-gray-400"}>
      {label}: <span className="text-white">{value}</span>
    </span>
  );
}
