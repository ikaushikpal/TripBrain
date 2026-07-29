package com.learn.springai.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.learn.springai.model.Country;
import com.learn.springai.service.CountryService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CountryTool {

    private final CountryService service;

    @Tool(description = """
            Get country details like ISO code, currency, capital and region.
            """)
    public String getCountryInfo(String input) {

        try {
            Country c = service.getCountry(input);

            return String.format("""
                    🌍 Country Info

                    Name: %s
                    ISO2: %s
                    ISO3: %s
                    Currency: %s
                    Capital: %s
                    Region: %s
                    Timezone: %s
                    """,
                    c.getName(),
                    c.getIso2(),
                    c.getIso3(),
                    c.getCurrency(),
                    c.getCapital(),
                    c.getRegion(),
                    c.getTimezone());

        } catch (Exception e) {
            return "Country not found. Please try a valid country name.";
        }
    }
}
