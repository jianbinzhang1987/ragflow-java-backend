package com.ragflow.backend.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Collections;
import java.util.List;

@Service
@ConditionalOnProperty(name = "llm.provider", havingValue = "openai")
public class OpenAiLLMClient implements LLMClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiLLMClient.class);

    private final RestClient restClient;

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

    static class Req {
        private String model;
        private double temperature;
        private List<Message> messages;

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
}
