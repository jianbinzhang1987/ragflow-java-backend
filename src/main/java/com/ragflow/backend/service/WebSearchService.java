package com.ragflow.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Web search service using external search API (e.g., Brave Search, SerpAPI, or
 * custom MCP)
 */
@Service
public class WebSearchService {

    private static final Logger log = LoggerFactory.getLogger(WebSearchService.class);

    @Value("${websearch.enabled:false}")
    private boolean enabled;

    @Value("${websearch.api-url:}")
    private String apiUrl;

    @Value("${websearch.api-key:}")
    private String apiKey;

    @Value("${websearch.provider:brave}")
    private String provider;

    private final RestTemplate restTemplate = new RestTemplate();

    public static class SearchResultItem {
        private String title;
        private String url;
        private String snippet;

        public SearchResultItem(String title, String url, String snippet) {
            this.title = title;
            this.url = url;
            this.snippet = snippet;
        }

        public String getTitle() {
            return title;
        }

        public String getUrl() {
            return url;
        }

        public String getSnippet() {
            return snippet;
        }
    }

    public boolean isEnabled() {
        return enabled && apiUrl != null && !apiUrl.isEmpty();
    }

    public List<SearchResultItem> search(String query, int maxResults) {
        List<SearchResultItem> results = new ArrayList<>();

        if (!isEnabled()) {
            log.warn("Web search is not enabled or not configured");
            return results;
        }

        try {
            log.info("Performing web search for: {}", query);

            if ("brave".equalsIgnoreCase(provider)) {
                return searchBrave(query, maxResults);
            } else if ("serpapi".equalsIgnoreCase(provider)) {
                return searchSerpApi(query, maxResults);
            } else {
                // Generic API fallback
                return searchGeneric(query, maxResults);
            }
        } catch (Exception e) {
            log.error("Web search failed: {}", e.getMessage(), e);
            return results;
        }
    }

    @SuppressWarnings("unchecked")
    private List<SearchResultItem> searchBrave(String query, int maxResults) {
        List<SearchResultItem> results = new ArrayList<>();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", "application/json");
        headers.set("X-Subscription-Token", apiKey);

        String url = apiUrl + "?q=" + java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8)
                + "&count=" + maxResults;

        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity,
                    (Class<Map<String, Object>>) (Class<?>) Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                Object webObj = body.get("web");
                if (webObj instanceof Map) {
                    Object resultsObj = ((Map<?, ?>) webObj).get("results");
                    if (resultsObj instanceof List) {
                        for (Object item : (List<?>) resultsObj) {
                            if (item instanceof Map) {
                                Map<?, ?> itemMap = (Map<?, ?>) item;
                                String title = getStringValue(itemMap, "title");
                                String itemUrl = getStringValue(itemMap, "url");
                                String snippet = getStringValue(itemMap, "description");
                                results.add(new SearchResultItem(title, itemUrl, snippet));
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Brave search error: {}", e.getMessage());
        }

        return results;
    }

    @SuppressWarnings("unchecked")
    private List<SearchResultItem> searchSerpApi(String query, int maxResults) {
        List<SearchResultItem> results = new ArrayList<>();

        String url = apiUrl + "?q=" + java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8)
                + "&api_key=" + apiKey + "&num=" + maxResults;

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.GET, null,
                    (Class<Map<String, Object>>) (Class<?>) Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Object organicResults = response.getBody().get("organic_results");
                if (organicResults instanceof List) {
                    for (Object item : (List<?>) organicResults) {
                        if (item instanceof Map) {
                            Map<?, ?> itemMap = (Map<?, ?>) item;
                            String title = getStringValue(itemMap, "title");
                            String itemUrl = getStringValue(itemMap, "link");
                            String snippet = getStringValue(itemMap, "snippet");
                            results.add(new SearchResultItem(title, itemUrl, snippet));
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("SerpAPI search error: {}", e.getMessage());
        }

        return results;
    }

    @SuppressWarnings("unchecked")
    private List<SearchResultItem> searchGeneric(String query, int maxResults) {
        List<SearchResultItem> results = new ArrayList<>();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (apiKey != null && !apiKey.isEmpty()) {
            headers.set("Authorization", "Bearer " + apiKey);
        }

        Map<String, Object> requestBody = Map.of(
                "query", query,
                "max_results", maxResults);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    apiUrl, HttpMethod.POST, entity,
                    (Class<Map<String, Object>>) (Class<?>) Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Object resultsObj = response.getBody().get("results");
                if (resultsObj instanceof List) {
                    for (Object item : (List<?>) resultsObj) {
                        if (item instanceof Map) {
                            Map<?, ?> itemMap = (Map<?, ?>) item;
                            String title = getStringValue(itemMap, "title");
                            String itemUrl = getStringValue(itemMap, "url");
                            String snippet = getStringValue(itemMap, "snippet");
                            if (snippet.isEmpty()) {
                                snippet = getStringValue(itemMap, "content");
                            }
                            results.add(new SearchResultItem(title, itemUrl, snippet));
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Generic search error: {}", e.getMessage());
        }

        return results;
    }

    private String getStringValue(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value != null ? String.valueOf(value) : "";
    }
}
