package com.ragflow.backend.vectorstore;

import java.util.List;
import java.util.Map;

public interface VectorStore {
    void upsert(String collection, Long chunkId, float[] vector, Map<String, Object> metadata);

    List<SearchResult> search(String collection, float[] queryVector, int topK);

    void save();

    void load();
}
