package com.learn.springai.service;

import java.util.List;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import com.learn.springai.dto.currency.CurrencyResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CurrencyService {

    private final RestClient.Builder restClientBuilder;

    private final List<String> urls = List.of(
            "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/",
            "https://latest.currency-api.pages.dev/v1/currencies/"
    );

    @Cacheable(value = "currencyRates", key = "#fromCurrency.toLowerCase()")
    public CurrencyResponse getCurrencyRates(String fromCurrency) {
        String baseCurrency = fromCurrency.toLowerCase();
        log.info("Fetching currency rates from external API for base: {}", baseCurrency);

        for (String url : urls) {
            try {
                RestClient client = restClientBuilder.build();
                String finalURL = url + baseCurrency + ".json";
                CurrencyResponse response = client.get()
                        .uri(finalURL)
                        .retrieve()
                        .body(CurrencyResponse.class);
                if (response != null && response.getCurrencies() != null) {
                    return response;
                }
            } catch (Exception e) {
                log.warn("Failed to fetch currency rates from {} for base {}", url, baseCurrency, e);
            }
        }
        throw new RuntimeException("Unable to fetch currency rates for base: " + fromCurrency);
    }
}
