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
public class RestaurantTool {

    private final RetrievalHelper helper;

    @Tool(description = """
            Find restaurants, cafes, or food experiences.
            Use when user asks about food, dining, or cuisine.
            """)
    public List<String> findRestaurants(
            String destination,
            String diningStyle // street food, fine dining, rooftop
    ) {

        String query = String.format(
                "%s best restaurants %s food places must try",
                destination,
                diningStyle != null ? diningStyle : "");

        return helper.search(query.trim(), 5);
    }
}
