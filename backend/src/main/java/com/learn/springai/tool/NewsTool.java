package com.learn.springai.tool;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class NewsTool {

    private final DocumentRetriever retriever;

    @Tool(description = """
            Get latest travel-related news, alerts, or updates for a destination.
            Use for safety, weather disruptions, or important events.
            """)
    public List<String> getTravelNews(String destination) {

        String query = destination + " travel news safety alerts weather events";

        return retriever.retrieve(new Query(query))
                .stream()
                .limit(5)
                .map(Document::getText)
                .toList();
    }
}
