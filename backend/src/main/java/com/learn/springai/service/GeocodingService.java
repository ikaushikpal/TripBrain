package com.learn.springai.service;

import java.util.Map;
import java.util.Optional;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import com.learn.springai.dto.geocoding.PublicGeocodeResponse;
import com.learn.springai.model.GeocodedLocation;
import com.learn.springai.repository.GeocodedLocationRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeocodingService {

    private final RestClient.Builder restClientBuilder;
    private final GeocodedLocationRepository geocodedLocationRepository;

    @Transactional
    @Cacheable(value = "searchResults", key = "'geocode-' + #query.toLowerCase().trim()")
    public PublicGeocodeResponse geocode(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }

        String normalizedQuery = query.trim().toLowerCase();

        // 1. Check SQL Database Cache first
        Optional<GeocodedLocation> cachedOpt = geocodedLocationRepository.findBySearchQuery(normalizedQuery);
        if (cachedOpt.isPresent()) {
            GeocodedLocation cached = cachedOpt.get();
            log.info("[GeocodingService] SQL DB Cache HIT for query: '{}' -> Lat: {}, Lon: {}, Country: {}",
                    normalizedQuery, cached.getLat(), cached.getLon(), cached.getCountry());
            return PublicGeocodeResponse.builder()
                    .lat(cached.getLat())
                    .lon(cached.getLon())
                    .display_name(cached.getDisplayName())
                    .build();
        }

        // 2. Query OpenStreetMap (OSM) Nominatim API with addressdetails
        log.info("[GeocodingService] SQL DB Cache MISS. Calling OSM Nominatim API for: '{}'", query);
        try {
            RestClient client = restClientBuilder.build();
            PublicGeocodeResponse[] response = client.get()
                    .uri("https://nominatim.openstreetmap.org/search?q={query}&format=json&addressdetails=1&limit=1", query.trim())
                    .header("User-Agent", "TripBrain/1.0 (contact@tripbrain.com)")
                    .retrieve()
                    .body(PublicGeocodeResponse[].class);

            if (response != null && response.length > 0) {
                PublicGeocodeResponse res = response[0];
                saveToDatabase(normalizedQuery, res);
                return res;
            }
        } catch (Exception e) {
            log.error("[GeocodingService] OSM Geocoding request failed for query: '{}': {}", query, e.getMessage());
        }

        // 3. Fallback for common destinations if OSM is offline / rate-limited
        PublicGeocodeResponse fallback = resolveLocalFallback(normalizedQuery);
        if (fallback != null) {
            saveToDatabase(normalizedQuery, fallback);
            return fallback;
        }

        return null;
    }

    private void saveToDatabase(String searchQuery, PublicGeocodeResponse res) {
        try {
            String country = null;
            String countryCode = null;
            String city = null;
            String state = null;

            if (res.getAddress() != null) {
                Map<String, Object> addr = res.getAddress();
                if (addr.get("country") != null) country = addr.get("country").toString();
                if (addr.get("country_code") != null) countryCode = addr.get("country_code").toString().toUpperCase();
                if (addr.get("city") != null) city = addr.get("city").toString();
                else if (addr.get("town") != null) city = addr.get("town").toString();
                else if (addr.get("village") != null) city = addr.get("village").toString();
                if (addr.get("state") != null) state = addr.get("state").toString();
            }

            // If country not parsed from address object, extract from display_name trailing part
            if (country == null && res.getDisplay_name() != null) {
                String[] parts = res.getDisplay_name().split(",");
                if (parts.length > 0) {
                    country = parts[parts.length - 1].replaceAll("\\(Local Fallback\\)", "").trim();
                }
            }

            GeocodedLocation entity = geocodedLocationRepository.findBySearchQuery(searchQuery)
                    .orElse(GeocodedLocation.builder().searchQuery(searchQuery).build());

            entity.setDisplayName(res.getDisplay_name());
            entity.setLat(res.getLat());
            entity.setLon(res.getLon());
            entity.setCountry(country);
            entity.setCountryCode(countryCode);
            entity.setCity(city);
            entity.setState(state);

            geocodedLocationRepository.save(entity);
            log.info("[GeocodingService] Saved geocoded location to SQL DB for: '{}' (Country: {}, Code: {})",
                    searchQuery, country, countryCode);
        } catch (Exception e) {
            log.warn("[GeocodingService] Failed to persist geocoded location to SQL DB: {}", e.getMessage());
        }
    }

    private PublicGeocodeResponse resolveLocalFallback(String lowerQuery) {
        PublicGeocodeResponse fallback = new PublicGeocodeResponse();
        if (lowerQuery.contains("paris")) {
            fallback.setLat("48.8566");
            fallback.setLon("2.3522");
            fallback.setDisplay_name("Paris, Ile-de-France, France (Local Fallback)");
            return fallback;
        } else if (lowerQuery.contains("london")) {
            fallback.setLat("51.5074");
            fallback.setLon("-0.1278");
            fallback.setDisplay_name("London, Greater London, United Kingdom (Local Fallback)");
            return fallback;
        } else if (lowerQuery.contains("tokyo")) {
            fallback.setLat("35.6762");
            fallback.setLon("139.6503");
            fallback.setDisplay_name("Tokyo, Japan (Local Fallback)");
            return fallback;
        } else if (lowerQuery.contains("mumbai")) {
            fallback.setLat("19.0760");
            fallback.setLon("72.8777");
            fallback.setDisplay_name("Mumbai, Maharashtra, India (Local Fallback)");
            return fallback;
        } else if (lowerQuery.contains("goa")) {
            fallback.setLat("15.2993");
            fallback.setLon("74.1240");
            fallback.setDisplay_name("Goa, India (Local Fallback)");
            return fallback;
        } else if (lowerQuery.contains("new york")) {
            fallback.setLat("40.7128");
            fallback.setLon("-74.0060");
            fallback.setDisplay_name("New York, New York, United States (Local Fallback)");
            return fallback;
        } else if (lowerQuery.contains("sydney")) {
            fallback.setLat("-33.8688");
            fallback.setLon("151.2093");
            fallback.setDisplay_name("Sydney, New South Wales, Australia (Local Fallback)");
            return fallback;
        } else if (lowerQuery.contains("delhi")) {
            fallback.setLat("28.6139");
            fallback.setLon("77.2090");
            fallback.setDisplay_name("Delhi, India (Local Fallback)");
            return fallback;
        } else if (lowerQuery.contains("rome")) {
            fallback.setLat("41.9028");
            fallback.setLon("12.4964");
            fallback.setDisplay_name("Rome, Lazio, Italy (Local Fallback)");
            return fallback;
        } else if (lowerQuery.contains("digha")) {
            fallback.setLat("21.6266");
            fallback.setLon("87.5074");
            fallback.setDisplay_name("Digha, Purba Medinipur, West Bengal, India (Local Fallback)");
            return fallback;
        }
        return null;
    }
}
