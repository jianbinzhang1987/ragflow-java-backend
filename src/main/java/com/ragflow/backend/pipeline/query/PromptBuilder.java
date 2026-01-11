package com.ragflow.backend.pipeline.query;

import org.springframework.stereotype.Component;

@Component
public class PromptBuilder {

    private static final String TEMPLATE_WITH_CONTEXT = """
            You are a helpful assistant. Use the following pieces of context to answer the question at the end.
            If the context doesn't contain enough information, use your own knowledge to provide a helpful answer.

            Context:
            %s

            Question: %s

            Answer:
            """;

    private static final String TEMPLATE_WITH_WEB_SEARCH = """
            You are a helpful assistant. The following information was retrieved from web search results.
            Use this information to answer the question. Please cite the source URLs in your answer.

            Web Search Results:
            %s

            Question: %s

            Please provide a comprehensive answer based on the search results above. Include source citations in the format [Source: URL].
            Answer:
            """;

    private static final String TEMPLATE_NO_CONTEXT = """
            You are a helpful assistant. Answer the following question based on your knowledge.

            Question: %s

            Answer:
            """;

    public String buildPrompt(String context, String question) {
        if (context == null || context.trim().isEmpty()) {
            return String.format(TEMPLATE_NO_CONTEXT, question);
        }
        return String.format(TEMPLATE_WITH_CONTEXT, context, question);
    }

    public String buildWebSearchPrompt(String webContext, String question) {
        return String.format(TEMPLATE_WITH_WEB_SEARCH, webContext, question);
    }
}
