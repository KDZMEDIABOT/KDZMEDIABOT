CREATE TABLE rag_vectors (
    id BIGSERIAL PRIMARY KEY,
    source_type VARCHAR(50) NOT NULL,
    source_id BIGINT NOT NULL,
    thread_id BIGINT,
    user_id BIGINT NOT NULL,
    chunk_text TEXT NOT NULL,
    embedding float[] NOT NULL,
    chunk_index INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_rag_vectors_user ON rag_vectors(user_id);
CREATE INDEX idx_rag_vectors_user_source ON rag_vectors(user_id, source_type, source_id);
CREATE INDEX idx_rag_vectors_thread ON rag_vectors(thread_id);
CREATE INDEX idx_rag_vectors_created_at ON rag_vectors(created_at DESC);
