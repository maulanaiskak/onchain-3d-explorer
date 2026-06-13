"use client";

import { useState } from "react";
import { useGraphStore } from "@/app/store/graph-store";
import { EdgeDTO } from "@/app/lib/mock-data";

function explorerUrl(chain: string, address: string): string | null {
  if (chain === "solana") return `https://solscan.io/account/${address}`;
  if (chain === "evm:base") return `https://basescan.org/address/${address}`;
  return null;
}

function timeAgo(iso: string): string {
  const s = (Date.now() - new Date(iso).getTime()) / 1000;
  if (s < 10) return "just now";
  if (s < 60) return `${Math.floor(s)}s ago`;
  if (s < 3600) return `${Math.floor(s / 60)}m ago`;
  return `${Math.floor(s / 3600)}h ago`;
}

function shortAddr(id: string) {
  return id.length > 12 ? id.slice(0, 6) + "…" + id.slice(-4) : id;
}

function shortAsset(asset: string) {
  // ERC20:0xcontract → ERC20
  if (asset.startsWith("ERC20:")) return "ERC20";
  return asset;
}

function totalVolume(edgeList: EdgeDTO[]) {
  return edgeList.reduce((s, e) => s + e.valueNorm, 0);
}

export default function NodePanel() {
  const { nodes, edges, selectedId, setSelected } = useGraphStore();
  const [copied, setCopied] = useState(false);

  const node = nodes.find((n) => n.id === selectedId);
  if (!node) return null;

  const outEdges = edges.filter((e) => e.from === node.id);
  const inEdges  = edges.filter((e) => e.to   === node.id);
  const allEdges = [...outEdges, ...inEdges].sort(
    (a, b) => new Date(b.blockTime).getTime() - new Date(a.blockTime).getTime()
  );

  const sentTo       = new Set(outEdges.map((e) => e.to));
  const receivedFrom = new Set(inEdges.map((e) => e.from));
  const lastSeen     = allEdges[0]?.blockTime ?? null;
  const outVol       = totalVolume(outEdges);
  const inVol        = totalVolume(inEdges);

  // Top counterparties
  const counterMap = new Map<string, { count: number; vol: number; dir: "out" | "in" | "both" }>();
  outEdges.forEach((e) => {
    const x = counterMap.get(e.to) ?? { count: 0, vol: 0, dir: "out" as const };
    counterMap.set(e.to, { count: x.count + 1, vol: x.vol + e.valueNorm, dir: x.dir === "in" ? "both" : "out" });
  });
  inEdges.forEach((e) => {
    const x = counterMap.get(e.from) ?? { count: 0, vol: 0, dir: "in" as const };
    counterMap.set(e.from, { count: x.count + 1, vol: x.vol + e.valueNorm, dir: x.dir === "out" ? "both" : "in" });
  });
  const topCounterparties = Array.from(counterMap.entries())
    .sort((a, b) => b[1].vol - a[1].vol)
    .slice(0, 4);

  function handleCopy() {
    navigator.clipboard.writeText(node!.id);
    setCopied(true);
    setTimeout(() => setCopied(false), 1500);
  }

  const explorerLink = explorerUrl(node.chain, node.id);
  const activityScore = Math.round((outVol + inVol) * 100);

  return (
    <div className="absolute bottom-6 left-6 w-72 bg-black/80 border border-cyan-500/30 rounded-xl p-4 backdrop-blur-md text-xs font-mono shadow-xl shadow-black/40">
      {/* Header */}
      <div className="flex items-center justify-between mb-3">
        <div className="flex items-center gap-2">
          {node.isWhale && <span title="Whale">🐋</span>}
          <span className="text-cyan-400 font-bold uppercase tracking-widest text-[10px]">
            {node.label ?? "Address"}
          </span>
        </div>
        <button onClick={() => setSelected(null)} className="text-gray-600 hover:text-white text-xs">✕</button>
      </div>

      {/* Address */}
      <div className="flex items-center gap-2 mb-3 px-2 py-1.5 bg-white/5 rounded-lg">
        <span className="text-cyan-300 flex-1 truncate">{shortAddr(node.id)}</span>
        <button onClick={handleCopy} className="text-gray-500 hover:text-cyan-300 transition-colors shrink-0" title="Copy">
          {copied ? <span className="text-green-400 text-[10px]">✓ Copied</span> : "⎘"}
        </button>
      </div>

      {/* Stats grid */}
      <div className="grid grid-cols-2 gap-1.5 mb-3">
        <StatBox label="Activity score" value={activityScore} unit="" color="text-cyan-300" />
        <StatBox label="Total txs" value={allEdges.length} unit="" color="text-white" />
        <StatBox label="Sent out" value={(outVol * 100).toFixed(1)} unit="%" color="text-red-400" />
        <StatBox label="Received" value={(inVol * 100).toFixed(1)} unit="%" color="text-green-400" />
      </div>

      {/* Connections */}
      <div className="mb-3 space-y-0.5">
        <p className="text-gray-600 text-[9px] uppercase tracking-widest mb-1">Connections</p>
        <Row label="Sent to" value={`${sentTo.size} unique addrs`} />
        <Row label="Received from" value={`${receivedFrom.size} unique addrs`} />
        {lastSeen && <Row label="Last seen" value={timeAgo(lastSeen)} valueClass="text-cyan-300" />}
      </div>

      {/* Top counterparties */}
      {topCounterparties.length > 0 && (
        <div className="mb-3">
          <p className="text-gray-600 text-[9px] uppercase tracking-widest mb-1">Top counterparties</p>
          <div className="space-y-1">
            {topCounterparties.map(([addr, info]) => (
              <div key={addr} className="flex items-center gap-1.5 text-[10px]">
                <span className={
                  info.dir === "out" ? "text-red-400" :
                  info.dir === "in"  ? "text-green-400" : "text-yellow-400"
                }>
                  {info.dir === "out" ? "↑" : info.dir === "in" ? "↓" : "⇅"}
                </span>
                <span className="text-gray-300 flex-1 truncate">{shortAddr(addr)}</span>
                <span className="text-gray-500 shrink-0">{info.count}x</span>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Recent transfers */}
      {allEdges.length > 0 && (
        <div className="mb-3">
          <p className="text-gray-600 text-[9px] uppercase tracking-widest mb-1">Recent transfers</p>
          <div className="space-y-1">
            {allEdges.slice(0, 3).map((e) => {
              const isSend = e.from === node.id;
              return (
                <div key={e.id} className="flex items-center gap-1 text-[10px]">
                  <span className={isSend ? "text-red-400" : "text-green-400"}>{isSend ? "↑" : "↓"}</span>
                  <span className="text-gray-400 truncate flex-1">{shortAddr(isSend ? e.to : e.from)}</span>
                  <span className={`shrink-0 ${e.valueNorm > 0.7 ? "text-amber-400" : "text-gray-500"}`}>
                    {(e.valueNorm * 100).toFixed(1)}%
                  </span>
                  <span className="text-gray-600 shrink-0 ml-1">{shortAsset(e.asset)}</span>
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* Footer */}
      <div className="flex items-center justify-between pt-2 border-t border-white/10">
        <span className="text-gray-600 text-[9px]">{node.chain}</span>
        {explorerLink && (
          <a href={explorerLink} target="_blank" rel="noopener noreferrer"
            className="text-indigo-400 hover:text-indigo-300 text-[10px] transition-colors">
            ↗ Explorer
          </a>
        )}
      </div>
    </div>
  );
}

function StatBox({ label, value, unit, color }: { label: string; value: string | number; unit: string; color: string }) {
  return (
    <div className="bg-white/5 rounded-lg px-2 py-1.5 text-center">
      <p className={`text-sm font-bold ${color}`}>{value}{unit}</p>
      <p className="text-gray-600 text-[9px] mt-0.5">{label}</p>
    </div>
  );
}

function Row({ label, value, valueClass }: { label: string; value: string; valueClass?: string }) {
  return (
    <div className="flex justify-between gap-2">
      <span className="text-gray-500">{label}</span>
      <span className={valueClass ?? "text-white"}>{value}</span>
    </div>
  );
}
