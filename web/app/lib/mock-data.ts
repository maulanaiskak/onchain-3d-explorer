export interface NodeDTO {
  id: string;
  chain: string;
  label: string | null;
  weight: number;   // aggregated transfer value weight
  recency: number;  // 0-1, 1 = just seen
  isWhale: boolean;
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
  { id: "0xAAABBB", chain: "solana", label: "Whale A",   weight: 0.9, recency: 1.0, isWhale: true  },
  { id: "0xCCCDDD", chain: "solana", label: null,        weight: 0.4, recency: 0.8, isWhale: false },
  { id: "0xEEEFFF", chain: "solana", label: "Exchange",  weight: 0.8, recency: 0.9, isWhale: false },
  { id: "0x111222", chain: "solana", label: null,        weight: 0.2, recency: 0.5, isWhale: false },
  { id: "0x333444", chain: "solana", label: "Whale B",   weight: 0.6, recency: 0.7, isWhale: true  },
  { id: "0x555666", chain: "solana", label: null,        weight: 0.1, recency: 0.3, isWhale: false },
  { id: "0x777888", chain: "solana", label: "Bridge",    weight: 0.5, recency: 0.6, isWhale: false },
  { id: "0x999AAA", chain: "solana", label: null,        weight: 0.3, recency: 0.4, isWhale: false },
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
