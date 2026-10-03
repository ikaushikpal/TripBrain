package com.learn.springai.service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class UserProfileService {

    private final VectorStore userProfileVectorStore;
    private final ChatClient routerChatClient;
    private final com.learn.springai.config.LlmBulkheadManager llmBulkheadManager;

    public UserProfileService(
            @Qualifier("userProfileVectorStore") VectorStore userProfileVectorStore,
            @Qualifier("routerChatClient") ChatClient routerChatClient,
            com.learn.springai.config.LlmBulkheadManager llmBulkheadManager) {
        this.userProfileVectorStore = userProfileVectorStore;
        this.routerChatClient = routerChatClient;
        this.llmBulkheadManager = llmBulkheadManager;
    }

    @Async
    public void extractAndStorePreferences(String userId, String userMessage) {
        if (userMessage == null || userMessage.trim().length() < 10) {
            return;
        }

        try {
            log.info("Extracting user preferences asynchronously for user: {}", userId);
            String response = llmBulkheadManager.executeWithGoogle(() -> routerChatClient.prompt()
                    .system("<system_instructions>\n  <role>You are a user preference extraction assistant.</role>\n  <task>Extract any travel preferences, dietary rules, budget habits, or accommodation requests from the user message.</task>\n  <output_format>Return them as concise bullet points (starting with '- '), or respond with 'NONE' if no preference is mentioned.</output_format>\n</system_instructions>")
                    .user(userMessage)
                    .call()
                    .content());

            if (response == null || response.contains("NONE") || response.isBlank()) {
                return;
            }

            List<Document> documents = Arrays.stream(response.split("\n"))
                    .map(String::trim)
                    .filter(line -> !line.isBlank() && (line.startsWith("-") || line.startsWith("*")))
                    .map(fact -> {
                        String cleanedFact = fact.replaceAll("^[*\\-\\s]+", "");
                        return new Document(cleanedFact, Map.of("user_id", userId));
                    })
                    .toList();

            if (!documents.isEmpty()) {
                userProfileVectorStore.add(documents);
                log.info("Saved {} new user preference facts to Qdrant", documents.size());
            }
        } catch (Exception e) {
            log.error("Failed to extract or store user preferences", e);
        }
    }

    public List<String> getUserPreferences(String userId) {
        try {
            org.springframework.ai.vectorstore.SearchRequest request = org.springframework.ai.vectorstore.SearchRequest
                    .builder()
                    .query("")
                    .filterExpression("user_id == '" + userId + "'")
                    .topK(5)
                    .build();

            return userProfileVectorStore.similaritySearch(request)
                    .stream()
                    .map(Document::getText)
                    .toList();
        } catch (Exception e) {
            log.warn("Failed to retrieve user preferences from Qdrant: {}", e.getMessage());
            return List.of();
        }
    }
}
