package com.learn.springai.tool;

import java.util.List;
import java.util.Map;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.learn.springai.dto.currency.CurrencyResponse;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CurrencyTool {

    private final RestClient.Builder restClientBuilder;

    private List<String> urls = List.of("https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/",
            "https://latest.currency-api.pages.dev/v1/currencies/");

    @Tool(description = """
                    Get live currency conversion rate between two currencies.
                    Use when calculating trip budgets or converting costs.
            """)
    public String convertCurrency(
            @ToolParam(required = true, description = "Three-letter base currency code to convert from (e.g., 'INR', 'USD', 'EUR')") String fromCurrency,

            @ToolParam(required = true, description = "Three-letter target currency code to convert to (e.g., 'THB', 'JPY', 'GBP')") String toCurrency,

            @ToolParam(required = false, description = "Optional amount to convert. Defaults to 1.0 if not provided") Double amount) {

        fromCurrency = fromCurrency.toLowerCase();
        toCurrency = toCurrency.toLowerCase();

        if (amount == null) {
            amount = 1.0;
        }
        for (String url : urls) {
            RestClient client = restClientBuilder.build();
            String finalURL = url + fromCurrency + ".json";

            CurrencyResponse response = client.get()
                    .uri(finalURL)
                    .retrieve()
                    .body(CurrencyResponse.class);

            if (response == null || !response.getCurrencies().containsKey(fromCurrency)) {
                return "Unable to fetch currency data";
            }

            Map<String, Double> currencyMap = response.getCurrencies().get(fromCurrency);

            Double rate = currencyMap.get(toCurrency);
            Double convertedAmount = amount * rate;

            return String.format(
                    "%.2f %s = %.2f %s",
                    amount,
                    fromCurrency.toUpperCase(),
                    convertedAmount,
                    toCurrency.toUpperCase());
        }
        return "Unable to convert currency. Please try again";
    }

}