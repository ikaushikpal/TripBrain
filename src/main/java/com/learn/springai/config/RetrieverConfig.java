package com.learn.springai.config;

import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import com.learn.springai.rag.WebSearchDocumentRetriever;

@Configuration
public class RetrieverConfig {

    @Value("${tavily.api-key}")
    public String TavilyApiKey;

    @Bean
    public DocumentRetriever documentRetriever(
            RestClient.Builder restClientBuilder) {

        System.out.println("API KEY in Config = " + TavilyApiKey); // ADD THIS
        return WebSearchDocumentRetriever.builder()
                .restClientBuilder(restClientBuilder)
                .apiKey(TavilyApiKey)
                .maxResults(10)
                .build();
    }
}