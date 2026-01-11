package com.ragflow.backend.embedding;

import java.util.List;

public interface EmbeddingClient {
    float[] embed(String text);

    List<float[]> embedBatch(List<String> texts);

    int getDimension();
}
