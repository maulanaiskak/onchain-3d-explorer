export interface NodeDTO {
  id: string;
  chain: string;
  label: string | null;
  volume: number;
  risk: number;
  flagged: boolean;
}

export interface EdgeDTO {
  id: string;
  from: string;
  to: string;
  asset: string;
  valueNorm: number;
  blockTime: string;
}

export const MOCK_NODES: NodeDTO[] = [
  { id: "0xAAABBB", chain: "solana", label: "Whale A",   volume: 0.9, risk: 0.1, flagged: false },
  { id: "0xCCCDDD", chain: "solana", label: null,        volume: 0.4, risk: 0.3, flagged: false },
  { id: "0xEEEFFF", chain: "solana", label: "Exchange",  volume: 0.8, risk: 0.0, flagged: false },
  { id: "0x111222", chain: "solana", label: null,        volume: 0.2, risk: 0.7, flagged: true  },
  { id: "0x333444", chain: "solana", label: "Whale B",   volume: 0.6, risk: 0.2, flagged: false },
  { id: "0x555666", chain: "solana", label: null,        volume: 0.1, risk: 0.9, flagged: true  },
  { id: "0x777888", chain: "solana", label: "Bridge",    volume: 0.5, risk: 0.1, flagged: false },
  { id: "0x999AAA", chain: "solana", label: null,        volume: 0.3, risk: 0.4, flagged: false },
];

export const MOCK_EDGES: EdgeDTO[] = [
  { id: "e1", from: "0xAAABBB", to: "0xCCCDDD", asset: "SOL", valueNorm: 0.8, blockTime: "2026-06-10T01:00:00Z" },
  { id: "e2", from: "0xAAABBB", to: "0xEEEFFF", asset: "SOL", valueNorm: 0.5, blockTime: "2026-06-10T01:01:00Z" },
  { id: "e3", from: "0xCCCDDD", to: "0x111222", asset: "SOL", valueNorm: 0.3, blockTime: "2026-06-10T01:02:00Z" },
  { id: "e4", from: "0x111222", to: "0x555666", asset: "SOL", valueNorm: 0.2, blockTime: "2026-06-10T01:03:00Z" },
  { id: "e5", from: "0x333444", to: "0xEEEFFF", asset: "SOL", valueNorm: 0.6, blockTime: "2026-06-10T01:04:00Z" },
  { id: "e6", from: "0x777888", to: "0x333444", asset: "SOL", valueNorm: 0.4, blockTime: "2026-06-10T01:05:00Z" },
  { id: "e7", from: "0x777888", to: "0x999AAA", asset: "SOL", valueNorm: 0.3, blockTime: "2026-06-10T01:06:00Z" },
  { id: "e8", from: "0x999AAA", to: "0xAAABBB", asset: "SOL", valueNorm: 0.7, blockTime: "2026-06-10T01:07:00Z" },
];
