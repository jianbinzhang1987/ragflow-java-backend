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
    private final WebSearchService webSearchService;

    @org.springframework.beans.factory.annotation.Value("${rag.score-threshold:0.5}")
    private double defaultScoreThreshold;

    @org.springframework.beans.factory.annotation.Value("${websearch.fallback-enabled:true}")
    private boolean webSearchFallbackEnabled;

    public ChatService(EmbeddingClient embeddingClient, VectorStore vectorStore, ChunkRepository chunkRepo,
            ContextBuilder contextBuilder, PromptBuilder promptBuilder, LLMClient llmClient,
            WebSearchService webSearchService) {
        this.embeddingClient = embeddingClient;
        this.vectorStore = vectorStore;
        this.chunkRepo = chunkRepo;
        this.contextBuilder = contextBuilder;
        this.promptBuilder = promptBuilder;
        this.llmClient = llmClient;
        this.webSearchService = webSearchService;
    }

    public QueryResp query(QueryReq req) {
        List<QueryResp.Citation> citations = new ArrayList<>();
        String answer;
        String sourceType = "knowledge_base";
        List<ChunkEntity> chunks = new ArrayList<>();
        List<SearchResult> results = new ArrayList<>();
        boolean kbSearchFailed = false;

        // Try knowledge base search first
        try {
            float[] queryVec = embeddingClient.embed(req.getQuestion());

            List<String> targetCollections = new ArrayList<>();
            if (req.getCollectionIds() != null) {
                targetCollections.addAll(req.getCollectionIds());
            } else if (req.getCollection() != null && !req.getCollection().isEmpty()) {
                targetCollections.add(req.getCollection());
            }

            List<SearchResult> allResults = new ArrayList<>();
            for (String collection : targetCollections) {
                allResults.addAll(vectorStore.search(collection, queryVec, req.getTopK()));
            }

            results = allResults.stream()
                    .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                    .limit(req.getTopK())
                    .collect(Collectors.toList());

            double threshold = req.getScoreThreshold() > 0 ? req.getScoreThreshold() : defaultScoreThreshold;

            results = results.stream()
                    .filter(r -> r.getScore() >= threshold)
                    .collect(Collectors.toList());

            List<Long> chunkIds = results.stream().map(SearchResult::getChunkId).collect(Collectors.toList());
            chunks = chunkRepo.findAllById(chunkIds);

            log.info("Retrieval Results for query: {}", req.getQuestion());
            for (SearchResult res : results) {
                String docName = (String) res.getMetadata().getOrDefault("docName", "unknown");
                ChunkEntity c = chunks.stream().filter(ch -> ch.getId().equals(res.getChunkId())).findFirst()
                        .orElse(null);
                String kbName = c != null ? c.getCollection() : "unknown";
                log.info(" - [Score: {}] [KB: {}] [File: {}]", String.format("%.4f", res.getScore()), kbName, docName);
            }
        } catch (Exception e) {
            log.error("Knowledge base search failed: {}", e.getMessage());
            kbSearchFailed = true;
        }

        // Check if we have valid KB results
        if (!chunks.isEmpty() && !kbSearchFailed) {
            // Use knowledge base context
            String context = contextBuilder.buildContext(results, chunks);
            String prompt = promptBuilder.buildPrompt(context, req.getQuestion());
            log.info("Sending Prompt to LLM (KB context):\n{}", prompt);

            answer = llmClient.chat(prompt);
            log.info("Received Answer from LLM:\n{}", answer);

            // Build KB citations
            for (SearchResult res : results) {
                ChunkEntity c = chunks.stream().filter(ch -> ch.getId().equals(res.getChunkId())).findFirst()
                        .orElse(null);
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
        } else if (webSearchFallbackEnabled && webSearchService.isEnabled()) {
            // Fallback to web search
            log.info("No KB results found or KB search failed, falling back to web search...");
            sourceType = "web_search";

            List<WebSearchService.SearchResultItem> webResults = webSearchService.search(req.getQuestion(), 5);

            if (!webResults.isEmpty()) {
                // Build web search context
                StringBuilder webContext = new StringBuilder();
                for (int i = 0; i < webResults.size(); i++) {
                    WebSearchService.SearchResultItem item = webResults.get(i);
                    webContext.append(String.format("[%d] %s\nURL: %s\n%s\n\n",
                            i + 1, item.getTitle(), item.getUrl(), item.getSnippet()));

                    // Add web citation
                    citations.add(new QueryResp.Citation(
                            (long) (i + 1),
                            item.getTitle(),
                            (long) (i + 1),
                            1.0,
                            "来源: " + item.getUrl()));
                }

                String prompt = promptBuilder.buildWebSearchPrompt(webContext.toString(), req.getQuestion());
                log.info("Sending Prompt to LLM (Web search context):\n{}", prompt);

                answer = llmClient.chat(prompt);
                log.info("Received Answer from LLM (Web search):\n{}", answer);
            } else {
                // No web results either, use LLM's own knowledge
                log.info("No web search results, using LLM's own knowledge...");
                sourceType = "llm_knowledge";
                String prompt = promptBuilder.buildPrompt("", req.getQuestion());
                answer = llmClient.chat(prompt);
            }
        } else {
            // Web search not enabled, use LLM's own knowledge
            log.info("Web search not enabled or KB search failed, using LLM's own knowledge...");
            sourceType = "llm_knowledge";
            String prompt = promptBuilder.buildPrompt("", req.getQuestion());
            log.info("Sending Prompt to LLM (No context):\n{}", prompt);
            answer = llmClient.chat(prompt);
            log.info("Received Answer from LLM:\n{}", answer);
        }

        QueryResp resp = new QueryResp(answer, citations);
        resp.setSourceType(sourceType);
        return resp;
    }

    public List<ChunkEntity> searchOnly(QueryReq req) {
        float[] queryVec = embeddingClient.embed(req.getQuestion());

        List<SearchResult> results = vectorStore.search(req.getCollection(), queryVec, req.getTopK());

        List<Long> chunkIds = results.stream().map(SearchResult::getChunkId).collect(Collectors.toList());
        return chunkRepo.findAllById(chunkIds);
    }

    public void queryStream(QueryReq req, org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter) {
        new Thread(() -> {
            try {
                List<ChunkEntity> chunks = new ArrayList<>();
                List<SearchResult> results = new ArrayList<>();
                boolean kbSearchFailed = false;
                String sourceType = "knowledge_base";

                // Send initial metadata
                emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                        .name("start")
                        .data("{\"status\":\"retrieving\"}"));

                // Try knowledge base search first
                try {
                    float[] queryVec = embeddingClient.embed(req.getQuestion());

                    List<String> targetCollections = new ArrayList<>();
                    if (req.getCollectionIds() != null) {
                        targetCollections.addAll(req.getCollectionIds());
                    } else if (req.getCollection() != null && !req.getCollection().isEmpty()) {
                        targetCollections.add(req.getCollection());
                    }

                    List<SearchResult> allResults = new ArrayList<>();
                    for (String collection : targetCollections) {
                        allResults.addAll(vectorStore.search(collection, queryVec, req.getTopK()));
                    }

                    results = allResults.stream()
                            .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                            .limit(req.getTopK())
                            .collect(Collectors.toList());

                    double threshold = req.getScoreThreshold() > 0 ? req.getScoreThreshold() : defaultScoreThreshold;

                    results = results.stream()
                            .filter(r -> r.getScore() >= threshold)
                            .collect(Collectors.toList());

                    List<Long> chunkIds = results.stream().map(SearchResult::getChunkId).collect(Collectors.toList());
                    chunks = chunkRepo.findAllById(chunkIds);

                    log.info("Retrieval Results for query: {}", req.getQuestion());
                    for (SearchResult res : results) {
                        String docName = (String) res.getMetadata().getOrDefault("docName", "unknown");
                        ChunkEntity c = chunks.stream().filter(ch -> ch.getId().equals(res.getChunkId())).findFirst()
                                .orElse(null);
                        String kbName = c != null ? c.getCollection() : "unknown";
                        log.info(" - [Score: {}] [KB: {}] [File: {}]", String.format("%.4f", res.getScore()), kbName,
                                docName);
                    }
                } catch (Exception e) {
                    log.error("Knowledge base search failed: {}", e.getMessage());
                    kbSearchFailed = true;
                }

                // Build prompt
                String prompt;
                if (!chunks.isEmpty() && !kbSearchFailed) {
                    String context = contextBuilder.buildContext(results, chunks);
                    prompt = promptBuilder.buildPrompt(context, req.getQuestion());
                    log.info("Sending Prompt to LLM (KB context - stream)");
                } else if (webSearchFallbackEnabled && webSearchService.isEnabled()) {
                    log.info("No KB results found or KB search failed, falling back to web search...");
                    sourceType = "web_search";

                    List<WebSearchService.SearchResultItem> webResults = webSearchService.search(req.getQuestion(), 5);
                    if (!webResults.isEmpty()) {
                        StringBuilder webContext = new StringBuilder();
                        for (int i = 0; i < webResults.size(); i++) {
                            WebSearchService.SearchResultItem item = webResults.get(i);
                            webContext.append(String.format("[%d] %s\nURL: %s\n%s\n\n",
                                    i + 1, item.getTitle(), item.getUrl(), item.getSnippet()));
                        }
                        prompt = promptBuilder.buildWebSearchPrompt(webContext.toString(), req.getQuestion());
                    } else {
                        sourceType = "llm_knowledge";
                        prompt = promptBuilder.buildPrompt("", req.getQuestion());
                    }
                } else {
                    sourceType = "llm_knowledge";
                    prompt = promptBuilder.buildPrompt("", req.getQuestion());
                }

                // Send source type
                emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                        .name("source")
                        .data("{\"type\":\"" + sourceType + "\"}"));

                // Stream LLM response
                llmClient.chatStream(prompt, emitter);

            } catch (Exception e) {
                log.error("Stream query failed", e);
                try {
                    emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                            .name("error")
                            .data("Error: " + e.getMessage()));
                    emitter.complete();
                } catch (Exception ex) {
                    emitter.completeWithError(ex);
                }
            }
        }).start();
    }
}
