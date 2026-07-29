package com.learn.springai.config;

import org.opensearch.client.opensearch.OpenSearchClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.opensearch.OpenSearchVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@ConditionalOnProperty(name = "spring.ai.vectorstore.type", havingValue = "opensearch")
public class OpenSearchConfig {

    @Value("${spring.ai.vectorstore.opensearch.index-name:pdf-lookup-app}")
    private String indexName;

    @Value("${spring.ai.vectorstore.opensearch.uris}")
    private String uris;

    @Value("${spring.ai.vectorstore.opensearch.username}")
    private String username;

    @Value("${spring.ai.vectorstore.opensearch.password}")
    private String password;

    @Bean
    @Primary
    public OpenSearchClient openSearchClient() {
        org.apache.http.impl.client.BasicCredentialsProvider credentialsProvider =
                new org.apache.http.impl.client.BasicCredentialsProvider();
        credentialsProvider.setCredentials(
                org.apache.http.auth.AuthScope.ANY,
                new org.apache.http.auth.UsernamePasswordCredentials(username, password)
        );

        java.net.URI uri = java.net.URI.create(uris);
        org.apache.http.HttpHost host = new org.apache.http.HttpHost(uri.getHost(), uri.getPort(), uri.getScheme());

        org.opensearch.client.RestClient restClient = org.opensearch.client.RestClient.builder(host)
                .setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder
                        .setDefaultCredentialsProvider(credentialsProvider)
                )
                .build();

        org.opensearch.client.json.jackson.JacksonJsonpMapper mapper = new org.opensearch.client.json.jackson.JacksonJsonpMapper();
        org.opensearch.client.transport.rest_client.RestClientTransport transport = 
                new org.opensearch.client.transport.rest_client.RestClientTransport(restClient, mapper);

        return new OpenSearchClient(transport);
    }

    @Bean
    @Primary
    public OpenSearchVectorStore pdfVectorStore(
            OpenSearchClient client,
            EmbeddingModel embeddingModel) {
        return OpenSearchVectorStore.builder(client, embeddingModel)
                .index(indexName)
                .initializeSchema(true)
                .build();
    }

    @Bean("userProfileVectorStore")
    public OpenSearchVectorStore userProfileVectorStore(
            OpenSearchClient client,
            EmbeddingModel embeddingModel) {
        return OpenSearchVectorStore.builder(client, embeddingModel)
                .index("user-profiles")
                .initializeSchema(true)
                .build();
    }
}
