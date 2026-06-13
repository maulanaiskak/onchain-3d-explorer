"use client";

import { useGraphStore } from "@/app/store/graph-store";

function short(addr: string): string {
  if (addr.length <= 10) return addr;
  return addr.slice(0, 4) + "…" + addr.slice(-3);
}

export default function ActivityFeed() {
  const edges    = useGraphStore((s) => s.edges);
  const selectedId = useGraphStore((s) => s.selectedId);
  if (selectedId) return null;

  const recent = edges
    .slice()
    .sort((a, b) => new Date(b.blockTime).getTime() - new Date(a.blockTime).getTime())
    .slice(0, 3);

  if (recent.length === 0) return null;

  return (
    <div className="absolute bottom-6 left-6 pointer-events-none" style={{ width: 180 }}>
      <div className="bg-black/50 border border-white/10 rounded-lg px-2.5 py-2 backdrop-blur-sm overflow-hidden">
        <div className="flex items-center gap-1.5 mb-1.5">
          <span className="w-1.5 h-1.5 rounded-full bg-green-400 animate-pulse flex-shrink-0" />
          <span className="text-gray-500 font-mono text-[9px] uppercase tracking-wider">Live</span>
          <span className="text-gray-600 font-mono text-[9px] ml-auto">{edges.length}</span>
        </div>
        <div className="space-y-1 overflow-hidden">
          {recent.map((e, i) => (
            <div
              key={e.id}
              className="flex items-center gap-1 font-mono text-[9px] overflow-hidden"
              style={{ opacity: i === 0 ? 1 : 0.35 }}
            >
              <span className="text-cyan-400 shrink-0">{short(e.from)}</span>
              <span className="text-gray-600 shrink-0">→</span>
              <span className="text-indigo-300 shrink-0">{short(e.to)}</span>
              <span
                className={`ml-auto shrink-0 ${e.valueNorm > 0.7 ? "text-amber-400" : "text-gray-500"}`}
              >
                {(e.valueNorm * 100).toFixed(0)}%
              </span>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
