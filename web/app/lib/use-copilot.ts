"use client";

import { useState, useCallback } from "react";
import { CopilotMessage, CopilotResponse, SceneCommand } from "./copilot-types";

export function useCopilot(chain: string, window: string) {
  const [messages, setMessages] = useState<CopilotMessage[]>([]);
  const [loading, setLoading] = useState(false);

  const send = useCallback(
    async (intent: string, onCommand: (cmd: SceneCommand) => void) => {
      const userMsg: CopilotMessage = { role: "user", text: intent };
      setMessages((prev) => [...prev, userMsg]);
      setLoading(true);

      try {
        const res = await fetch("/api/copilot", {
          method: "POST",
          headers: { "Content-Type": "application/json", Accept: "text/event-stream" },
          body: JSON.stringify({ intent, chain, window }),
        });

        if (!res.ok || !res.body) throw new Error(`HTTP ${res.status}`);

        const reader = res.body.getReader();
        const decoder = new TextDecoder();
        let buffer = "";

        while (true) {
          const { done, value } = await reader.read();
          if (done) break;
          buffer += decoder.decode(value, { stream: true });

          // Parse SSE frames
          const frames = buffer.split("\n\n");
          buffer = frames.pop() ?? "";

          for (const frame of frames) {
            const eventLine = frame.split("\n").find((l) => l.startsWith("event:"));
            const dataLine  = frame.split("\n").find((l) => l.startsWith("data:"));
            const eventType = eventLine?.slice(6).trim();
            const data      = dataLine?.slice(5).trim();
            if (!data) continue;

            if (eventType === "commands") {
              const resp: CopilotResponse = JSON.parse(data);
              const assistantMsg: CopilotMessage = {
                role: "assistant",
                text: resp.narrative,
                commands: resp.commands,
                grounded: resp.grounded,
              };
              setMessages((prev) => [...prev, assistantMsg]);
              resp.commands.forEach(onCommand);
            } else if (eventType === "error") {
              const err = JSON.parse(data);
              setMessages((prev) => [
                ...prev,
                { role: "assistant", text: err.message ?? "Something went wrong.", error: true },
              ]);
            }
          }
        }
      } catch (e: unknown) {
        const msg = e instanceof Error ? e.message : "Network error";
        setMessages((prev) => [
          ...prev,
          { role: "assistant", text: msg, error: true },
        ]);
      } finally {
        setLoading(false);
      }
    },
    [chain, window]
  );

  const clear = useCallback(() => setMessages([]), []);

  return { messages, loading, send, clear };
}
