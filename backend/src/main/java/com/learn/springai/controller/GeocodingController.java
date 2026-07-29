package com.learn.springai.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.learn.springai.dto.geocoding.PublicGeocodeResponse;
import com.learn.springai.service.GeocodingService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/geocoding")
@RequiredArgsConstructor
public class GeocodingController {

    private final GeocodingService geocodingService;

    @GetMapping("/search")
    public ResponseEntity<PublicGeocodeResponse> geocode(@RequestParam("query") String query) {
        PublicGeocodeResponse response = geocodingService.geocode(query);
        if (response != null) {
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.notFound().build();
    }
}
