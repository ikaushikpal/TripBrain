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
public class HotelTool {

    private final RetrievalHelper helper;

    @Tool(description = """
            Find hotels in a destination based on budget and preferences.
            Use when the user asks for hotel suggestions, stays, or accommodations.
            """)
    public List<String> findHotels(
            String destination,
            Double budget,
            String preferences // e.g. "luxury", "boutique", "near beach"
    ) {

        String query = String.format(
                "%s hotels %s %s %s",
                destination,
                preferences != null ? preferences : "",
                budget != null ? "under " + budget : "",
                "best areas to stay");

        return helper.search(query.trim(), 5);
    }
}