package com.learn.springai.service;

import java.util.Optional;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.learn.springai.model.Country;
import com.learn.springai.repository.CountryRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CountryService {

    private final CountryRepository repository;

    @Cacheable(value = "countries", key = "#input.toLowerCase()")
    public Country getCountry(String input) {

        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Country input cannot be empty");
        }

        String normalized = normalize(input);

        Optional<Country> byIso = repository.findByIso2IgnoreCase(normalized);
        if (byIso.isPresent()) {
            return byIso.get();
        }

        java.util.List<Country> ftsResults = repository.searchByName(normalized);
        if (!ftsResults.isEmpty()) {
            return ftsResults.get(0);
        }

        Country fallback = repository.findTopByNameIgnoreCaseContaining(normalized);
        if (fallback != null) {
            return fallback;
        }

        throw new RuntimeException("Country not found: " + input);
    }

    private String normalize(String input) {
        return input.toLowerCase()
                .replaceAll("[^a-z ]", "") // remove emojis/symbols
                .trim();
    }

    private String toFtsQuery(String input) {
        return input.replaceAll("\\s+", "* ") + "*";
    }
}