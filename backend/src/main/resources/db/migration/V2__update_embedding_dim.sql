-- Migrate address_embedding to 3072-dim (gemini-embedding-001).
-- pgvector's HNSW and IVFFlat indexes cap at 2000 dims, so we use a
-- plain btree index on address and rely on sequential scan for cosine
-- similarity. For a portfolio-scale dataset this is perfectly adequate.
DROP INDEX IF EXISTS idx_addr_embed;

ALTER TABLE address_embedding
    ALTER COLUMN embedding TYPE vector(3072)
    USING embedding::text::vector(3072);
