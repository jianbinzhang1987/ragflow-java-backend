package com.ragflow.backend.llm;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface LLMClient {
    String chat(String prompt);

    /**
     * Stream chat response using SSE
     * 
     * @param prompt  the user prompt
     * @param emitter SSE emitter to send chunks
     */
    void chatStream(String prompt, SseEmitter emitter);
}
