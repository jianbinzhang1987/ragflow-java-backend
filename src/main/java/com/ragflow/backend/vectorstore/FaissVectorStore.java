package com.ragflow.backend.vectorstore;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * A "Mock" FAISS implementation using simple in-memory vectors and Cosine
 * Similarity.
 * Persists to local file system to simulate a persistent index.
 * TODO: Replace with actual FAISS JNI bindings or standard vector DB client.
 */
@Component
public class FaissVectorStore implements VectorStore {

    private static final Logger log = LoggerFactory.getLogger(FaissVectorStore.class);

    @Value("${storage.index-dir:./data/index}")
    private String indexDir;

    // Collection -> ChunkId -> Entry
    private final Map<String, Map<Long, VectorEntry>> indexes = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        this.load();
    }

    @Override
    public void upsert(String collection, Long chunkId, float[] vector, Map<String, Object> metadata) {
        indexes.computeIfAbsent(collection, k -> new ConcurrentHashMap<>())
                .put(chunkId, new VectorEntry(vector, metadata));
        // Auto-save or periodic save could be implemented, here we rely on manual
        // save() check or @PreDestroy
        // For this minimal pipeline, we might want to save immediately or let the
        // service call save()
    }

    @Override
    public List<SearchResult> search(String collection, float[] queryVector, int topK) {
        Map<Long, VectorEntry> index = indexes.get(collection);
        if (index == null || index.isEmpty()) {
            return Collections.emptyList();
        }

        // Brute-force Cosine Similarity
        // PriorityQueue to keep topK, min-heap by score if we want largest k, actually
        // simply sort list is easier for small dataset

        return index.entrySet().stream()
                .map(e -> {
                    double score = cosineSimilarity(queryVector, e.getValue().vector);
                    return new SearchResult(e.getKey(), score, e.getValue().metadata);
                })
                .sorted((a, b) -> Double.compare(b.getScore(), a.getScore())) // Descending
                .limit(topK)
                .collect(Collectors.toList());
    }

    @Override
    public synchronized void save() {
        File dir = new File(indexDir);
        if (!dir.exists())
            dir.mkdirs();

        for (Map.Entry<String, Map<Long, VectorEntry>> entry : indexes.entrySet()) {
            String collection = entry.getKey();
            File file = new File(dir, collection + ".faiss");
            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file))) {
                oos.writeObject(entry.getValue());
                log.info("Saved index for collection: {} to {}", collection, file.getAbsolutePath());
            } catch (IOException e) {
                log.error("Failed to save index for collection: " + collection, e);
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public synchronized void load() {
        File dir = new File(indexDir);
        if (!dir.exists())
            return;

        File[] files = dir.listFiles((d, name) -> name.endsWith(".faiss"));
        if (files == null)
            return;

        for (File file : files) {
            String filename = file.getName();
            String collection = filename.substring(0, filename.lastIndexOf("."));
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
                Map<Long, VectorEntry> data = (Map<Long, VectorEntry>) ois.readObject();
                indexes.put(collection, new ConcurrentHashMap<>(data));
                log.info("Loaded index for collection: {} with {} items", collection, data.size());
            } catch (Exception e) {
                log.error("Failed to load index from " + file.getAbsolutePath(), e);
            }
        }
    }

    private double cosineSimilarity(float[] v1, float[] v2) {
        if (v1.length != v2.length)
            return 0.0;
        double dot = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;
        for (int i = 0; i < v1.length; i++) {
            dot += v1[i] * v2[i];
            norm1 += v1[i] * v1[i];
            norm2 += v2[i] * v2[i];
        }
        if (norm1 == 0 || norm2 == 0)
            return 0.0;
        return dot / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }

    // Serializable wrapper
    private static class VectorEntry implements Serializable {
        float[] vector;
        Map<String, Object> metadata;

        public VectorEntry(float[] vector, Map<String, Object> metadata) {
            this.vector = vector;
            this.metadata = metadata;
        }
    }
}
