package com.ragflow.backend.pipeline.query;

import com.ragflow.backend.entity.ChunkEntity;
import com.ragflow.backend.repository.ChunkRepository;
import com.ragflow.backend.vectorstore.SearchResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ContextBuilder {

    private final ChunkRepository chunkRepo;

    @Value("${rag.max-context-chars:4000}")
    private int maxContextChars;

    public ContextBuilder(ChunkRepository chunkRepo) {
        this.chunkRepo = chunkRepo;
    }

    public String buildContext(List<SearchResult> results, List<ChunkEntity> loadedChunks) {
        StringBuilder sb = new StringBuilder();
        // Just concatenate content until max chars
        for (SearchResult res : results) {
            // Find content
            ChunkEntity chunk = loadedChunks.stream()
                    .filter(c -> c.getId().equals(res.getChunkId()))
                    .findFirst().orElse(null);

            if (chunk != null) {
                if (sb.length() + chunk.getContent().length() > maxContextChars) {
                    break;
                }
                sb.append(chunk.getContent()).append("\n\n");
            }
        }
        return sb.toString();
    }
}
