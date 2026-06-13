export default function Legend() {
  return (
    <div className="absolute top-4 right-6 bg-black/60 border border-white/10 rounded-xl p-3 backdrop-blur-sm pointer-events-none">
      <p className="text-gray-400 font-mono text-[10px] uppercase tracking-widest mb-2">Legend</p>

      {/* Node colors */}
      <div className="space-y-1.5 mb-3">
        <LegendDot color="#f59e0b" label="Whale (high volume)" />
        <LegendDot color="#22d3ee" label="Recently active" />
        <LegendDot color="#6366f1" label="Active" />
        <LegendDot color="#374151" label="Dormant" />
        <LegendDot color="#a855f7" label="AI highlighted" />
      </div>

      {/* Size */}
      <div className="mb-3 space-y-1">
        <p className="text-gray-600 text-[9px] font-mono uppercase">Node size</p>
        <p className="text-gray-400 text-[10px]">Larger = higher transfer volume</p>
      </div>

      {/* Edge colors */}
      <div className="space-y-1.5">
        <p className="text-gray-600 text-[9px] font-mono uppercase">Edge color</p>
        <LegendLine color="#f59e0b" label="High value (>70%)" />
        <LegendLine color="#22d3ee" label="Medium value" />
        <LegendLine color="#6366f1" label="Low value" />
      </div>
    </div>
  );
}

function LegendDot({ color, label }: { color: string; label: string }) {
  return (
    <div className="flex items-center gap-2">
      <div className="w-2.5 h-2.5 rounded-full flex-shrink-0" style={{ backgroundColor: color }} />
      <span className="text-gray-300 text-[10px] font-mono">{label}</span>
    </div>
  );
}

function LegendLine({ color, label }: { color: string; label: string }) {
  return (
    <div className="flex items-center gap-2">
      <div className="w-5 h-0.5 flex-shrink-0" style={{ backgroundColor: color }} />
      <span className="text-gray-300 text-[10px] font-mono">{label}</span>
    </div>
  );
}
