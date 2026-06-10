"use client";

import { useRef, useState, useEffect, KeyboardEvent } from "react";
import { useCopilot } from "@/app/lib/use-copilot";
import { useGraphStore } from "@/app/store/graph-store";
import { SceneCommand, CopilotMessage } from "@/app/lib/copilot-types";

const SUGGESTIONS = [
  "Show me the biggest whales",
  "Highlight high-value transfers",
  "Focus on the most connected node",
  "Reset the view",
];

export default function CopilotPanel() {
  const [open, setOpen] = useState(false);
  const [input, setInput] = useState("");
  const bottomRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  const {
    highlightNodes, focusNode, annotateNode,
    filterByAsset, resetOverlay, setSelected,
  } = useGraphStore();

  const handleCommand = (cmd: SceneCommand) => {
    switch (cmd.type) {
      case "highlight":
        if (cmd.nodeIds) highlightNodes(cmd.nodeIds);
        break;
      case "flyTo":
      case "focus":
        if (cmd.target) { focusNode(cmd.target); setSelected(cmd.target); }
        if (cmd.nodeId) { focusNode(cmd.nodeId); setSelected(cmd.nodeId); }
        break;
      case "annotate":
        if (cmd.nodeId && cmd.label) annotateNode(cmd.nodeId, cmd.label);
        break;
      case "filter":
        filterByAsset(cmd.asset ?? null);
        break;
      case "resetView":
        resetOverlay();
        break;
    }
  };

  const { messages, loading, send, clear } = useCopilot("solana", "1h");

  const submit = () => {
    const text = input.trim();
    if (!text || loading) return;
    setInput("");
    send(text, handleCommand);
  };

  const onKey = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === "Enter") submit();
    if (e.key === "Escape") setOpen(false);
  };

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  useEffect(() => {
    if (open) inputRef.current?.focus();
  }, [open]);

  return (
    <>
      {/* Toggle button */}
      <button
        onClick={() => setOpen((v) => !v)}
        className="absolute bottom-6 right-6 w-12 h-12 rounded-full bg-indigo-600 hover:bg-indigo-500 flex items-center justify-center shadow-lg shadow-indigo-900/60 transition-all z-10"
        title="AI Copilot"
      >
        <span className="text-white text-xl">{open ? "✕" : "✦"}</span>
      </button>

      {/* Panel */}
      {open && (
        <div className="absolute bottom-20 right-6 w-80 h-[480px] bg-black/80 border border-indigo-500/30 rounded-2xl flex flex-col backdrop-blur-md shadow-2xl shadow-indigo-900/40 z-10">
          {/* Header */}
          <div className="flex items-center justify-between px-4 py-3 border-b border-indigo-500/20">
            <div className="flex items-center gap-2">
              <span className="text-indigo-400 text-sm">✦</span>
              <span className="text-white font-mono text-sm font-bold tracking-wide">AI Copilot</span>
            </div>
            <button onClick={clear} className="text-gray-600 hover:text-gray-300 text-xs font-mono">
              clear
            </button>
          </div>

          {/* Messages */}
          <div className="flex-1 overflow-y-auto px-4 py-3 space-y-3 scrollbar-thin">
            {messages.length === 0 && (
              <div className="space-y-2 mt-2">
                <p className="text-gray-500 text-xs font-mono">Try asking…</p>
                {SUGGESTIONS.map((s) => (
                  <button
                    key={s}
                    onClick={() => { setInput(s); inputRef.current?.focus(); }}
                    className="w-full text-left px-3 py-2 rounded-lg bg-indigo-950/60 border border-indigo-500/20 text-indigo-300 text-xs hover:bg-indigo-900/60 transition-colors font-mono"
                  >
                    {s}
                  </button>
                ))}
              </div>
            )}

            {messages.map((msg, i) => (
              <MessageBubble key={i} msg={msg} />
            ))}

            {loading && (
              <div className="flex gap-1 px-3 py-2">
                <span className="w-1.5 h-1.5 rounded-full bg-indigo-400 animate-bounce [animation-delay:0ms]" />
                <span className="w-1.5 h-1.5 rounded-full bg-indigo-400 animate-bounce [animation-delay:150ms]" />
                <span className="w-1.5 h-1.5 rounded-full bg-indigo-400 animate-bounce [animation-delay:300ms]" />
              </div>
            )}
            <div ref={bottomRef} />
          </div>

          {/* Input */}
          <div className="px-3 pb-3">
            <div className="flex gap-2 items-center bg-indigo-950/50 border border-indigo-500/30 rounded-xl px-3 py-2">
              <input
                ref={inputRef}
                value={input}
                onChange={(e) => setInput(e.target.value)}
                onKeyDown={onKey}
                placeholder="Ask about the graph…"
                className="flex-1 bg-transparent text-white text-xs font-mono outline-none placeholder-gray-600"
                disabled={loading}
              />
              <button
                onClick={submit}
                disabled={!input.trim() || loading}
                className="text-indigo-400 disabled:text-gray-700 hover:text-indigo-300 transition-colors text-sm"
              >
                ↵
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}

function MessageBubble({ msg }: { msg: CopilotMessage }) {
  const isUser = msg.role === "user";

  return (
    <div className={`flex ${isUser ? "justify-end" : "justify-start"}`}>
      <div
        className={`max-w-[90%] px-3 py-2 rounded-xl text-xs font-mono leading-relaxed ${
          isUser
            ? "bg-indigo-700/60 text-white"
            : msg.error
            ? "bg-red-900/40 text-red-300 border border-red-500/30"
            : "bg-gray-800/60 text-gray-200 border border-gray-700/30"
        }`}
      >
        <p>{msg.text}</p>

        {msg.commands && msg.commands.length > 0 && (
          <div className="mt-2 space-y-1">
            {msg.commands.map((cmd, i) => (
              <span
                key={i}
                className="inline-block mr-1 px-1.5 py-0.5 bg-indigo-900/60 text-indigo-300 rounded text-[10px] border border-indigo-700/40"
              >
                {cmd.type}
              </span>
            ))}
          </div>
        )}

        {msg.grounded === false && (
          <p className="mt-1 text-yellow-600 text-[10px]">⚠ ungrounded response</p>
        )}
      </div>
    </div>
  );
}
