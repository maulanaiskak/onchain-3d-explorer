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

const CHAINS = [
  { id: "evm:base", label: "BASE" },
  { id: "solana",   label: "SOL"  },
];

export default function Hud({ status }: Props) {
  const { nodes, edges, chain, setChain } = useGraphStore();
  const whales = nodes.filter((n) => n.isWhale).length;

  return (
    <div className="absolute top-4 left-4 flex flex-col gap-2">
      <div className="flex items-center gap-2 pointer-events-none">
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

      <div className="flex gap-1.5">
        {CHAINS.map((c) => (
          <button
            key={c.id}
            onClick={() => setChain(c.id)}
            className={`text-[11px] font-mono font-bold px-3 py-1 rounded-md border transition-all ${
              chain === c.id
                ? "bg-cyan-500/30 border-cyan-400 text-cyan-200 shadow-sm shadow-cyan-500/30"
                : "bg-gray-900/80 border-gray-600 text-gray-300 hover:text-white hover:border-gray-400"
            }`}
          >
            {c.label}
          </button>
        ))}
      </div>

      <div className="flex gap-3 text-xs font-mono text-gray-400 pointer-events-none">
        <Stat label="NODES" value={nodes.length} />
        <Stat label="EDGES" value={edges.length} />
        {whales > 0 && <Stat label="WHALES" value={whales} className="text-purple-400" />}
      </div>

      <div className="flex gap-3 text-xs font-mono text-gray-400 pointer-events-none">
        <span className="text-gray-500">
          TXNS: <span className="text-white">{edges.length}</span>
          {" · "}VOL: <span className="text-amber-400">{(edges.reduce((s, e) => s + e.valueNorm, 0) * 100).toFixed(0)} units</span>
        </span>
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
