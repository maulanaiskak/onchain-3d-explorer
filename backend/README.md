# On-Chain 3D Explorer — Backend

Spring Boot 3.5 / WebFlux / R2DBC reactive backend for the On-Chain 3D Explorer.

## Prerequisites

| Tool | Version |
|------|---------|
| Java | 25 (via SDKMAN or Homebrew) |
| Docker & Docker Compose | v2+ |
| (Optional) Gradle | 8.10 — wrapper included, no local install needed |

## Quick Start

### 1. Start PostgreSQL + pgvector

```bash
# From the project root (where docker-compose.yml lives)
docker compose up -d
```

This starts `pgvector/pgvector:pg16` on port **5432** with:
- Database: `onchain3d`
- User / Password: `postgres` / `postgres`

### 2. Run the Application

```bash
# From the backend/ directory
./gradlew bootRun
```

Flyway migrations run automatically on startup and create all tables.

The application listens on **http://localhost:8080**.

Health check: `http://localhost:8080/actuator/health`

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_HOST` | `localhost` | PostgreSQL host |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_NAME` | `onchain3d` | Database name |
| `DB_USER` | `postgres` | Database user |
| `DB_PASSWORD` | `postgres` | Database password |
| `VERTEX_PROJECT_ID` | _(empty)_ | GCP project ID for Vertex AI |
| `VERTEX_LOCATION` | `us-central1` | Vertex AI region |
| `VERTEX_MODEL` | `gemini-1.5-flash` | LLM model ID |
| `VERTEX_EMBED_MODEL` | `text-embedding-004` | Embedding model ID |
| `HELIUS_API_KEY` | _(empty)_ | Helius API key (Solana ingest) |
| `ALCHEMY_API_KEY` | _(empty)_ | Alchemy API key (EVM ingest) |
| `WINDOW_DEFAULT` | `1h` | Default time window for graph queries |
| `VALUE_FLOOR_NORM` | `0.01` | Minimum normalised transfer value to ingest |
| `MAX_AGENT_STEPS` | `3` | Max reasoning steps for the co-pilot agent |

Export variables before running:

```bash
export VERTEX_PROJECT_ID=my-gcp-project
export HELIUS_API_KEY=my-helius-key
./gradlew bootRun
```

## Build

```bash
./gradlew build          # compile + test
./gradlew compileJava    # compile only
./gradlew test           # run tests
```

## Project Structure

```
src/main/java/com/maul/onchain3d/
├── Onchain3dApplication.java   # Spring Boot entry point
├── config/                     # AppProperties, R2dbcConfig
├── ingest/                     # Chain data ingestion (Helius, Alchemy)
├── normalize/                  # Raw → Edge normalisation
├── graph/                      # R2DBC entities (Address, Transfer, …)
├── stream/dto/                 # SSE wire types (Delta, NodeDTO, EdgeDTO)
└── copilot/                    # AI agent / copilot layer
```
