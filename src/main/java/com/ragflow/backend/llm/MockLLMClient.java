package com.ragflow.backend.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
@ConditionalOnProperty(name = "llm.provider", havingValue = "mock", matchIfMissing = true)
public class MockLLMClient implements LLMClient {

    private static final Logger log = LoggerFactory.getLogger(MockLLMClient.class);

    @Override
    public String chat(String prompt) {
        log.info("Mock LLM received prompt: {}", prompt);
        return "This is a MOCK answer. I received your context and question. The context provided mentioned... (simulated logic).";
    }

    @Override
    public void chatStream(String prompt, SseEmitter emitter) {
        log.info("Mock LLM stream received prompt: {}", prompt);
        String response = "这是一个模拟的流式响应。我收到了你的问题和上下文。让我逐字为你展示答案的效果。";

        new Thread(() -> {
            try {
                for (char c : response.toCharArray()) {
                    emitter.send(SseEmitter.event().name("message").data(String.valueOf(c)));
                    Thread.sleep(50); // Simulate typing delay
                }
                emitter.send(SseEmitter.event().name("done").data(""));
                emitter.complete();
            } catch (Exception e) {
                log.error("Mock stream error", e);
                emitter.completeWithError(e);
            }
        }).start();
    }
}
