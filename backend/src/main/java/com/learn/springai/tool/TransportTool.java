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
public class TransportTool {

    private final RetrievalHelper helper;

    @Tool(description = """
            Find transport options between locations including flights, trains, and local travel.
            Use for logistics and travel planning.
            """)
    public List<String> findTransport(
            String source,
            String destination) {

        String query = String.format(
                "%s to %s travel options flight cost time best way",
                source,
                destination);

        return helper.search(query, 5);
    }
}