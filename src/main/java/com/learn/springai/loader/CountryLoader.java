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

        try {
            RestClient client = restClientBuilder.build();

            List<Map<String, Object>> response = client.get()
                    .uri(URL)
                    .retrieve()
                    .body(List.class);

            if (response == null) {
                log.error("Failed to fetch countries");
                return;
            }

            List<Country> countries = new ArrayList<>();

            for (Map<String, Object> c : response) {

                try {
                    Country country = new Country();

                    // name
                    Map<String, Object> nameMap = (Map<String, Object>) c.get("name");
                    country.setName((String) nameMap.get("common"));

                    // ISO codes
                    country.setIso2((String) c.get("cca2"));
                    country.setIso3((String) c.get("cca3"));

                    // currency (take first)
                    Map<String, Object> currencies = (Map<String, Object>) c.get("currencies");
                    if (currencies != null && !currencies.isEmpty()) {
                        String currency = currencies.keySet().iterator().next();
                        country.setCurrency(currency);
                    }

                    // capital
                    List<String> capital = (List<String>) c.get("capital");
                    if (capital != null && !capital.isEmpty()) {
                        country.setCapital(capital.get(0));
                    }

                    // flag
                    country.setFlag((String) c.get("flag"));

                    // timezone
                    List<String> timezones = (List<String>) c.get("timezones");
                    if (timezones != null && !timezones.isEmpty()) {
                        country.setTimezone(timezones.get(0));
                    }

                    // region
                    country.setRegion((String) c.get("region"));
                    country.setSubregion((String) c.get("subregion"));

                    countries.add(country);

                } catch (Exception e) {
                    log.warn("Skipping country due to parsing error: {}", c);
                }
            }

            repository.saveAll(countries);

            log.info("Loaded {} countries into DB", countries.size());

        } catch (Exception e) {
            log.error("Failed to load countries", e);
        }
    }
}
