package com.ragflow.backend.dto;

public class IndexResp {
    private Long docId;
    private int chunkCount;
    private String status;
    private String error;

    public IndexResp(Long docId, int chunkCount, String status, String error) {
        this.docId = docId;
        this.chunkCount = chunkCount;
        this.status = status;
        this.error = error;
    }

    public Long getDocId() {
        return docId;
    }

    public void setDocId(Long docId) {
        this.docId = docId;
    }

    public int getChunkCount() {
        return chunkCount;
    }

    public void setChunkCount(int chunkCount) {
        this.chunkCount = chunkCount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
