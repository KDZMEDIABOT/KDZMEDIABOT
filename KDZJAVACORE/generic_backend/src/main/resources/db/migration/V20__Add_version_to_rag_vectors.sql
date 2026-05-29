ALTER TABLE rag_vectors ADD COLUMN version INTEGER NOT NULL DEFAULT 2;

CREATE INDEX idx_rag_vectors_version ON rag_vectors(version);
