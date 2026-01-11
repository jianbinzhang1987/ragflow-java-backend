package com.ragflow.backend.vectorstore;

import java.util.Map;

public class SearchResult {
    private Long chunkId;
    private double score;
    private Map<String, Object> metadata;

    public SearchResult() {
    }

    public SearchResult(Long chunkId, double score, Map<String, Object> metadata) {
        this.chunkId = chunkId;
        this.score = score;
        this.metadata = metadata;
    }

    public Long getChunkId() {
        return chunkId;
    }

    public void setChunkId(Long chunkId) {
        this.chunkId = chunkId;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}
