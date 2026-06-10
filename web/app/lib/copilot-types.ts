export type SceneCommandType =
  | "filter"
  | "highlight"
  | "flyTo"
  | "focus"
  | "expandNeighbors"
  | "annotate"
  | "resetView";

export interface SceneCommand {
  type: SceneCommandType;
  // filter
  window?: string;
  minValue?: number;
  asset?: string;
  // highlight
  nodeIds?: string[];
  style?: string;
  // flyTo
  target?: string;
  durationMs?: number;
  // focus / expandNeighbors / annotate
  nodeId?: string;
  depth?: number;
  direction?: string;
  limit?: number;
  label?: string;
}

export interface CopilotResponse {
  narrative: string;
  commands: SceneCommand[];
  grounded: boolean;
}

export interface CopilotMessage {
  role: "user" | "assistant";
  text: string;
  commands?: SceneCommand[];
  grounded?: boolean;
  error?: boolean;
}
