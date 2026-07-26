package com.learn.springai.rag;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

public class WebSearchDocumentRetriever implements DocumentRetriever {

    private static final Logger logger = LoggerFactory.getLogger(WebSearchDocumentRetriever.class);

    private static final String TAVILY_BASE_URL = "https://api.tavily.com/search";
    private static final int DEFAULT_RESULT_LIMIT = 10;
    private static final int MAX_QUERY_LENGTH = 380;

    private final int resultLimit;
    private final RestClient restClient;

    public WebSearchDocumentRetriever(RestClient.Builder clientBuilder, int resultLimit, String apiKey) {

        this.restClient = clientBuilder
                .requestInterceptor((request, body, execution) -> {
                    logger.debug("==== REQUEST ====");
                    logger.debug("Headers: {}", request.getHeaders());
                    logger.debug("Body: {}", new String(body));
                    return execution.execute(request, body);
                })
                .baseUrl(TAVILY_BASE_URL)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();

        if (resultLimit <= 0) {
            throw new IllegalArgumentException("resultLimit must be greater than 0");
        }

        this.resultLimit = resultLimit;
    }

    @Override
    public List<Document> retrieve(Query query) {

        String originalQuery = query.text();
        logger.info("Processing query (raw): {}", originalQuery);

        // 🔥 CORE FIX
        String finalQuery = optimizeQuery(originalQuery);

        logger.info("Final search query: {}", finalQuery);

        TavilyResponsePayload response = restClient.post()
                .uri("")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(new TavilyRequestPayload(finalQuery, "basic", resultLimit))
                .retrieve()
                .body(TavilyResponsePayload.class);

        if (response == null || CollectionUtils.isEmpty(response.results())) {
            return List.of();
        }

        List<Document> docs = new ArrayList<>();

        for (TavilyResponsePayload.Hit hit : response.results()) {
            docs.add(Document.builder()
                    .text(hit.content())
                    .metadata("title", hit.title())
                    .metadata("url", hit.url())
                    .score(hit.score())
                    .build());
        }

        return docs;
    }

    // ─────────────────────────────────────────────
    // 🔥 QUERY OPTIMIZATION (MAIN FIX)
    // ─────────────────────────────────────────────

    private String optimizeQuery(String input) {

        if (input == null || input.isBlank()) {
            return "";
        }

        // ✅ If already small & clean → keep it
        if (input.length() <= 350 && !input.contains("Trip Details")) {
            return input;
        }

        String lower = input.toLowerCase();
        StringBuilder q = new StringBuilder();

        // Extract high-signal fields
        extractAndAppend(lower, "destination:", q);
        extractAndAppend(lower, "must visit places:", q);
        extractAndAppend(lower, "vacation styles:", q);
        extractAndAppend(lower, "interests:", q);
        extractAndAppend(lower, "budget preference:", q);
        extractAndAppend(lower, "max budget:", q);

        // Add strong generic keywords
        q.append(" itinerary travel guide");

        String result = q.toString().trim();

        // 🚨 Hard safety limit (Tavily = 400 max)
        return result.length() > MAX_QUERY_LENGTH
                ? result.substring(0, MAX_QUERY_LENGTH)
                : result;
    }

    private void extractAndAppend(String text, String key, StringBuilder out) {

        int idx = text.indexOf(key);
        if (idx == -1)
            return;

        int end = text.indexOf("\n", idx);
        if (end == -1)
            end = text.length();

        String value = text.substring(idx + key.length(), end).trim();

        if (!value.isEmpty()) {
            out.append(cleanValue(value)).append(" ");
        }
    }

    // Clean noise like commas, symbols, etc.
    private String cleanValue(String value) {
        return value
                .replace(",", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    // ─────────────────────────────────────────────
    // DTOs
    // ─────────────────────────────────────────────

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record TavilyRequestPayload(
            String query,
            String searchDepth,
            int maxResults) {
    }

    record TavilyResponsePayload(List<Hit> results) {
        record Hit(String title, String url, String content, Double score) {
        }
    }

    // ─────────────────────────────────────────────
    // BUILDER
    // ─────────────────────────────────────────────

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private RestClient.Builder clientBuilder;
        private int resultLimit = DEFAULT_RESULT_LIMIT;
        private String apiKey;

        private Builder() {
        }

        public Builder restClientBuilder(RestClient.Builder clientBuilder) {
            this.clientBuilder = clientBuilder;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder maxResults(int maxResults) {
            if (maxResults <= 0) {
                throw new IllegalArgumentException("maxResults must be greater than 0");
            }
            this.resultLimit = maxResults;
            return this;
        }

        public WebSearchDocumentRetriever build() {
            return new WebSearchDocumentRetriever(clientBuilder, resultLimit, apiKey);
        }
    }
}