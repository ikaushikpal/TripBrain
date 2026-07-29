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
public class WeatherTool {

    private final RetrievalHelper helper;

    @Tool(description = """
            Get weather forecast and seasonal conditions.
            Use for planning activities and packing.
            """)
    public String getWeather(
            String destination,
            String month // optional: "August"
    ) {

        String query = String.format(
                "%s weather %s forecast temperature rain conditions",
                destination,
                month != null ? month : "");

        return helper.searchOne(query.trim());
    }
}