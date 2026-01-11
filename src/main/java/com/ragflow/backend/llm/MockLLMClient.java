package com.ragflow.backend.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "llm.provider", havingValue = "mock", matchIfMissing = true)
public class MockLLMClient implements LLMClient {

    private static final Logger log = LoggerFactory.getLogger(MockLLMClient.class);

    @Override
    public String chat(String prompt) {
        log.info("Mock LLM received prompt: {}", prompt);
        return "This is a MOCK answer. I received your context and question. The context provided mentioned... (simulated logic).";
    }
}
