package com.learn.springai.dto.currency;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class CurrencyResponse {

    private String date;

    // Holds dynamic currency objects like "inr", "usd", etc.
    private Map<String, Map<String, Double>> currencies = new HashMap<>();

    @JsonAnySetter
    public void addCurrency(String key, Object value) {
        if ("date".equals(key))
            return;

        if (value instanceof Map) {
            currencies.put(key, (Map<String, Double>) value);
        }
    }
}