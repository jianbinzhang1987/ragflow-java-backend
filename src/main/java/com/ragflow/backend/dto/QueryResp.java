package com.ragflow.backend.dto;

import java.util.List;

public class QueryResp {
    private String answer;
    private List<Citation> citations;
    private String sourceType = "knowledge_base"; // knowledge_base, web_search, llm_knowledge

    public QueryResp() {
    }

    public QueryResp(String answer, List<Citation> citations) {
        this.answer = answer;
        this.citations = citations;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public List<Citation> getCitations() {
        return citations;
    }

    public void setCitations(List<Citation> citations) {
        this.citations = citations;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public static class Citation {
        private Long docId;
        private String docName;
        private Long chunkId;
        private double score;
        private String snippet;

        public Citation() {
        }

        public Citation(Long docId, String docName, Long chunkId, double score, String snippet) {
            this.docId = docId;
            this.docName = docName;
            this.chunkId = chunkId;
            this.score = score;
            this.snippet = snippet;
        }

        public Long getDocId() {
            return docId;
        }

        public void setDocId(Long docId) {
            this.docId = docId;
        }

        public String getDocName() {
            return docName;
        }

        public void setDocName(String docName) {
            this.docName = docName;
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

        public String getSnippet() {
            return snippet;
        }

        public void setSnippet(String snippet) {
            this.snippet = snippet;
        }
    }
}
