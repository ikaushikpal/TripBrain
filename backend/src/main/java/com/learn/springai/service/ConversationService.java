package com.learn.springai.service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.learn.springai.dto.conversation.ConversationDTO;
import com.learn.springai.model.ChatMessage;
import com.learn.springai.model.Conversation;
import com.learn.springai.model.User;
import com.learn.springai.model.TripPdf;
import com.learn.springai.model.TripRequest;
import com.learn.springai.model.PublicTripGalleryItem;
import com.learn.springai.repository.ChatMessageRepository;
import com.learn.springai.repository.ConversationRepository;
import com.learn.springai.repository.UserRepository;
import com.learn.springai.repository.VectorDBRepository;
import com.learn.springai.repository.TripPdfRepository;
import com.learn.springai.repository.TripRequestRepository;
import com.learn.springai.repository.PublicTripGalleryRepository;
import com.learn.springai.util.ChecksumUtils;
import com.learn.springai.config.DatabaseViewManager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationService {
    private final UserRepository userRepository;
    private final VectorDBRepository vectorDBRepository;
    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final TripPdfRepository tripPdfRepository;
    private final TripRequestRepository tripRequestRepository;
    private final PublicTripGalleryRepository publicTripGalleryRepository;
    private final DatabaseViewManager databaseViewManager;

    public void verifyReadAccess(String conversationId, String userId) {
        Conversation conversation = getConversation(conversationId);
        boolean isOwner = userId != null && conversation.getUser() != null && userId.equals(conversation.getUser().getId());
        boolean isPublic = conversation.getTripPdf() != null && conversation.getTripPdf().isPublic();

        if (!isOwner && !isPublic) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN, "Access Denied: You do not have permission to view this conversation"
            );
        }
    }

    public void verifyWriteAccess(String conversationId, String userId) {
        Conversation conversation = getConversation(conversationId);
        boolean isOwner = userId != null && conversation.getUser() != null && userId.equals(conversation.getUser().getId());

        if (!isOwner) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN, "Access Denied: Only the conversation owner can modify this conversation"
            );
        }
    }

    public ConversationDTO startNewConversation(String userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));

        Conversation conversation = new Conversation();
        conversation.setUser(user);
        conversation.setConversationStart(LocalDateTime.now());
        conversation.setLastUpdated(LocalDateTime.now());

        Conversation newConversation = conversationRepository.save(conversation);
        return ConversationDTO.createFromConversation(newConversation);
    }

    public Conversation updateConversation(Conversation conversation) {
        conversation.setLastUpdated(LocalDateTime.now());
        Conversation savedConv = conversationRepository.save(conversation);
        conversationRepository.flush();
        return savedConv;
    }

    public Conversation getConversation(String id) {
        return conversationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Conversation with id:  " + id + " not found"));
    }

    public ConversationDTO getConversationById(String id) {
        Conversation conversation = this.getConversation(id);
        return ConversationDTO.createFromConversation(conversation);
    }

    public List<ConversationDTO> getConversationsByUserId(String userId) {
        return conversationRepository.findByUserIdAndDeletedFalseOrderByPinnedDescLastUpdatedDesc(userId).stream()
                .map(ConversationDTO::createFromConversation)
                .collect(java.util.stream.Collectors.toList());
    }

    public Map<String, Object> getConversationMessages(String conversationId) {
        Conversation conversation = this.getConversation(conversationId);

        if (conversation != null) {
            List<ChatMessage> messages = chatMessageRepository
                    .findByConversationIdAndDeletedFalseOrderBySequenceNumberAsc(conversationId);

            return Map.of(
                    "conversation", conversation,
                    "messages", messages.stream()
                            .map(msg -> {
                                Map<String, Object> messageMap = new HashMap<>();
                                messageMap.put("role", msg.getRole());
                                messageMap.put("content", msg.getContent());
                                messageMap.put("timestamp", msg.getMessageTimestamp());
                                messageMap.put("messageType", msg.getMessageType());
                                messageMap.put("metadata", msg.getMetadataJson());
                                return messageMap;
                            })
                            .toList());
        }

        throw new RuntimeException("Conversation not found");
    }

    public Map<String, Object> getConversationMessagesPaginated(String conversationId, Integer cursor, int limit) {
        Conversation conversation = this.getConversation(conversationId);
        if (conversation == null) {
            throw new RuntimeException("Conversation not found");
        }

        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, limit);
        List<ChatMessage> messages;
        if (cursor == null) {
            messages = chatMessageRepository
                    .findByConversationIdAndDeletedFalseOrderBySequenceNumberDesc(conversationId, pageable);
        } else {
            messages = chatMessageRepository
                    .findByConversationIdAndDeletedFalseAndSequenceNumberLessThanOrderBySequenceNumberDesc(conversationId, cursor, pageable);
        }

        List<ChatMessage> sortedMessages = new ArrayList<>(messages);
        Collections.reverse(sortedMessages);

        Integer nextCursor = null;
        if (!messages.isEmpty()) {
            ChatMessage oldestMessage = messages.get(messages.size() - 1);
            int minSequence = oldestMessage.getSequenceNumber();
            if (minSequence > 0) {
                nextCursor = minSequence;
            }
        }

        return Map.of(
                "conversation", conversation,
                "nextCursor", nextCursor != null ? nextCursor : "",
                "messages", sortedMessages.stream()
                        .map(msg -> {
                            Map<String, Object> messageMap = new HashMap<>();
                            messageMap.put("role", msg.getRole());
                            messageMap.put("content", msg.getContent());
                            messageMap.put("timestamp", msg.getMessageTimestamp());
                            messageMap.put("messageType", msg.getMessageType());
                            messageMap.put("metadata", msg.getMetadataJson());
                            messageMap.put("sequenceNumber", msg.getSequenceNumber());
                            return messageMap;
                        })
                        .toList());
    }

    public void deleteConversation(String conversationId) {
        Conversation conversation = this.getConversation(conversationId);

        if (conversation != null) {
            conversation.setDeleted(true);
            conversation.setLastUpdated(LocalDateTime.now());
            conversationRepository.save(conversation);
            databaseViewManager.refreshViewAsync();
        } else {
            throw new RuntimeException("Conversation not found");
        }
    }

    public void ingestPdf(String conversationId, org.springframework.web.multipart.MultipartFile file) {
        try {
            String checksum = ChecksumUtils.calculateSHA256(file);
            java.util.Optional<TripPdf> existingPdf = tripPdfRepository.findByChecksum(checksum);

            Conversation conversation = conversationRepository.findById(conversationId)
                    .orElseThrow(() -> new RuntimeException("Conversation not found"));

            if (existingPdf.isPresent()) {
                TripPdf sourcePdf = existingPdf.get();
                // Check if this conversation already has a PDF linked to prevent duplicates
                if (conversation.getTripPdf() != null) {
                    return;
                }
                // Save a new TripPdf database entry mapping this same physical file metadata to the conversation
                TripPdf linkedPdf = TripPdf.builder()
                        .conversation(conversation)
                        .filePath(sourcePdf.getFilePath())
                        .publicUrl(sourcePdf.getPublicUrl())
                        .isPublic(sourcePdf.isPublic())
                        .generatedAt(LocalDateTime.now())
                        .destination(sourcePdf.getDestination())
                        .checksum(checksum)
                        .thumbnailUrl(sourcePdf.getThumbnailUrl())
                        .build();
                tripPdfRepository.save(linkedPdf);
                // Skip async vector store calculation since it's already embedded in Qdrant
                return;
            }

            // Check if this conversation already has a PDF linked
            if (conversation.getTripPdf() != null) {
                // Delete existing first to enforce OneToOne correctly
                tripPdfRepository.delete(conversation.getTripPdf());
                conversationRepository.flush();
            }

            java.nio.file.Path uploadPath = java.nio.file.Paths.get("uploads").toAbsolutePath();
            java.nio.file.Files.createDirectories(uploadPath);
            String filename = java.util.UUID.randomUUID().toString() + "-" + file.getOriginalFilename();
            java.nio.file.Path targetPath = uploadPath.resolve(filename);
            java.nio.file.Files.copy(file.getInputStream(), targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            // Create new TripPdf entry in database mapping the uploaded file metadata to the conversation
            TripPdf newPdf = TripPdf.builder()
                    .conversation(conversation)
                    .filePath("uploads/" + filename)
                    .publicUrl("/resources/" + filename)
                    .isPublic(false)
                    .generatedAt(LocalDateTime.now())
                    .destination(file.getOriginalFilename())
                    .checksum(checksum)
                    .build();
            tripPdfRepository.save(newPdf);

            org.springframework.core.io.Resource resource = new org.springframework.core.io.FileSystemResource(targetPath.toFile());
            vectorDBRepository.saveToVectorDB(resource, conversationId, targetPath.toUri().toString());
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to ingest PDF", e);
        }
    }

    @Transactional
    public ConversationDTO forkTrip(String pdfId, String targetUserId) {
        TripPdf originalPdf = tripPdfRepository.findById(pdfId)
                .orElseThrow(() -> new RuntimeException("PDF not found"));

        Conversation originalConv = originalPdf.getConversation();
        TripRequest originalRequest = tripRequestRepository.findByConversationId(originalConv.getId())
                .orElse(null);

        User forkingUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Conversation forkedConv = Conversation.builder()
                .user(forkingUser)
                .title("Forked: " + originalConv.getTitle())
                .conversationStart(LocalDateTime.now())
                .lastUpdated(LocalDateTime.now())
                .deleted(false)
                .build();
        Conversation savedConv = conversationRepository.save(forkedConv);

        if (originalRequest != null) {
            TripRequest clonedRequest = TripRequest.builder()
                    .conversation(savedConv)
                    .source(originalRequest.getSource())
                    .destination(originalRequest.getDestination())
                    .startDate(java.time.LocalDate.now().plusDays(14)) // Offset to future date
                    .endDate(java.time.LocalDate.now().plusDays(14 + originalRequest.getNights()))
                    .adults(originalRequest.getAdults())
                    .children(originalRequest.getChildren())
                    .travellerType(originalRequest.getTravellerType())
                    .currency(originalRequest.getCurrency())
                    .budgetPreference(originalRequest.getBudgetPreference())
                    .maxBudget(originalRequest.getMaxBudget())
                    .dailyBudgetPerPerson(originalRequest.getDailyBudgetPerPerson())
                    .flightsIncludedInBudget(originalRequest.getFlightsIncludedInBudget())
                    .maxTravelTimePerDay(originalRequest.getMaxTravelTimePerDay())
                    .cabinClass(originalRequest.getCabinClass())
                    .directFlightsOnly(originalRequest.getDirectFlightsOnly())
                    .privateTransferPreferred(originalRequest.getPrivateTransferPreferred())
                    .minHotelStars(originalRequest.getMinHotelStars())
                    .maxHotelStars(originalRequest.getMaxHotelStars())
                    .includeFoodTour(originalRequest.getIncludeFoodTour())
                    .activityIntensity(originalRequest.getActivityIntensity())
                    .includeTransport(originalRequest.getIncludeTransport())
                    .includeHotels(originalRequest.getIncludeHotels())
                    .includeRestaurants(originalRequest.getIncludeRestaurants())
                    .includeWeatherForecast(originalRequest.getIncludeWeatherForecast())
                    .includeCostBreakdown(originalRequest.getIncludeCostBreakdown())
                    .generateWeatherFallbacks(originalRequest.getGenerateWeatherFallbacks())
                    .includeVisaInfo(originalRequest.getIncludeVisaInfo())
                    .nationality(originalRequest.getNationality())
                    .passportCountry(originalRequest.getPassportCountry())
                    .accessibilityRequired(originalRequest.getAccessibilityRequired())
                    .notes(originalRequest.getNotes())
                    
                    // Safely clone persistent collections by copying elements into new HashSets
                    .preferredTransportModes(originalRequest.getPreferredTransportModes() != null ? new java.util.HashSet<>(originalRequest.getPreferredTransportModes()) : null)
                    .accommodationTypes(originalRequest.getAccommodationTypes() != null ? new java.util.HashSet<>(originalRequest.getAccommodationTypes()) : null)
                    .requiredAmenities(originalRequest.getRequiredAmenities() != null ? new java.util.HashSet<>(originalRequest.getRequiredAmenities()) : null)
                    .foodStyles(originalRequest.getFoodStyles() != null ? new java.util.HashSet<>(originalRequest.getFoodStyles()) : null)
                    .foodAllergies(originalRequest.getFoodAllergies() != null ? new java.util.HashSet<>(originalRequest.getFoodAllergies()) : null)
                    .diningStyles(originalRequest.getDiningStyles() != null ? new java.util.HashSet<>(originalRequest.getDiningStyles()) : null)
                    .vacationStyles(originalRequest.getVacationStyles() != null ? new java.util.HashSet<>(originalRequest.getVacationStyles()) : null)
                    .extras(originalRequest.getExtras() != null ? new java.util.HashSet<>(originalRequest.getExtras()) : null)
                    .interests(originalRequest.getInterests() != null ? new java.util.HashSet<>(originalRequest.getInterests()) : null)
                    .mustVisitPlaces(originalRequest.getMustVisitPlaces() != null ? new java.util.HashSet<>(originalRequest.getMustVisitPlaces()) : null)
                    .avoidPlaces(originalRequest.getAvoidPlaces() != null ? new java.util.HashSet<>(originalRequest.getAvoidPlaces()) : null)
                    .build();
            tripRequestRepository.save(clonedRequest);
        }

        // Clone the TripPdf entity to link the new conversation to the same PDF resource
        TripPdf clonedPdf = TripPdf.builder()
                .conversation(savedConv)
                .filePath(originalPdf.getFilePath())
                .publicUrl(originalPdf.getPublicUrl())
                .isPublic(false) // Forked trip is private to this new user by default
                .generatedAt(LocalDateTime.now())
                .destination(originalPdf.getDestination())
                .thumbnailUrl(originalPdf.getThumbnailUrl())
                .tags(originalPdf.getTags())
                .checksum(originalPdf.getChecksum())
                .build();
        tripPdfRepository.save(clonedPdf);

        // Copy original chat messages to the cloned conversation to provide full contextual history
        List<ChatMessage> originalMessages = chatMessageRepository.findByConversationIdAndDeletedFalseOrderBySequenceNumberAsc(originalConv.getId());
        int seq = 0;
        for (ChatMessage msg : originalMessages) {
            ChatMessage clonedMsg = ChatMessage.builder()
                    .conversation(savedConv)
                    .role(msg.getRole())
                    .content(msg.getContent())
                    .messageType(msg.getMessageType())
                    .metadataJson(msg.getMetadataJson())
                    .sequenceNumber(seq++)
                    .messageTimestamp(LocalDateTime.now())
                    .deleted(false)
                    .build();
            chatMessageRepository.save(clonedMsg);
        }

        // If no original messages exist, add a standard welcome message
        if (originalMessages.isEmpty()) {
            chatMessageRepository.save(ChatMessage.builder()
                    .conversation(savedConv)
                    .role("ASSISTANT")
                    .content("I have duplicated the trip request for '" + originalPdf.getDestination() + "'. Let me know if you would like to customize dates, budget, or anything else!")
                    .sequenceNumber(0)
                    .messageTimestamp(LocalDateTime.now())
                    .deleted(false)
                    .build());
        }

        return ConversationDTO.createFromConversation(savedConv);
    }


    public List<PublicTripGalleryItem> getPublicTrips() {
        return publicTripGalleryRepository.findAllByOrderByGeneratedAtDesc();
    }

    @Transactional
    public void updateTripVisibility(String pdfId, boolean isPublic) {
        TripPdf pdf = tripPdfRepository.findById(pdfId)
                .orElseThrow(() -> new RuntimeException("PDF not found"));
        pdf.setPublic(isPublic);
        tripPdfRepository.save(pdf);
        databaseViewManager.refreshViewAsync();
    }

    @Transactional
    public void toggleConversationPin(String conversationId, boolean pinned) {
        Conversation conv = getConversation(conversationId);
        conv.setPinned(pinned);
        conversationRepository.save(conv);
    }

    @Transactional
    public void toggleConversationPublic(String conversationId, boolean isPublic) {
        Conversation conv = getConversation(conversationId);
        conv.setIsPublic(isPublic);
        conversationRepository.save(conv);

        com.learn.springai.model.TripPdf pdf = conv.getTripPdf();
        if (pdf != null) {
            pdf.setPublic(isPublic);
            tripPdfRepository.save(pdf);
        }
        databaseViewManager.refreshViewAsync();
    }

    @Transactional
    public void saveSystemMessage(String conversationId, String content) {
        Conversation conversation = getConversation(conversationId);
        Integer lastSequence = chatMessageRepository.findMaxSequenceByConversationId(conversationId);
        if (lastSequence == null) lastSequence = -1;

        chatMessageRepository.save(
                com.learn.springai.model.ChatMessage.builder()
                        .conversation(conversation)
                        .role("ASSISTANT")
                        .content(content)
                        .sequenceNumber(++lastSequence)
                        .messageTimestamp(LocalDateTime.now())
                        .deleted(false)
                        .build());
        chatMessageRepository.flush();
    }

    @Scheduled(cron = "0 */10 * * * *") // Runs every 10 minutes
    @Transactional
    public void cleanupUninitiatedConversations() {
        log.info("Running background job to clean up uninitiated conversations...");
        try {
            List<Conversation> uninitiated = conversationRepository.findUninitiatedConversations();
            if (!uninitiated.isEmpty()) {
                log.info("Found {} uninitiated conversations to delete", uninitiated.size());
                for (Conversation conv : uninitiated) {
                    conv.setDeleted(true);
                    conv.setLastUpdated(LocalDateTime.now());
                }
                conversationRepository.saveAll(uninitiated);
                log.info("Successfully marked uninitiated conversations as deleted.");
            }
        } catch (Exception e) {
            log.error("Failed to run cleanup job", e);
        }
    }
}
