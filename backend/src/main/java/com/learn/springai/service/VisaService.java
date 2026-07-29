package com.learn.springai.service;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.learn.springai.dto.countryVisa.CountryVisaStats;
import com.learn.springai.dto.countryVisa.VisaResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class VisaService {

    private final RestClient.Builder restClientBuilder;
    private static final String BASE_URL = "https://rough-sun-2523.fly.dev";

    @Cacheable(value = "visas", key = "#passportCode + '-' + #destinationCode")
    public VisaResponse getVisaInfo(String passportCode, String destinationCode) {
        log.info("Fetching visa info from external API: {} -> {}", passportCode, destinationCode);
        RestClient client = restClientBuilder.build();
        String url = BASE_URL + "/visa/" + passportCode.toUpperCase() + "/" + destinationCode.toUpperCase();
        return client.get()
                .uri(url)
                .retrieve()
                .body(VisaResponse.class);
    }

    @Cacheable(value = "visaStats", key = "#passportCode")
    public CountryVisaStats getVisaStats(String passportCode) {
        log.info("Fetching visa stats from external API for: {}", passportCode);
        RestClient client = restClientBuilder.build();
        String url = BASE_URL + "/country/" + passportCode.toUpperCase();
        return client.get()
                .uri(url)
                .retrieve()
                .body(CountryVisaStats.class);
    }
}
