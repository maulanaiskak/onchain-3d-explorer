"use client";

import { useGraphStore } from "@/app/store/graph-store";

export default function NodePanel() {
  const { nodes, selectedId, setSelected } = useGraphStore();
  const node = nodes.find((n) => n.id === selectedId);
  if (!node) return null;

  return (
    <div className="absolute bottom-6 left-6 w-72 bg-black/70 border border-cyan-500/30 rounded-xl p-4 backdrop-blur-sm text-sm font-mono">
      <div className="flex items-center justify-between mb-3">
        <span className="text-cyan-400 font-bold text-xs uppercase tracking-widest">Node Details</span>
        <button onClick={() => setSelected(null)} className="text-gray-500 hover:text-white text-xs">✕</button>
      </div>

      <div className="space-y-2 text-gray-300">
        <Row label="Address" value={node.id} mono />
        <Row label="Chain" value={node.chain} />
        {node.label && <Row label="Label" value={node.label} />}
        <Row label="Weight" value={(node.weight * 100).toFixed(0) + "%"} />
        <Row
          label="Recency"
          value={(node.recency * 100).toFixed(0) + "%"}
          valueClass={node.recency > 0.7 ? "text-cyan-400" : node.recency > 0.4 ? "text-indigo-400" : "text-gray-500"}
        />
        {node.isWhale && (
          <div className="mt-2 px-2 py-1 bg-purple-900/40 border border-purple-500/40 rounded text-purple-300 text-xs">
            🐋 Whale address
          </div>
        )}
      </div>
    </div>
  );
}

function Row({ label, value, mono, valueClass }: {
  label: string; value: string; mono?: boolean; valueClass?: string;
}) {
  return (
    <div className="flex justify-between gap-2">
      <span className="text-gray-500 text-xs">{label}</span>
      <span className={`text-xs truncate max-w-[160px] ${mono ? "font-mono text-cyan-300" : ""} ${valueClass ?? "text-white"}`}>
        {value}
      </span>
    </div>
  );
}
