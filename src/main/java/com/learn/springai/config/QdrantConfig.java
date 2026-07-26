package com.learn.springai.config;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;

import io.qdrant.client.grpc.Collections.Distance;
import io.qdrant.client.grpc.Collections.VectorParams;
import io.qdrant.client.grpc.Collections.PayloadSchemaType;
import io.qdrant.client.grpc.Collections.FloatIndexParams;

import io.qdrant.client.grpc.Points.CreateFieldIndexCollection;
import io.qdrant.client.grpc.Points.FieldType;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class QdrantConfig {

    private static final String COLLECTION_NAME = "pdf-lookup-app";

    @Value("${qdrant.host}")
    private String host;

    @Value("${qdrant.api-key}")
    private String apiKey;

    // ✅ Qdrant Client
    @Bean
    public QdrantClient qdrantClient() {
        return new QdrantClient(
                QdrantGrpcClient.newBuilder(host, 6334, true)
                        .withApiKey(apiKey)
                        .build());
    }

    // ✅ Create Collection
    private void createCollectionIfNotExists(
            QdrantClient client,
            EmbeddingModel embeddingModel) {
        try {
            client.createCollectionAsync(
                    COLLECTION_NAME,
                    VectorParams.newBuilder()
                            .setSize(embeddingModel.dimensions())
                            .setDistance(Distance.Cosine)
                            .build())
                    .get();

            System.out.println("✅ Collection created");

        } catch (Exception e) {
            System.out.println("ℹ️ Collection exists: " + e.getMessage());
        }
    }

    // ✅ Create Index (ONLY conversation_id)
    private void createIndexes(QdrantClient client) {

        createPayloadIndex(client, "conversation_id", FieldType.FieldTypeKeyword);
        // Optional metadata
        createPayloadIndex(client, "chunk_index", FieldType.FieldTypeInteger);
        createPayloadIndex(client, "page_number", FieldType.FieldTypeInteger);
    }

    private void createPayloadIndex(
            QdrantClient client,
            String fieldName,
            FieldType fieldType) {
        try {

            CreateFieldIndexCollection request = CreateFieldIndexCollection.newBuilder()
                    .setCollectionName(COLLECTION_NAME)
                    .setFieldName(fieldName)
                    .setFieldType(fieldType) // ✅ correct type
                    .build();

            client.createPayloadIndexAsync(request, null).get();

            System.out.println("✅ Index created: " + fieldName);

        } catch (Exception e) {
            System.out.println("ℹ️ Index exists [" + fieldName + "]: " + e.getMessage());
        }
    }

    // ✅ Vector Store
    @Bean
    @Primary
    public QdrantVectorStore pdfVectorStore(
            QdrantClient client,
            EmbeddingModel embeddingModel) {

        createCollectionIfNotExists(client, embeddingModel);
        createIndexes(client);

        return QdrantVectorStore.builder(client, embeddingModel)
                .collectionName(COLLECTION_NAME)
                .build();
    }
}