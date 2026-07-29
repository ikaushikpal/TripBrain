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
public class PlaceTool {

    private final RetrievalHelper helper;

    @Tool(description = """
            Find tourist attractions and places to visit.
            Use for sightseeing, activities, and itinerary planning.
            """)
    public List<String> findPlaces(
            String destination,
            String preferences // nightlife, culture, adventure
    ) {

        String query = String.format(
                "%s places to visit %s top attractions itinerary highlights",
                destination,
                preferences != null ? preferences : "");

        return helper.search(query.trim(), 6);
    }
}