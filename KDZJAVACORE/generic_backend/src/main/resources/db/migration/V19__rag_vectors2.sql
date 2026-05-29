ALTER TABLE rag_vectors DROP COLUMN embedding;
    
ALTER TABLE rag_vectors ADD COLUMN embedding TEXT NOT NULL;