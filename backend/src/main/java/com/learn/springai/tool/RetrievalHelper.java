package com.learn.springai.tool;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RetrievalHelper {

    private final DocumentRetriever retriever;

    @Cacheable(value = "searchResults", key = "#query")
    public List<String> search(String query, int limit) {
        return retriever.retrieve(new Query(query))
                .stream()
                .limit(limit)
                .map(Document::getText)
                .toList();
    }

    @Cacheable(value = "searchResults", key = "#query")
    public String searchOne(String query) {
        return retriever.retrieve(new Query(query))
                .stream()
                .map(Document::getText)
                .findFirst()
                .orElse("No relevant information found.");
    }
}
