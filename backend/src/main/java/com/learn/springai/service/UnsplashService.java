package com.learn.springai.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.awt.image.BufferedImage;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.core.ParameterizedTypeReference;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;

@Service
@Slf4j
public class UnsplashService {

    private static final String DEFAULT_IMAGE = "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=1600&q=80";
    
    @Value("${unsplash.access-key}")
    private String accessKey;

    private final RestClient restClient;

    public UnsplashService() {
        this.restClient = RestClient.builder().build();
    }

    public String getPhotoUrl(String query) {
        if (query == null || query.isBlank()) {
            return DEFAULT_IMAGE;
        }
        try {
            String url = "https://api.unsplash.com/search/photos?page=1&per_page=1&query=" + URLEncoder.encode(query, "UTF-8");
            
            org.springframework.http.ResponseEntity<String> responseEntity = restClient.get()
                    .uri(url)
                    .header("Authorization", "Client-ID " + accessKey)
                    .retrieve()
                    .toEntity(String.class);

            if (responseEntity.getStatusCode().is2xxSuccessful() && responseEntity.getBody() != null) {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(responseEntity.getBody());
                if (root.has("results")) {
                    com.fasterxml.jackson.databind.JsonNode results = root.get("results");
                    if (results.isArray() && results.size() > 0) {
                        com.fasterxml.jackson.databind.JsonNode firstResult = results.get(0);
                        if (firstResult.has("urls")) {
                            com.fasterxml.jackson.databind.JsonNode urls = firstResult.get("urls");
                            if (urls.has("thumb")) {
                                String thumbUrl = urls.get("thumb").asText();
                                log.info("Resolved Unsplash photo thumb URL for: {} -> {}", query, thumbUrl);
                                return thumbUrl;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to resolve Unsplash photo from API for query: {}, falling back. Error: {}", query, e.getMessage());
        }
        return DEFAULT_IMAGE;
    }

    public byte[] downloadPhotoBytes(String urlStr) {
        try {
            byte[] rawBytes = restClient.get()
                    .uri(urlStr)
                    .retrieve()
                    .body(byte[].class);
            
            if (rawBytes == null) return null;

            // Compress the image to ensure it is not above 200KB
            if (rawBytes.length > 200 * 1024) {
                log.info("Compressing image from {} bytes to fit within 200KB", rawBytes.length);
                return compressImage(rawBytes, 200 * 1024);
            }
            return rawBytes;
        } catch (Exception e) {
            log.warn("Failed to download Unsplash image bytes using RestClient: {}", e.getMessage());
            return null;
        }
    }

    private byte[] compressImage(byte[] imageBytes, int maxSizeBytes) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (image == null) return imageBytes;

            float quality = 0.8f;
            byte[] compressed = imageBytes;
            while (quality > 0.1f) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                java.util.Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
                if (!writers.hasNext()) break;
                ImageWriter writer = writers.next();
                try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
                    writer.setOutput(ios);
                    ImageWriteParam param = writer.getDefaultWriteParam();
                    if (param.canWriteCompressed()) {
                        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                        param.setCompressionType(param.getCompressionTypes()[0]);
                        param.setCompressionQuality(quality);
                    }
                    writer.write(null, new IIOImage(image, null, null), param);
                } finally {
                    writer.dispose();
                }
                compressed = baos.toByteArray();
                if (compressed.length <= maxSizeBytes) {
                    log.info("Successfully compressed image to {} bytes at quality {}", compressed.length, quality);
                    return compressed;
                }
                quality -= 0.15f;
            }
            return compressed;
        } catch (Exception e) {
            log.warn("Failed to compress image, returning raw bytes: {}", e.getMessage());
            return imageBytes;
        }
    }
}
