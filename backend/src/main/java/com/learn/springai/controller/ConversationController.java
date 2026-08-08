package com.learn.springai.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.learn.springai.dto.conversation.ConversationDTO;
import com.learn.springai.dto.conversation.NewConversationDTO;
import com.learn.springai.dto.tripRequest.TripRequestResponseDTO;
import com.learn.springai.dto.tripRequest.TripRequestUpdateDTO;
import com.learn.springai.model.Conversation;
import com.learn.springai.service.ConversationService;
import com.learn.springai.service.TripRequestService;
import com.learn.springai.service.GeocodingService;
import com.learn.springai.model.TripRequest;
import com.learn.springai.dto.geocoding.PublicGeocodeResponse;

import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.validation.Valid;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.http.HttpStatus;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('USER', 'ADMIN')")
public class ConversationController {

    private final ConversationService conversationService;
    private final TripRequestService tripRequestService;
    private final GeocodingService geocodingService;
    private final com.learn.springai.service.BackblazeStorageService backblazeStorageService;
    private final com.learn.springai.service.DocumentProcessingService documentProcessingService;
    private final com.learn.springai.repository.TripPdfRepository tripPdfRepository;
    private final com.learn.springai.service.UnsplashService unsplashService;
    private final com.learn.springai.repository.TripRequestRepository tripRequestRepository;

    @PostMapping("/new")
    public ResponseEntity<ConversationDTO> startNewConversation(
            @Valid @RequestBody NewConversationDTO newConversationDTO) {
        return ResponseEntity.status(HttpStatus.SC_CREATED)
                .body(conversationService.startNewConversation(newConversationDTO.getUserId()));
    }

    @GetMapping
    public ResponseEntity<List<ConversationDTO>> getUserConversations(
            HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.SC_UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(conversationService.getConversationsByUserId(userId));
    }

