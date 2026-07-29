package com.learn.springai.tool;

import java.util.List;
import java.util.Map;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import com.learn.springai.dto.currency.CurrencyResponse;
import com.learn.springai.service.CurrencyService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CurrencyTool {

    private final CurrencyService currencyService;

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

        try {
            CurrencyResponse response = currencyService.getCurrencyRates(fromCurrency);

            if (response == null || !response.getCurrencies().containsKey(fromCurrency)) {
                return "Unable to fetch currency data";
            }

            Map<String, Double> currencyMap = response.getCurrencies().get(fromCurrency);

            Double rate = currencyMap.get(toCurrency);
            if (rate == null) {
                return "Target currency rate not found: " + toCurrency.toUpperCase();
            }
            Double convertedAmount = amount * rate;

            return String.format(
                    "%.2f %s = %.2f %s",
                    amount,
                    fromCurrency.toUpperCase(),
                    convertedAmount,
                    toCurrency.toUpperCase());
        } catch (Exception e) {
            return "Unable to convert currency. Please try again";
        }
    }

}