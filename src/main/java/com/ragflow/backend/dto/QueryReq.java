package com.ragflow.backend.dto;

public class QueryReq {
    private String collection = "default";
    private String question;
    private int topK = 5;
    private double scoreThreshold = 0.0;

    public String getCollection() {
        return collection;
    }

    public void setCollection(String collection) {
        this.collection = collection;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public double getScoreThreshold() {
        return scoreThreshold;
    }

    public void setScoreThreshold(double scoreThreshold) {
        this.scoreThreshold = scoreThreshold;
    }

    private java.util.List<String> collectionIds;

    public java.util.List<String> getCollectionIds() {
        return collectionIds;
    }

    public void setCollectionIds(java.util.List<String> collectionIds) {
        this.collectionIds = collectionIds;
    }
}
