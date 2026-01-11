package com.ragflow.backend.dto;

public class UploadResp {
    private Long docId;
    private String fileName;
    private String status;

    public UploadResp(Long docId, String fileName, String status) {
        this.docId = docId;
        this.fileName = fileName;
        this.status = status;
    }

    public Long getDocId() {
        return docId;
    }

    public void setDocId(Long docId) {
        this.docId = docId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
