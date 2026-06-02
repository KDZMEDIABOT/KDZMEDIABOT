-- Drop old JSON text column; Java code will re-index all threads with pgvector
ALTER TABLE rag_vectors DROP COLUMN embedding;

-- Add vector column with 2000D for HNSW support
ALTER TABLE rag_vectors ADD COLUMN embedding vector(2000);

-- HNSW index for fast cosine similarity (requires <= 2000 dims)
CREATE INDEX idx_rag_vectors_embedding_hnsw ON rag_vectors USING hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 64);
