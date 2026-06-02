-- Rename old JSON text column to temporary name
ALTER TABLE rag_vectors RENAME COLUMN embedding TO embedding_json;

-- Add vector column with exactly 2000 dims for HNSW support
ALTER TABLE rag_vectors ADD COLUMN embedding vector(2000);

-- Populate vector column from JSON text using SQL string conversion
UPDATE rag_vectors SET embedding = embedding_json::text::vector WHERE embedding_json IS NOT NULL;

-- Drop old JSON text column after migrating all rows
ALTER TABLE rag_vectors DROP COLUMN embedding_json;

-- HNSW index for fast cosine similarity (requires <= 2000 dims)
CREATE INDEX idx_rag_vectors_embedding_hnsw ON rag_vectors USING hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 64);
