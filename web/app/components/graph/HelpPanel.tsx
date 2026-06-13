"use client";

import { useState } from "react";

const SECTIONS = [
  {
    title: "Navigate",
    items: [
      "🖱 Drag — rotate the graph",
      "🖱 Scroll — zoom in / out",
      "🖱 Right-drag — pan",
    ],
  },
  {
    title: "Graph",
    items: [
      "Click a node to inspect it",
      "Larger node = higher volume",
      "🟡 Amber = whale address",
      "🔵 Cyan = recently active",
      "Pulsing ring = just seen",
      "Arrow on edge = transfer direction",
      "Hover edge = see asset & value",
    ],
  },
  {
    title: "AI Copilot  ✦",
    items: [
      "Click ✦ bottom-right to open",
      '"Show me the biggest whales"',
      '"Highlight high-value transfers"',
      '"Focus on the most connected node"',
      '"Reset the view"',
    ],
  },
  {
    title: "Chain",
    items: [
      "BASE — real-time ERC-20 transfers on Base L2",
      "SOL — real-time SOL transfers on Solana",
      "Switching chain clears the graph",
    ],
  },
];

export default function HelpPanel() {
  const [open, setOpen] = useState(false);

  return (
    <>
      {/* Trigger button */}
      <button
        onClick={() => setOpen((v) => !v)}
        className="absolute bottom-24 right-6 w-7 h-7 rounded-full bg-white/5 border border-white/20 hover:bg-white/10 flex items-center justify-center transition-colors z-20"
        title="How to use"
      >
        <span className="text-gray-300 text-xs font-bold font-mono">?</span>
      </button>

      {/* Panel */}
      {open && (
        <div className="absolute bottom-28 right-6 w-64 bg-black/85 border border-white/15 rounded-2xl p-4 backdrop-blur-md shadow-2xl z-20 overflow-y-auto max-h-[70vh]">
          <div className="flex items-center justify-between mb-3">
            <span className="text-white font-mono text-xs font-bold tracking-wide">How to use</span>
            <button onClick={() => setOpen(false)} className="text-gray-500 hover:text-white text-xs">✕</button>
          </div>

          <div className="space-y-4">
            {SECTIONS.map((s) => (
              <div key={s.title}>
                <p className="text-gray-500 font-mono text-[9px] uppercase tracking-widest mb-1.5">{s.title}</p>
                <ul className="space-y-1">
                  {s.items.map((item) => (
                    <li key={item} className="text-gray-300 font-mono text-[10px] leading-relaxed">
                      {item}
                    </li>
                  ))}
                </ul>
              </div>
            ))}
          </div>

          <p className="mt-4 text-gray-600 font-mono text-[9px] text-center">
            data streams live from mainnet
          </p>
        </div>
      )}
    </>
  );
}
