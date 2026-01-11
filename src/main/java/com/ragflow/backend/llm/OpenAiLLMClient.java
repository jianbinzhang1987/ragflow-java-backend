package com.ragflow.backend.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.List;

@Service
@ConditionalOnProperty(name = "llm.provider", havingValue = "openai")
public class OpenAiLLMClient implements LLMClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiLLMClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Value("${llm.base-url}")
    private String baseUrl;

    @Value("${llm.api-key}")
    private String apiKey;

    @Value("${llm.model}")
    private String model;

    @Value("${llm.temperature:0.7}")
    private double temperature;

    public OpenAiLLMClient(RestClient.Builder builder) {
        this.restClient = builder.build();
        this.objectMapper = new ObjectMapper();
        // Ignore unknown properties in JSON response
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public String chat(String prompt) {
        Req req = new Req();
        req.setModel(model);
        req.setTemperature(temperature);
        req.setMessages(Collections.singletonList(new Message("user", prompt)));

        try {
            Resp resp = restClient.post()
                    .uri(baseUrl + "/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(req)
                    .retrieve()
                    .body(Resp.class);

            if (resp != null && resp.getChoices() != null && !resp.getChoices().isEmpty()) {
                return resp.getChoices().get(0).getMessage().getContent();
            }
            return "Error: No response from LLM";
        } catch (Exception e) {
            log.error("LLM call failed", e);
            return "Error calling LLM: " + e.getMessage();
        }
    }

    @Override
    public void chatStream(String prompt, SseEmitter emitter) {
        Req req = new Req();
        req.setModel(model);
        req.setTemperature(temperature);
        req.setMessages(Collections.singletonList(new Message("user", prompt)));
        req.setStream(true);

        try {
            restClient.post()
                    .uri(baseUrl + "/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(req)
                    .exchange((request, response) -> {
                        try (InputStream is = response.getBody();
                                BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {

                            String line;
                            boolean completed = false;
                            while ((line = reader.readLine()) != null) {
                                if (completed) {
                                    break;
                                }
                                String trimmed = line.trim();
                                if (trimmed.isEmpty() || trimmed.startsWith("event:")) {
                                    continue;
                                }

                                String data = trimmed;
                                if (trimmed.startsWith("data:")) {
                                    data = trimmed.substring(5).trim();
                                }

                                if (data.isEmpty()) {
                                    continue;
                                }

                                if ("[DONE]".equals(data)) {
                                    emitter.send(SseEmitter.event().name("done").data(""));
                                    emitter.complete();
                                    completed = true;
                                    break;
                                }

                                try {
                                    StreamResp chunk = objectMapper.readValue(data, StreamResp.class);
                                    if (chunk.getChoices() != null && !chunk.getChoices().isEmpty()) {
                                        StreamChoice choice = chunk.getChoices().get(0);
                                        if (choice.getDelta() != null && choice.getDelta().getContent() != null
                                                && !choice.getDelta().getContent().isEmpty()) {
                                            emitter.send(SseEmitter.event()
                                                    .name("message")
                                                    .data(choice.getDelta().getContent()));
                                        }
                                        if (choice.getFinish_reason() != null
                                                && !choice.getFinish_reason().isEmpty()) {
                                            emitter.send(SseEmitter.event().name("done").data(""));
                                            emitter.complete();
                                            completed = true;
                                            break;
                                        }
                                    }
                                } catch (Exception e) {
                                    log.warn("Failed to parse chunk: {}, error: {}", data, e.getMessage());
                                }
                            }
                        } catch (Exception e) {
                            log.error("Error reading stream", e);
                            emitter.completeWithError(e);
                        }
                        return null;
                    });
        } catch (Exception e) {
            log.error("LLM stream call failed", e);
            try {
                emitter.send(SseEmitter.event().name("error").data("Error: " + e.getMessage()));
                emitter.complete();
            } catch (Exception ex) {
                emitter.completeWithError(ex);
            }
        }
    }

    static class Req {
        private String model;
        private double temperature;
        private List<Message> messages;
        private boolean stream = false;

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public double getTemperature() {
            return temperature;
        }

        public void setTemperature(double temperature) {
            this.temperature = temperature;
        }

        public List<Message> getMessages() {
            return messages;
        }

        public void setMessages(List<Message> messages) {
            this.messages = messages;
        }

        public boolean isStream() {
            return stream;
        }

        public void setStream(boolean stream) {
            this.stream = stream;
        }
    }

    static class Message {
        private String role;
        private String content;

        public Message() {
        }

        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }

    static class Resp {
        private List<Choice> choices;

        public List<Choice> getChoices() {
            return choices;
        }

        public void setChoices(List<Choice> choices) {
            this.choices = choices;
        }

        static class Choice {
            private Message message;

            public Message getMessage() {
                return message;
            }

            public void setMessage(Message message) {
                this.message = message;
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class StreamResp {
        private List<StreamChoice> choices;

        public List<StreamChoice> getChoices() {
            return choices;
        }

        public void setChoices(List<StreamChoice> choices) {
            this.choices = choices;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class StreamChoice {
        private Delta delta;
        private String finish_reason;

        public Delta getDelta() {
            return delta;
        }

        public void setDelta(Delta delta) {
            this.delta = delta;
        }

        public String getFinish_reason() {
            return finish_reason;
        }

        public void setFinish_reason(String finish_reason) {
            this.finish_reason = finish_reason;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Delta {
        private String role;
        private String content;

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }
}
