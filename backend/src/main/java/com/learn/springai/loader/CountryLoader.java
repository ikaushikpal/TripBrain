package com.learn.springai.loader;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.learn.springai.model.Country;
import com.learn.springai.repository.CountryRepository;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class CountryLoader {

    private final CountryRepository repository;
    private final RestClient.Builder restClientBuilder;

    private static final String URL = "https://restcountries.com/v3.1/all?fields=name,cca2,cca3,currencies,capital,flag,timezones,region,subregion";

    @PostConstruct
    public void loadCountries() {
        if (repository.count() > 0) {
            log.info("Countries already loaded. Skipping...");
            return;
        }

        List<Country> countries = new ArrayList<>();
        try {
            RestClient client = restClientBuilder.build();

            // The restcountries.com v3.1 API is deprecated and returns an error object starting with '{'.
            // Let's wrap this in a safe block. If it fails, we fall back to static популяр list.
            Object rawBody = client.get()
                    .uri(URL)
                    .retrieve()
                    .body(Object.class);

            if (rawBody instanceof List) {
                List<Map<String, Object>> response = (List<Map<String, Object>>) rawBody;
                for (Map<String, Object> c : response) {
                    try {
                        Country country = new Country();
                        Map<String, Object> nameMap = (Map<String, Object>) c.get("name");
                        country.setName((String) nameMap.get("common"));
                        country.setIso2((String) c.get("cca2"));
                        country.setIso3((String) c.get("cca3"));

                        Map<String, Object> currencies = (Map<String, Object>) c.get("currencies");
                        if (currencies != null && !currencies.isEmpty()) {
                            String currency = currencies.keySet().iterator().next();
                            country.setCurrency(currency);
                        }

                        List<String> capital = (List<String>) c.get("capital");
                        if (capital != null && !capital.isEmpty()) {
                            country.setCapital(capital.get(0));
                        }

                        country.setFlag((String) c.get("flag"));

                        List<String> timezones = (List<String>) c.get("timezones");
                        if (timezones != null && !timezones.isEmpty()) {
                            country.setTimezone(timezones.get(0));
                        }

                        country.setRegion((String) c.get("region"));
                        country.setSubregion((String) c.get("subregion"));
                        countries.add(country);
                    } catch (Exception e) {
                        // skip single country parsing error
                    }
                }
            } else {
                log.warn("RestCountries API returned non-list response (possibly deprecated). Falling back to static country list.");
                countries = getFallbackCountries();
            }
        } catch (Exception e) {
            log.warn("Failed to fetch from RestCountries API: {}. Falling back to static country list.", e.getMessage());
            countries = getFallbackCountries();
        }

        if (!countries.isEmpty()) {
            repository.saveAll(countries);
            log.info("Loaded {} countries into DB", countries.size());
        } else {
            log.error("No countries to save");
        }
    }

    private List<Country> getFallbackCountries() {
        List<Country> countries = new ArrayList<>();
        countries.add(createCountry("India", "IN", "IND", "INR", "New Delhi", "🇮🇳", "UTC+05:30", "Asia", "Southern Asia"));
        countries.add(createCountry("Thailand", "TH", "THA", "THB", "Bangkok", "🇹🇭", "UTC+07:00", "Asia", "South-Eastern Asia"));
        countries.add(createCountry("United States", "US", "USA", "USD", "Washington, D.C.", "🇺🇸", "UTC-05:00", "Americas", "North America"));
        countries.add(createCountry("Japan", "JP", "JPN", "JPY", "Tokyo", "🇯🇵", "UTC+09:00", "Asia", "Eastern Asia"));
        countries.add(createCountry("Singapore", "SG", "SGP", "SGD", "Singapore", "🇸🇬", "UTC+08:00", "Asia", "South-Eastern Asia"));
        countries.add(createCountry("United Kingdom", "GB", "GBR", "GBP", "London", "🇬🇧", "UTC+00:00", "Europe", "Northern Europe"));
        countries.add(createCountry("France", "FR", "FRA", "EUR", "Paris", "🇫🇷", "UTC+01:00", "Europe", "Western Europe"));
        countries.add(createCountry("Germany", "DE", "DEU", "EUR", "Berlin", "🇩🇪", "UTC+01:00", "Europe", "Western Europe"));
        countries.add(createCountry("United Arab Emirates", "AE", "ARE", "AED", "Abu Dhabi", "🇦🇪", "UTC+04:00", "Asia", "Western Asia"));
        countries.add(createCountry("Australia", "AU", "AUS", "AUD", "Canberra", "🇦🇺", "UTC+10:00", "Oceania", "Australia and New Zealand"));
        countries.add(createCountry("Canada", "CA", "CAN", "CAD", "Ottawa", "🇨🇦", "UTC-05:00", "Americas", "North America"));
        countries.add(createCountry("Spain", "ES", "ESP", "EUR", "Madrid", "🇪🇸", "UTC+01:00", "Europe", "Southern Europe"));
        countries.add(createCountry("Italy", "IT", "ITA", "EUR", "Rome", "🇮🇹", "UTC+01:00", "Europe", "Southern Europe"));
        countries.add(createCountry("Switzerland", "CH", "CHE", "CHF", "Bern", "🇨🇭", "UTC+01:00", "Europe", "Western Europe"));
        countries.add(createCountry("Maldives", "MV", "MDV", "MVR", "Malé", "🇲🇻", "UTC+05:00", "Asia", "Southern Asia"));
        countries.add(createCountry("Malaysia", "MY", "MYS", "MYR", "Kuala Lumpur", "🇲🇾", "UTC+08:00", "Asia", "South-Eastern Asia"));
        countries.add(createCountry("Indonesia", "ID", "IDN", "IDR", "Jakarta", "🇮🇩", "UTC+07:00", "Asia", "South-Eastern Asia"));
        return countries;
    }

    private Country createCountry(String name, String iso2, String iso3, String currency, String capital, String flag, String timezone, String region, String subregion) {
        Country c = new Country();
        c.setName(name);
        c.setIso2(iso2);
        c.setIso3(iso3);
        c.setCurrency(currency);
        c.setCapital(capital);
        c.setFlag(flag);
        c.setTimezone(timezone);
        c.setRegion(region);
        c.setSubregion(subregion);
        return c;
    }
}