    private String getCurrentUserId(HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        if (userId == null) {
            org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof String) {
                userId = (String) auth.getPrincipal();
            }
        }
        return userId;
    }

    @GetMapping("/{conversationId}")
    public ResponseEntity<ConversationDTO> getConversation(
            @PathVariable("conversationId") String conversationId,
            HttpServletRequest request) {
        conversationService.verifyReadAccess(conversationId, getCurrentUserId(request));
        ConversationDTO conversation = conversationService.getConversationById(conversationId);
        if (conversation != null) {
            return ResponseEntity.ok(conversation);
        } else {
            return ResponseEntity.notFound().build();
        }
    }
    @GetMapping("/{conversationId}/messages")
    public ResponseEntity<Map<String, Object>> getConversationMessages(
            @PathVariable("conversationId") String conversationId,
            @RequestParam(value = "cursor", required = false) Integer cursor,
            @RequestParam(value = "limit", required = false, defaultValue = "15") Integer limit,
            HttpServletRequest request) {
        conversationService.verifyReadAccess(conversationId, getCurrentUserId(request));
        Map<String, Object> response = conversationService.getConversationMessagesPaginated(conversationId, cursor, limit);
        return ResponseEntity.ok(response);
    }
    @DeleteMapping("/{conversationId}")
    public ResponseEntity<Void> deleteConversation(
            @PathVariable("conversationId") String conversationId,
            HttpServletRequest request) {
        conversationService.verifyWriteAccess(conversationId, getCurrentUserId(request));
        conversationService.deleteConversation(conversationId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{conversationId}/upload")
    public ResponseEntity<String> uploadPdf(
            @PathVariable("conversationId") String conversationId,
            @RequestParam("file") MultipartFile file) {
        conversationService.ingestPdf(conversationId, file);
        return ResponseEntity.ok("PDF upload started successfully.");
    }

    @PostMapping("/trips/{pdfId}/fork")
    public ResponseEntity<ConversationDTO> forkTrip(
            @PathVariable("pdfId") String pdfId,
            @RequestParam("targetUserId") String targetUserId) {
        ConversationDTO forked = conversationService.forkTrip(pdfId, targetUserId);
        return ResponseEntity.ok(forked);
    }

    @GetMapping("/trips/public")
    public ResponseEntity<List<com.learn.springai.model.PublicTripGalleryItem>> getPublicTrips() {
        List<com.learn.springai.model.PublicTripGalleryItem> trips = conversationService.getPublicTrips();
        return ResponseEntity.ok(trips);
    }

    @PostMapping("/trips/{pdfId}/visibility")
    public ResponseEntity<Void> updateVisibility(
            @PathVariable("pdfId") String pdfId,
            @RequestParam("isPublic") boolean isPublic) {
        conversationService.updateTripVisibility(pdfId, isPublic);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{conversationId}/map-route")
    public ResponseEntity<Map<String, Object>> getMapRoute(@PathVariable("conversationId") String conversationId) {
        TripRequest request = tripRequestService.findEntityByConversationId(conversationId).orElse(null);
        if (request == null) {
            return ResponseEntity.notFound().build();
        }

        // Build a GeoJSON format response
        List<Map<String, Object>> features = new java.util.ArrayList<>();
        List<double[]> routeCoordinates = new java.util.ArrayList<>();

        // Geocode source
        if (request.getSource() != null && !request.getSource().isBlank()) {
            PublicGeocodeResponse srcGeo = geocodingService.geocode(request.getSource());
            if (srcGeo != null) {
                double lat = Double.parseDouble(srcGeo.getLat());
                double lon = Double.parseDouble(srcGeo.getLon());
                routeCoordinates.add(new double[]{lon, lat});

                features.add(Map.of(
                    "type", "Feature",
                    "geometry", Map.of("type", "Point", "coordinates", List.of(lon, lat)),
                    "properties", Map.of("title", request.getSource(), "type", "START")
                ));
            }
        }

        // Geocode destination
        if (request.getDestination() != null && !request.getDestination().isBlank()) {
            PublicGeocodeResponse destGeo = geocodingService.geocode(request.getDestination());
            if (destGeo != null) {
                double lat = Double.parseDouble(destGeo.getLat());
                double lon = Double.parseDouble(destGeo.getLon());
                routeCoordinates.add(new double[]{lon, lat});

                features.add(Map.of(
                    "type", "Feature",
                    "geometry", Map.of("type", "Point", "coordinates", List.of(lon, lat)),
                    "properties", Map.of("title", request.getDestination(), "type", "END")
                ));
            }
        }

        // If we have coordinates, build a LineString route connecting source to destination
        if (routeCoordinates.size() > 1) {
            features.add(Map.of(
                "type", "Feature",
                "geometry", Map.of("type", "LineString", "coordinates", routeCoordinates),
                "properties", Map.of("description", "Direct connection route")
            ));
        }

        return ResponseEntity.ok(Map.of(
            "type", "FeatureCollection",
            "features", features
        ));
    }

    @GetMapping("/{conversationId}/upload-url")
    public ResponseEntity<Map<String, String>> getUploadUrl(
            @PathVariable String conversationId,
            @RequestParam String filename,
            @RequestParam(required = false) String contentType) {
        String uuid = java.util.UUID.randomUUID().toString();
        String fileKey = "user_uploads/" + uuid + "_" + filename;
        String uploadUrl = backblazeStorageService.generatePresignedUploadUrl(fileKey, contentType);
        return ResponseEntity.ok(Map.of(
                "uploadUrl", uploadUrl,
                "fileKey", fileKey
        ));
    }

    @PostMapping("/{conversationId}/process-upload")
    public ResponseEntity<Map<String, Object>> processUpload(
            @PathVariable String conversationId,
            @RequestBody Map<String, String> body) {
        String fileKey = body.get("fileKey");
        String contentType = body.get("contentType");
        String filename = body.get("filename");

        if (fileKey == null || fileKey.isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "fileKey is required"
            );
        }

        Conversation conversation = conversationService.getConversation(conversationId);

        try {
            // 1. Download bytes from Backblaze B2 S3
            byte[] fileBytes = backblazeStorageService.downloadFile(fileKey);

            // 2. Extract text using PDFBox/Tika/Tess4J
            String extractedText = documentProcessingService.extractText(fileBytes, contentType);

            // 3. Verify travel relevance
            boolean isTravelRelated = documentProcessingService.checkTravelRelevance(extractedText);
            if (!isTravelRelated) {
                return ResponseEntity.badRequest().body(Map.of(
                        "status", "REJECTED",
                        "message", "This information does not contain anything related for travel so not processing it."
                ));
            }

            // 4. Index text in Vector Database
            documentProcessingService.indexToVectorDB(extractedText, conversationId, fileKey);

            // 5. Append context to chat conversation
            documentProcessingService.addContextToConversation(conversation, filename != null ? filename : "Document", extractedText);

            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "message", "Document processed and added to conversation successfully."
            ));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("status", "ERROR", "message", "Failed to process document: " + e.getMessage()));
        }
    }

    @GetMapping("/trips/{pdfId}/download")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<Void> downloadTripPdf(
            @PathVariable String pdfId,
            jakarta.servlet.http.HttpServletRequest request,
            jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {

        com.learn.springai.model.TripPdf pdf = null;
        try {
            pdf = conversationService.getConversation(pdfId).getTripPdf();
        } catch (Exception e) {
            // ignore
        }
        if (pdf == null) {
            pdf = tripPdfRepository.findById(pdfId).orElse(null);
        }
        if (pdf == null) {
            pdf = tripPdfRepository.findByConversationId(pdfId).orElse(null);
        }
        if (pdf == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "PDF not found"
            );
        }

        // Authorization check: if PDF is private, current user must be owner
        if (!pdf.isPublic()) {
            String currentUserId = (String) request.getAttribute("userId");
            Conversation conversation = pdf.getConversation();
            if (currentUserId == null || conversation == null || conversation.getUser() == null 
                    || !conversation.getUser().getId().equals(currentUserId)) {
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.FORBIDDEN, "This trip plan is private."
                );
            }
        }

        String filePath = pdf.getFilePath();
        Conversation conversation = pdf.getConversation();
        String src = "unknown";
        String dest = pdf.getDestination();
        String days = "0";
        String budget = "mid";
        String ownerName = "traveler";

        if (conversation != null) {
            if (conversation.getUser() != null) {
                ownerName = conversation.getUser().getName();
            }
            Optional<TripRequest> tripReqOpt = tripRequestRepository.findByConversationId(conversation.getId());
            if (tripReqOpt.isPresent()) {
                TripRequest tr = tripReqOpt.get();
                if (tr.getSource() != null) src = tr.getSource();
                if (tr.getDestination() != null) dest = tr.getDestination();
                days = String.valueOf(tr.getTotalDays());
                if (tr.getBudgetPreference() != null) budget = tr.getBudgetPreference().name();
            }
        }

        String cleanSrc = src.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
        String cleanDest = dest.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
        String cleanDays = days.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
        String cleanBudget = budget.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
        String cleanOwner = ownerName.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();

        String customFileName = String.format("%s_%s_%sdays_%s_%s.pdf", 
                cleanSrc, cleanDest, cleanDays, cleanBudget, cleanOwner);

        // If file is stored locally, serve it directly
        if (filePath != null && (filePath.startsWith("uploads") || filePath.contains("/") || filePath.contains("\\"))) {
            java.nio.file.Path localPath = java.nio.file.Paths.get(filePath);
            if (java.nio.file.Files.exists(localPath)) {
                byte[] bytes = java.nio.file.Files.readAllBytes(localPath);
                response.setContentType("application/pdf");
                response.setContentLength(bytes.length);
                response.setHeader("Content-Disposition", "attachment; filename=\"" + customFileName + "\"");
                response.getOutputStream().write(bytes);
                response.getOutputStream().flush();
                return null;
            }
        }

        // Otherwise generate 30-minute presigned GET URL and redirect
        try {
            String s3Key = pdf.getFilePath();
            String presignedUrl = backblazeStorageService.generatePresignedDownloadUrl(s3Key, customFileName, java.time.Duration.ofMinutes(30));
            System.out.println("[BACKEND] PDF Redirect Download Link: " + presignedUrl);
            response.sendRedirect(presignedUrl);
            return null;
        } catch (Exception e) {
            // Last resort: check if there's a local backup even if the DB points to S3
            java.nio.file.Path localPath = java.nio.file.Paths.get("uploads", "final_trip_pdfs", pdfId + ".pdf");
            if (java.nio.file.Files.exists(localPath)) {
                byte[] bytes = java.nio.file.Files.readAllBytes(localPath);
                response.setContentType("application/pdf");
                response.setContentLength(bytes.length);
                response.setHeader("Content-Disposition", "attachment; filename=\"" + customFileName + "\"");
                response.getOutputStream().write(bytes);
                response.getOutputStream().flush();
                return null;
            }
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Failed to download PDF: " + e.getMessage()
            );
        }
    }

    @GetMapping("/trips/{pdfId}/download-url")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<Map<String, String>> getDownloadUrl(
            @PathVariable String pdfId,
            jakarta.servlet.http.HttpServletRequest request) {

        com.learn.springai.model.TripPdf pdf = null;
        try {
            pdf = conversationService.getConversation(pdfId).getTripPdf();
        } catch (Exception e) {
            // ignore
        }
        if (pdf == null) {
            pdf = tripPdfRepository.findById(pdfId).orElse(null);
        }
        if (pdf == null) {
            pdf = tripPdfRepository.findByConversationId(pdfId).orElse(null);
        }
        if (pdf == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "PDF not found"
            );
        }

        // Authorization check: if PDF is private, current user must be owner
        if (!pdf.isPublic()) {
            String currentUserId = (String) request.getAttribute("userId");
            Conversation conversation = pdf.getConversation();
            if (currentUserId == null || conversation == null || conversation.getUser() == null 
                    || !conversation.getUser().getId().equals(currentUserId)) {
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.FORBIDDEN, "This trip plan is private."
                );
            }
        }

        try {
            String s3Key = pdf.getFilePath();
            String presignedUrl = backblazeStorageService.generatePresignedDownloadUrl(s3Key, java.time.Duration.ofMinutes(30));
            System.out.println("[BACKEND] PDF JSON Download Link: " + presignedUrl);
            return ResponseEntity.ok(Map.of("downloadUrl", presignedUrl));
        } catch (Exception e) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Failed to generate download URL: " + e.getMessage()
            );
        }
    }

    @GetMapping("/trips/{conversationId}/thumbnail")
    public ResponseEntity<byte[]> getThumbnail(@PathVariable String conversationId) {
        try {
            byte[] bytes = backblazeStorageService.downloadFile("final_trip_pdfs/" + conversationId + "-thumbnail.svg");
            return ResponseEntity.ok()
                    .contentType(org.springframework.http.MediaType.parseMediaType("image/svg+xml"))
                    .body(bytes);
        } catch (Exception e) {
            // Fall back to local file if B2 download fails
            try {
                java.nio.file.Path localPath = java.nio.file.Paths.get("uploads", "final_trip_pdfs", conversationId + "-thumbnail.svg");
                if (java.nio.file.Files.exists(localPath)) {
                    byte[] bytes = java.nio.file.Files.readAllBytes(localPath);
                    return ResponseEntity.ok()
                            .contentType(org.springframework.http.MediaType.parseMediaType("image/svg+xml"))
                            .body(bytes);
                }
            } catch (Exception ex) {
                // ignore
            }
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "Thumbnail not found"
            );
        }
    }

    @GetMapping("/{conversationId}/destination-image")
    public ResponseEntity<Map<String, String>> getDestinationImage(@PathVariable String conversationId) {
        String destination = "travel";
        TripRequest req = tripRequestService.findEntityByConversationId(conversationId).orElse(null);
        if (req != null && req.getDestination() != null && !req.getDestination().isBlank()) {
            destination = req.getDestination();
        }
        String imageUrl = unsplashService.getPhotoUrl(destination);
        return ResponseEntity.ok(Map.of("imageUrl", imageUrl));
    }

    @PutMapping("/{conversationId}/pin")
    public ResponseEntity<Void> togglePin(
            @PathVariable("conversationId") String conversationId,
            @RequestParam("pinned") boolean pinned) {
        conversationService.toggleConversationPin(conversationId, pinned);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{conversationId}/public")
    public ResponseEntity<Void> togglePublic(
            @PathVariable("conversationId") String conversationId,
            @RequestParam("isPublic") boolean isPublic) {
        conversationService.toggleConversationPublic(conversationId, isPublic);
        return ResponseEntity.ok().build();
    }
    @GetMapping("/share/{conversationId}")
    public ResponseEntity<Map<String, Object>> getSharedConversationMessages(
            @PathVariable("conversationId") String conversationId,
            @RequestParam(value = "cursor", required = false) Integer cursor,
            @RequestParam(value = "limit", required = false, defaultValue = "15") Integer limit) {
        Conversation conv = conversationService.getConversation(conversationId);
        if (conv == null || conv.getDeleted()) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.NOT_FOUND).build();
        }
        if (!Boolean.TRUE.equals(conv.getIsPublic())) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build();
        }
        Map<String, Object> response = conversationService.getConversationMessagesPaginated(conversationId, cursor, limit);
        return ResponseEntity.ok(response);
    }
    @GetMapping("/{conversationId}/trip-request")
    public ResponseEntity<TripRequestResponseDTO> getTripRequest(
            @PathVariable("conversationId") String conversationId) {
        try {
            return ResponseEntity.ok(tripRequestService.getByConversationId(conversationId));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PatchMapping("/{conversationId}/trip-request")
    public ResponseEntity<TripRequestResponseDTO> updateTripRequest(
            @PathVariable("conversationId") String conversationId,
            @RequestBody TripRequestUpdateDTO updateDTO) {
        
        // Load old request to compare source/destination
        TripRequest oldRequest = tripRequestService.findEntityByConversationId(conversationId).orElse(null);
        String oldSource = oldRequest != null ? oldRequest.getSource() : "";
        String oldDestination = oldRequest != null ? oldRequest.getDestination() : "";

        boolean sourceChanged = updateDTO.getSource() != null && !updateDTO.getSource().equalsIgnoreCase(oldSource);
        boolean destinationChanged = updateDTO.getDestination() != null && !updateDTO.getDestination().equalsIgnoreCase(oldDestination);

        if (sourceChanged || destinationChanged) {
            // Clear out optional fields in updateDTO as requested
            updateDTO.setAdults(1);
            updateDTO.setChildren(0);
            updateDTO.setMinHotelStars(3);
            updateDTO.setMaxHotelStars(5);
            updateDTO.setNotes("");
            
            // Set mustVisitPlaces etc to empty set
            updateDTO.setMustVisitPlaces(java.util.Collections.emptySet());
            updateDTO.setVacationStyles(java.util.Collections.emptySet());
            updateDTO.setInterests(java.util.Collections.emptySet());
            updateDTO.setPreferredTransportModes(java.util.Collections.emptySet());

            // Save the system notification message
            conversationService.saveSystemMessage(conversationId, 
                "User has changed the trip preferences. Optional fields have been reset. Need to gather more information.");
        }

        TripRequestResponseDTO updated = tripRequestService.update(conversationId, updateDTO);
        return ResponseEntity.ok(updated);
    }
}