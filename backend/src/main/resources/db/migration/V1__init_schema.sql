CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE address (
    address        TEXT PRIMARY KEY,
    chain          TEXT NOT NULL,
    label          TEXT,
    first_seen_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE transfer (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tx_hash      TEXT NOT NULL,
    log_index    INT  NOT NULL DEFAULT 0,
    chain        TEXT NOT NULL,
    from_addr    TEXT NOT NULL REFERENCES address(address),
    to_addr      TEXT NOT NULL REFERENCES address(address),
    asset        TEXT NOT NULL,
    value_raw    NUMERIC(78,0) NOT NULL,
    value_norm   DOUBLE PRECISION NOT NULL,
    block_time   TIMESTAMPTZ NOT NULL,
    ingested_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tx_hash, log_index)
);

CREATE INDEX idx_transfer_block_time ON transfer (chain, block_time DESC);
CREATE INDEX idx_transfer_from       ON transfer (from_addr, block_time DESC);
CREATE INDEX idx_transfer_to         ON transfer (to_addr,   block_time DESC);
CREATE INDEX idx_transfer_value      ON transfer (chain, value_norm DESC, block_time DESC);

CREATE TABLE address_stats (
    address       TEXT PRIMARY KEY REFERENCES address(address),
    window_label  TEXT NOT NULL,
    in_value      DOUBLE PRECISION NOT NULL DEFAULT 0,
    out_value     DOUBLE PRECISION NOT NULL DEFAULT 0,
    tx_count      INT NOT NULL DEFAULT 0,
    is_whale      BOOLEAN NOT NULL DEFAULT false,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE address_embedding (
    address     TEXT PRIMARY KEY REFERENCES address(address),
    summary     TEXT NOT NULL,
    embedding   vector(768) NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_addr_embed ON address_embedding
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);
