# Onchain 3D Explorer

A real-time 3D graph visualization of on-chain transactions across Solana and Base (EVM), with an AI copilot powered by Google Gemini.

![Tech Stack](https://img.shields.io/badge/Spring_Boot-3.5-6DB33F?style=flat&logo=spring) ![Next.js](https://img.shields.io/badge/Next.js-15-black?style=flat&logo=next.js) ![Three.js](https://img.shields.io/badge/Three.js-r169-black?style=flat&logo=three.js) ![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?style=flat&logo=postgresql)

## Features

- **Live 3D Graph** — force-directed graph of wallet addresses (nodes) and token transfers (edges), updated in real time via Server-Sent Events
- **Multi-chain** — switch between Solana and Base (EVM) with a single click
- **AI Copilot** — ask natural language questions about the graph (powered by Gemini 2.5 Flash + vector similarity search)
- **Visual cues** — whale wallets, high-value transfers, recency decay all encoded as color and size
- **Node inspector** — click any node to see activity score, top counterparties, and recent transfers

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Spring Boot 3.5, WebFlux, R2DBC, JDBC |
| Database | PostgreSQL 16 + pgvector |
| Ingest | Public WebSocket RPCs (Solana publicnode, Base publicnode) |
| AI | Google Gemini 2.5 Flash (LLM) + gemini-embedding-001 (RAG) |
| Frontend | Next.js 15, React Three Fiber, Zustand |
| Infra | Docker Compose |

## Getting Started

### Prerequisites

- Docker & Docker Compose
- A [Google AI Studio](https://aistudio.google.com) API key (free tier works)

### 1. Clone & configure

```bash
git clone https://github.com/maulanaiskak/onchain-3d-explorer.git
cd onchain-3d-explorer

cp .env.example .env
# Edit .env and fill in GEMINI_API_KEY
```

### 2. Run

```bash
docker compose up --build
```

Open **http://localhost:3000**

On subsequent runs (no code changes):

```bash
docker compose up
```

### Environment Variables

| Variable | Required | Description |
|----------|----------|-------------|
| `GEMINI_API_KEY` | Yes | Google AI Studio key for LLM + embeddings |
| `HELIUS_API_KEY` | No | Higher Solana RPC rate limits |
| `ALCHEMY_API_KEY` | No | Higher Base RPC rate limits |

## How to Use

- **Orbit** — click + drag to rotate the graph
- **Zoom** — scroll wheel
- **Select node** — click any sphere to inspect wallet details
- **Switch chain** — BASE / SOL buttons in the top-left HUD
- **AI Copilot** — click the chat icon and ask anything (e.g. *"who are the top whales?"*, *"show me USDC flows"*)
- **Legend** — top-right corner explains node colors and sizes

## Architecture

```
Public WS RPCs ──► IngestPipeline ──► PostgreSQL (pgvector)
                                           │
                        ┌──────────────────┤
                        │                  │
                   SseController      CopilotHandler
                        │                  │
                   EventSource         Gemini API
                        │
                   React Three Fiber (3D graph)
```

Live transaction signatures arrive via WebSocket, get normalized and persisted, then fan out to all SSE subscribers as graph deltas (upsertNodes / upsertEdges). The frontend batches updates at 250ms and caps the graph at 150 nodes / 300 edges to keep the browser smooth.
