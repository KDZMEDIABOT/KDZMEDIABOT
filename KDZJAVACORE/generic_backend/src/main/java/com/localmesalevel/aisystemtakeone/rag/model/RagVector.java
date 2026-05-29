package com.localmesalevel.aisystemtakeone.rag.model;

import com.localmesalevel.aisystemtakeone.rag.util.EmbeddingArrayConverter;

import javax.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "rag_vectors")
public class RagVector {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_type", nullable = false, length = 50)
    private String sourceType;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Column(name = "thread_id")
    private Long threadId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "chunk_text", nullable = false, columnDefinition = "text")
    private String chunkText;

    @Column(name = "embedding", nullable = false, columnDefinition = "text")
    @Convert(converter = EmbeddingArrayConverter.class)
    private double[] embedding;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(name = "version", nullable = false)
    private int version = 2;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public RagVector() {
        this.createdAt = Instant.now();
    }

    public void setId(Long id) { this.id = id; }
    public Long getId() { return id; }

    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getSourceType() { return sourceType; }

    public void setSourceId(Long sourceId) { this.sourceId = sourceId; }
    public Long getSourceId() { return sourceId; }

    public void setThreadId(Long threadId) { this.threadId = threadId; }
    public Long getThreadId() { return threadId; }

    public void setUserId(Long userId) { this.userId = userId; }
    public Long getUserId() { return userId; }

    public void setChunkText(String chunkText) { this.chunkText = chunkText; }
    public String getChunkText() { return chunkText; }

    public void setEmbedding(double[] embedding) { this.embedding = embedding; }
    public double[] getEmbedding() { return embedding; }

    public void setChunkIndex(int chunkIndex) { this.chunkIndex = chunkIndex; }
    public int getChunkIndex() { return chunkIndex; }

    public void setVersion(int version) { this.version = version; }
    public int getVersion() { return version; }

    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getCreatedAt() { return createdAt; }
}
