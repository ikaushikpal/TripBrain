package com.learn.springai.controller;

import com.learn.springai.service.BackblazeStorageService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@RestController
public class PublicStaticController {

    private final BackblazeStorageService storageService;

    public PublicStaticController(BackblazeStorageService storageService) {
        this.storageService = storageService;
    }

    @GetMapping("/api/public-static/**")
    public ResponseEntity<Void> redirectToPresignedUrl(HttpServletRequest request) {
        // Extract file subpath after /api/public-static/
        String uri = request.getRequestURI();
        String subPath = uri.substring(uri.indexOf("/api/public-static/") + "/api/public-static/".length());
        
        try {
            String fileKey = "static/" + subPath;
            // Generate an inline presigned URL with the maximum possible expiry (7 days / 168 hours)
            String presignedUrl = storageService.generatePresignedInlineUrl(fileKey, Duration.ofDays(7));
            
            // Set Cache-Control header to cache the HTTP redirect in the browser for 7 days.
            // During this period, the browser will directly fetch from the B2 CDN/S3 URL 
            // without hitting the Spring Boot backend, completely eliminating outgoing traffic!
            CacheControl cacheControl = CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic();
            
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(presignedUrl))
                    .cacheControl(cacheControl)
                    .build();
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}
