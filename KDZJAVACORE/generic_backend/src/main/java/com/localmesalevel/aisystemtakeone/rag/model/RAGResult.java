package com.localmesalevel.aisystemtakeone.rag.model;

public class RAGResult {
    private String content;
    private String sourceType;
    private long sourceId;
    private double relevance;
    private String citation;

    public RAGResult() {
    }

    public RAGResult(String content, String sourceType, long sourceId, double relevance, String citation) {
        this.content = content;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.relevance = relevance;
        this.citation = citation;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public long getSourceId() {
        return sourceId;
    }

    public void setSourceId(long sourceId) {
        this.sourceId = sourceId;
    }

    public double getRelevance() {
        return relevance;
    }

    public void setRelevance(double relevance) {
        this.relevance = relevance;
    }

    public String getCitation() {
        return citation;
    }

    public void setCitation(String citation) {
        this.citation = citation;
    }
}
