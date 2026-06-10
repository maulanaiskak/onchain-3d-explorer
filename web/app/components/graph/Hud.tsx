"use client";

import { useGraphStore } from "@/app/store/graph-store";
import { ConnectionStatus } from "@/app/lib/use-sse-stream";

interface Props {
  status: ConnectionStatus;
}

const STATUS_COLOR: Record<ConnectionStatus, string> = {
  connecting:   "bg-yellow-400 animate-pulse",
  connected:    "bg-green-400",
  disconnected: "bg-gray-500",
  error:        "bg-red-500",
};

const STATUS_LABEL: Record<ConnectionStatus, string> = {
  connecting:   "CONNECTING",
  connected:    "LIVE",
  disconnected: "RECONNECTING",
  error:        "ERROR",
};

export default function Hud({ status }: Props) {
  const { nodes, edges } = useGraphStore();
  const whales = nodes.filter((n) => n.isWhale).length;

  return (
    <div className="absolute top-4 left-4 flex flex-col gap-2 pointer-events-none">
      <div className="flex items-center gap-2">
        <div className={`w-2 h-2 rounded-full ${STATUS_COLOR[status]}`} />
        <span className="text-cyan-400 font-mono text-sm font-bold tracking-wider">
          ON-CHAIN 3D EXPLORER
        </span>
        <span className={`text-xs font-mono px-1.5 py-0.5 rounded ${
          status === "connected" ? "text-green-400" : "text-yellow-400"
        }`}>
          {STATUS_LABEL[status]}
        </span>
      </div>

      <div className="flex gap-3 text-xs font-mono text-gray-400">
        <Stat label="NODES" value={nodes.length} />
        <Stat label="EDGES" value={edges.length} />
        {whales > 0 && <Stat label="WHALES" value={whales} className="text-purple-400" />}
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
