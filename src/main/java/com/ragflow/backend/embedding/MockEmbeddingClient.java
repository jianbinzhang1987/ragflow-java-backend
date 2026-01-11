package com.ragflow.backend.embedding;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
@ConditionalOnProperty(name = "embedding.provider", havingValue = "mock", matchIfMissing = true)
public class MockEmbeddingClient implements EmbeddingClient {

    @Value("${embedding.dimension:1536}")
    private int dimension;

    @Override
    public float[] embed(String text) {
        return generateVector(text);
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        List<float[]> list = new ArrayList<>();
        for (String t : texts) {
            list.add(generateVector(t));
        }
        return list;
    }

    @Override
    public int getDimension() {
        return dimension;
    }

    private float[] generateVector(String text) {
        // Deterministic vector based on text hash for testing
        Random random = new Random(text.hashCode());
        float[] v = new float[dimension];
        double norm = 0.0;
        for (int i = 0; i < dimension; i++) {
            v[i] = random.nextFloat() - 0.5f;
            norm += v[i] * v[i];
        }
        // Normalize
        double sqrtNorm = Math.sqrt(norm);
        if (sqrtNorm > 0) {
            for (int i = 0; i < dimension; i++) {
                v[i] /= (float) sqrtNorm;
            }
        }
        return v;
    }
}
