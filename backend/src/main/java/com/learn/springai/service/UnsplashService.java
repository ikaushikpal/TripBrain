package com.learn.springai.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.net.URLEncoder;
import java.util.Iterator;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.http.ResponseEntity;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;

@Service
@Slf4j
public class UnsplashService {

    private static final String DEFAULT_IMAGE = "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=1600&q=80";

    @Value("${unsplash.access-key}")
    private String accessKey;

    private final RestClient restClient;

    public UnsplashService(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    public String getPhotoUrl(String query) {
        if (query == null || query.isBlank()) {
            return DEFAULT_IMAGE;
        }
        String rawBody = null;
        try {
            String url = "https://api.unsplash.com/search/photos?page=1&per_page=1&query="
                    + URLEncoder.encode(query, "UTF-8");

            ResponseEntity<String> responseEntity = restClient.get()
                    .uri(url)
                    .header("Authorization", "Client-ID " + accessKey)
                    .header("Accept-Encoding", "identity")
                    .retrieve()
                    .toEntity(String.class);

            if (responseEntity.getStatusCode().is2xxSuccessful() && responseEntity.getBody() != null) {
                rawBody = responseEntity.getBody();
                String body = rawBody.trim();
                if (body.startsWith("{") && body.endsWith("}")) {
                    ObjectMapper mapper = new ObjectMapper();
                    SearchResponse searchResponse = mapper.readValue(body, SearchResponse.class);
                    if (searchResponse != null && searchResponse.getResults() != null
                            && !searchResponse.getResults().isEmpty()) {
                        Photo firstPhoto = searchResponse.getResults().get(0);
                        if (firstPhoto.getUrls() != null && firstPhoto.getUrls().getSmall() != null) {
                            String smallUrl = firstPhoto.getUrls().getSmall();
                            log.info("Resolved Unsplash photo small URL for: {} -> {}", query, smallUrl);
                            return smallUrl;
                        }
                    }
                } else {
                    log.warn("Invalid or truncated JSON response received from Unsplash: '{}'", body);
                }
            }
        } catch (Exception e) {
            log.warn(
                    "Failed to resolve Unsplash photo from API for query: {}, falling back. Raw response: {}, Error: {}",
                    query, rawBody, e.getMessage());
        }
        return DEFAULT_IMAGE;
    }

    public byte[] downloadPhotoBytes(String urlStr) {
        try {
            byte[] rawBytes = restClient.get()
                    .uri(urlStr)
                    .retrieve()
                    .body(byte[].class);

            if (rawBytes == null)
                return null;

            // Compress the image to ensure it is not above 300KB
            if (rawBytes.length > 300 * 1024) {
                log.info("Compressing image from {} bytes to fit within 200KB", rawBytes.length);
                return compressImage(rawBytes, 300 * 1024);
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
            if (image == null)
                return imageBytes;

            int width = image.getWidth();
            int height = image.getHeight();

            // Step 1: Scale down if the image has large dimensions (max 1200px) to
            // exponentially drop bytes without losing details
            int maxDim = 1200;
            if (width > maxDim || height > maxDim) {
                double scale = (double) maxDim / Math.max(width, height);
                int targetWidth = (int) (width * scale);
                int targetHeight = (int) (height * scale);

                log.info("Scaling down image from {}x{} to target {}x{}", width, height, targetWidth, targetHeight);

                Image scaledImage = image.getScaledInstance(targetWidth, targetHeight,
                        Image.SCALE_SMOOTH);
                BufferedImage bufferedScaledImage = new BufferedImage(targetWidth, targetHeight,
                        BufferedImage.TYPE_INT_RGB);
                Graphics2D g2d = bufferedScaledImage.createGraphics();
                g2d.drawImage(scaledImage, 0, 0, null);
                g2d.dispose();
                image = bufferedScaledImage;
            }

            // Step 2: Try compressing with high JPEG quality step-down (capped at min 0.5
            // quality to preserve sharpness)
            float quality = 0.85f;
            byte[] compressed = imageBytes;
            while (quality >= 0.5f) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
                if (!writers.hasNext())
                    break;
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
                quality -= 0.1f;
            }

            // Step 3: Fallback scale down further to 800px if still over limit, rather than
            // degrading quality
            if (compressed.length > maxSizeBytes) {
                int secondMaxDim = 800;
                width = image.getWidth();
                height = image.getHeight();
                double scale = (double) secondMaxDim / Math.max(width, height);
                int targetWidth = (int) (width * scale);
                int targetHeight = (int) (height * scale);

                log.info("Second scaling down stage: {}x{} to {}x{}", width, height, targetWidth, targetHeight);

                java.awt.Image scaledImage = image.getScaledInstance(targetWidth, targetHeight,
                        java.awt.Image.SCALE_SMOOTH);
                BufferedImage bufferedScaledImage = new BufferedImage(targetWidth, targetHeight,
                        BufferedImage.TYPE_INT_RGB);
                java.awt.Graphics2D g2d = bufferedScaledImage.createGraphics();
                g2d.drawImage(scaledImage, 0, 0, null);
                g2d.dispose();
                image = bufferedScaledImage;

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
                if (writers.hasNext()) {
                    ImageWriter writer = writers.next();
                    try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
                        writer.setOutput(ios);
                        ImageWriteParam param = writer.getDefaultWriteParam();
                        if (param.canWriteCompressed()) {
                            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                            param.setCompressionType(param.getCompressionTypes()[0]);
                            param.setCompressionQuality(0.75f);
                        }
                        writer.write(null, new IIOImage(image, null, null), param);
                    } finally {
                        writer.dispose();
                    }
                    compressed = baos.toByteArray();
                }
            }

            return compressed;
        } catch (Exception e) {
            log.warn("Failed to compress image, returning raw bytes: {}", e.getMessage());
            return imageBytes;
        }
    }

    @lombok.Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SearchResponse {
        private int total;
        @JsonProperty("total_pages")
        private int totalPages;
        private java.util.List<Photo> results;
    }

    @lombok.Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Photo {
        private String id;
        @JsonProperty("created_at")
        private String createdAt;
        @JsonProperty("updated_at")
        private String updatedAt;
        private Urls urls;
    }

    @lombok.Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Urls {
        private String raw;
        private String full;
        private String regular;
        private String small;
        private String thumb;
        @JsonProperty("small_s3")
        private String smallS3;
    }
}
