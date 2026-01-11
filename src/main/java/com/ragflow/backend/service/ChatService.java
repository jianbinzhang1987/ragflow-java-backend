package com.ragflow.backend.service;

import com.ragflow.backend.dto.QueryReq;
import com.ragflow.backend.dto.QueryResp;
import com.ragflow.backend.embedding.EmbeddingClient;
import com.ragflow.backend.entity.ChunkEntity;
import com.ragflow.backend.llm.LLMClient;
import com.ragflow.backend.pipeline.query.ContextBuilder;
import com.ragflow.backend.pipeline.query.PromptBuilder;
import com.ragflow.backend.repository.ChunkRepository;
import com.ragflow.backend.vectorstore.SearchResult;
import com.ragflow.backend.vectorstore.VectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final EmbeddingClient embeddingClient;
    private final VectorStore vectorStore;
    private final ChunkRepository chunkRepo;
    private final ContextBuilder contextBuilder;
    private final PromptBuilder promptBuilder;
    private final LLMClient llmClient;

    public ChatService(EmbeddingClient embeddingClient, VectorStore vectorStore, ChunkRepository chunkRepo,
            ContextBuilder contextBuilder, PromptBuilder promptBuilder, LLMClient llmClient) {
        this.embeddingClient = embeddingClient;
        this.vectorStore = vectorStore;
        this.chunkRepo = chunkRepo;
        this.contextBuilder = contextBuilder;
        this.promptBuilder = promptBuilder;
        this.llmClient = llmClient;
    }

    public QueryResp query(QueryReq req) {
        float[] queryVec = embeddingClient.embed(req.getQuestion());

        List<String> targetCollections = new ArrayList<>();
        if (req.getCollectionIds() != null && !req.getCollectionIds().isEmpty()) {
            targetCollections.addAll(req.getCollectionIds());
        } else {
            targetCollections.add(req.getCollection());
        }

        List<SearchResult> allResults = new ArrayList<>();
        for (String collection : targetCollections) {
            allResults.addAll(vectorStore.search(collection, queryVec, req.getTopK()));
        }

        List<SearchResult> results = allResults.stream()
                .sorted((a, b) -> Double.compare(b.getScore(), a.getScore())) // Global sort
                .limit(req.getTopK()) // Global topK
                .collect(Collectors.toList());

        if (req.getScoreThreshold() > 0) {
            results = results.stream()
                    .filter(r -> r.getScore() >= req.getScoreThreshold())
                    .collect(Collectors.toList());
        }

        List<Long> chunkIds = results.stream().map(SearchResult::getChunkId).collect(Collectors.toList());
        List<ChunkEntity> chunks = chunkRepo.findAllById(chunkIds);

        String context = contextBuilder.buildContext(results, chunks);

        String prompt = promptBuilder.buildPrompt(context, req.getQuestion());

        String answer = llmClient.chat(prompt);

        List<QueryResp.Citation> citations = new ArrayList<>();
        for (SearchResult res : results) {
            ChunkEntity c = chunks.stream().filter(ch -> ch.getId().equals(res.getChunkId())).findFirst().orElse(null);
            if (c != null) {
                String docName = (String) res.getMetadata().getOrDefault("docName", "unknown");
                citations.add(new QueryResp.Citation(
                        Long.valueOf(res.getMetadata().getOrDefault("docId", 0).toString()),
                        docName,
                        c.getId(),
                        res.getScore(),
                        c.getContent().substring(0, Math.min(c.getContent().length(), 100)) + "..."));
            }
        }

        return new QueryResp(answer, citations);
    }

    public List<ChunkEntity> searchOnly(QueryReq req) {
        float[] queryVec = embeddingClient.embed(req.getQuestion());

        List<SearchResult> results = vectorStore.search(req.getCollection(), queryVec, req.getTopK());

        List<Long> chunkIds = results.stream().map(SearchResult::getChunkId).collect(Collectors.toList());
        return chunkRepo.findAllById(chunkIds);
    }
}
