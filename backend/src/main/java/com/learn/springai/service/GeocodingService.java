package com.learn.springai.service;

import java.util.List;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.learn.springai.dto.geocoding.PublicGeocodeResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeocodingService {

    private final RestClient.Builder restClientBuilder;

    @Cacheable(value = "searchResults", key = "'geocode-' + #query.toLowerCase()")
    public PublicGeocodeResponse geocode(String query) {
        log.info("Performing OSM geocoding lookup for: {}", query);
        try {
            RestClient client = restClientBuilder.build();
            PublicGeocodeResponse[] response = client.get()
                    .uri("https://nominatim.openstreetmap.org/search?q={query}&format=json&limit=1", query)
                    .header("User-Agent", "TripBrain/1.0 (contact@tripbrain.com)")
                    .retrieve()
                    .body(PublicGeocodeResponse[].class);

            if (response != null && response.length > 0) {
                return response[0];
            }
        } catch (Exception e) {
            log.error("OSM Geocoding failed for query: {}", query, e);
        }

        // Fallback for popular cities if OSM fails/times out
        String lowerQuery = query.toLowerCase();
        PublicGeocodeResponse fallback = new PublicGeocodeResponse();
        if (lowerQuery.contains("paris")) {
            fallback.setLat("48.8566");
            fallback.setLon("2.3522");
            fallback.setDisplay_name("Paris, France (Local Fallback)");
            return fallback;
        } else if (lowerQuery.contains("london")) {
            fallback.setLat("51.5074");
            fallback.setLon("-0.1278");
            fallback.setDisplay_name("London, United Kingdom (Local Fallback)");
            return fallback;
        } else if (lowerQuery.contains("tokyo")) {
            fallback.setLat("35.6762");
            fallback.setLon("139.6503");
            fallback.setDisplay_name("Tokyo, Japan (Local Fallback)");
            return fallback;
        } else if (lowerQuery.contains("mumbai")) {
            fallback.setLat("19.0760");
            fallback.setLon("72.8777");
            fallback.setDisplay_name("Mumbai, India (Local Fallback)");
            return fallback;
        } else if (lowerQuery.contains("goa")) {
            fallback.setLat("15.2993");
            fallback.setLon("74.1240");
            fallback.setDisplay_name("Goa, India (Local Fallback)");
            return fallback;
        } else if (lowerQuery.contains("new york")) {
            fallback.setLat("40.7128");
            fallback.setLon("-74.0060");
            fallback.setDisplay_name("New York, USA (Local Fallback)");
            return fallback;
        } else if (lowerQuery.contains("sydney")) {
            fallback.setLat("-33.8688");
            fallback.setLon("151.2093");
            fallback.setDisplay_name("Sydney, Australia (Local Fallback)");
            return fallback;
        } else if (lowerQuery.contains("delhi")) {
            fallback.setLat("28.6139");
            fallback.setLon("77.2090");
            fallback.setDisplay_name("Delhi, India (Local Fallback)");
            return fallback;
        } else if (lowerQuery.contains("rome")) {
            fallback.setLat("41.9028");
            fallback.setLon("12.4964");
            fallback.setDisplay_name("Rome, Italy (Local Fallback)");
            return fallback;
        }

        return null;
    }
}
