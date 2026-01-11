package com.ragflow.backend.embedding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(name = "embedding.provider", havingValue = "openai")
public class OpenAiEmbeddingClient implements EmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiEmbeddingClient.class);

    private final RestClient restClient;

    @Value("${embedding.base-url}")
    private String baseUrl;

    @Value("${embedding.api-key}")
    private String apiKey;

    @Value("${embedding.model}")
    private String modelName;

    @Value("${embedding.dimension:1536}")
    private int dimension;

    public OpenAiEmbeddingClient(RestClient.Builder builder) {
        this.restClient = builder.build();
    }

    @Override
    public float[] embed(String text) {
        return embedBatch(Collections.singletonList(text)).get(0);
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        Req req = new Req();
        req.setModel(modelName);
        req.setInput(texts);

        try {
            Resp resp = restClient.post()
                    .uri(baseUrl + "/embeddings")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(req)
                    .retrieve()
                    .body(Resp.class);

            if (resp == null || resp.getData() == null) {
                throw new RuntimeException("Empty response from OpenAI Embedding API");
            }

            return resp.getData().stream()
                    .map(d -> {
                        float[] f = new float[d.getEmbedding().size()];
                        for (int i = 0; i < d.getEmbedding().size(); i++)
                            f[i] = d.getEmbedding().get(i).floatValue();
                        return f;
                    })
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Embedding failed", e);
            throw new RuntimeException("Embedding failed: " + e.getMessage());
        }
    }

    @Override
    public int getDimension() {
        return dimension;
    }

    static class Req {
        private String model;
        private List<String> input;

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public List<String> getInput() {
            return input;
        }

        public void setInput(List<String> input) {
            this.input = input;
        }
    }

    static class Resp {
        private List<DataItem> data;

        public List<DataItem> getData() {
            return data;
        }

        public void setData(List<DataItem> data) {
            this.data = data;
        }

        static class DataItem {
            private List<Double> embedding;
            private int index;

            public List<Double> getEmbedding() {
                return embedding;
            }

            public void setEmbedding(List<Double> embedding) {
                this.embedding = embedding;
            }

            public int getIndex() {
                return index;
            }

            public void setIndex(int index) {
                this.index = index;
            }
        }
    }
}
