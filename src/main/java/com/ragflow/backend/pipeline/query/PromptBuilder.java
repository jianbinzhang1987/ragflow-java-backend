package com.ragflow.backend.pipeline.query;

import org.springframework.stereotype.Component;

@Component
public class PromptBuilder {

    private static final String TEMPLATE = """
            You are a helpful assistant. Use the following pieces of context to answer the question at the end.
            If you don't know the answer, just say that you don't know, don't try to make up an answer.

            Context:
            %s

            Question: %s

            Answer:
            """;

    public String buildPrompt(String context, String question) {
        return String.format(TEMPLATE, context, question);
    }
}
