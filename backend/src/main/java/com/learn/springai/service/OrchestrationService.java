package com.learn.springai.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import com.learn.springai.model.Conversation;
import com.learn.springai.model.TripRequest;
import com.learn.springai.model.TripPdf;
import com.learn.springai.model.ChatMessage;
import com.learn.springai.repository.ChatMessageRepository;
import reactor.core.publisher.Flux;
import java.time.LocalDateTime;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learn.springai.config.TripRequestPromptSerializer;
import com.learn.springai.dto.tripRequest.TripRequestUpdateDTO;

@Service
public class OrchestrationService {

    // =========================================================================
    // GROQ MODEL POOL (COMMENTED OUT AS PER USER REQUEST)
    // =========================================================================
    // public static final String PRIMARY_MODEL = "openai/gpt-oss-120b";
    // private static final List<String> MODELS_POOL = List.of(
    // "openai/gpt-oss-120b",
    // "openai/gpt-oss-20b",
    // "qwen/qwen3.6-27b",
    // "qwen/qwen3.8-27b"
    // );

    // =========================================================================
    // GEMINI MODEL POOL (ACTIVE)
    // =========================================================================
    public static final String PRIMARY_MODEL = "gemini-3.5-flash-lite";
    private static final List<String> MODELS_POOL = List.of(
            "gemini-3.5-flash-lite",
            "gemini-3.5-flash",
            "gemini-3.6-flash",
            "gemini-3.7-flash");
    private static final java.util.Random RANDOM = new java.util.Random();

    private String getRandomModel() {
        return MODELS_POOL.get(RANDOM.nextInt(MODELS_POOL.size()));
    }

    private org.springframework.ai.chat.prompt.ChatOptions getRandomModelOptions() {
        String model = getRandomModel();
        logger.info("Load-balancing: dynamically selected Gemini model = {}", model);
        return org.springframework.ai.chat.prompt.ChatOptions.builder()
                .model(model)
                .build();
    }

    private static final Logger logger = LoggerFactory.getLogger(OrchestrationService.class);

    private final ChatClient routerChatClient;
    private final ChatClient itineraryChatClient;
    private final ChatClient visaChatClient;
    private final ChatClient budgetChatClient;
    private final ChatClient generalChatClient;
    private final ChatClient exportChatClient;

    private final UserProfileService userProfileService;
    private final ConversationService conversationService;
    private final ChatMessageRepository chatMessageRepository;
    private final com.learn.springai.config.LlmBulkheadManager llmBulkheadManager;
    private final TripRequestService tripRequestService;
    private final com.learn.springai.tool.RetrievalHelper retrievalHelper;
    private final TripPdfService tripPdfService;
    private final ObjectMapper objectMapper;
    private final PromptTemplateService promptTemplateService;

    public OrchestrationService(
            @Qualifier("routerChatClient") ChatClient routerChatClient,
            @Qualifier("itineraryChatClient") ChatClient itineraryChatClient,
            @Qualifier("visaChatClient") ChatClient visaChatClient,
            @Qualifier("budgetChatClient") ChatClient budgetChatClient,
            @Qualifier("generalChatClient") ChatClient generalChatClient,
            @Qualifier("exportChatClient") ChatClient exportChatClient,
            UserProfileService userProfileService,
            ConversationService conversationService,
            ChatMessageRepository chatMessageRepository,
            com.learn.springai.config.LlmBulkheadManager llmBulkheadManager,
            TripRequestService tripRequestService,
            com.learn.springai.tool.RetrievalHelper retrievalHelper,
            TripPdfService tripPdfService,
            ObjectMapper objectMapper,
            PromptTemplateService promptTemplateService) {
        this.routerChatClient = routerChatClient;
        this.itineraryChatClient = itineraryChatClient;
        this.visaChatClient = visaChatClient;
        this.budgetChatClient = budgetChatClient;
        this.generalChatClient = generalChatClient;
        this.exportChatClient = exportChatClient;
        this.userProfileService = userProfileService;
        this.conversationService = conversationService;
        this.chatMessageRepository = chatMessageRepository;
        this.llmBulkheadManager = llmBulkheadManager;
        this.tripRequestService = tripRequestService;
        this.retrievalHelper = retrievalHelper;
        this.tripPdfService = tripPdfService;
        this.objectMapper = objectMapper;
        this.promptTemplateService = promptTemplateService;
    }

    private String classifyIntent(String message) {
        return llmBulkheadManager.executeWithGoogle(() -> {
            try {
                String classificationPrompt = promptTemplateService.getTemplate("classifyIntent.st");

                String response = routerChatClient.prompt()
                        .options(getRandomModelOptions())
                        .system(classificationPrompt)
                        .user(message)
                        .call()
                        .content();

                if (response != null) {
                    String clean = response.trim().toUpperCase();
                    if (clean.contains("ITINERARY"))
                        return "ITINERARY";
                    if (clean.contains("VISA"))
                        return "VISA";
                    if (clean.contains("BUDGET"))
                        return "BUDGET";
                }
            } catch (Exception e) {
                logger.error("Failed to classify user intent, defaulting to GENERAL", e);
            }
            return "GENERAL";
        });
    }

    private ChatClient selectChatClient(String intent) {
        logger.info("Routing query to agent path: {}", intent);
        return switch (intent) {
            case "ITINERARY" -> itineraryChatClient;
            case "VISA" -> visaChatClient;
            case "BUDGET" -> budgetChatClient;
            default -> generalChatClient;
        };
    }

    private String enrichMessageWithPreferences(String conversationId, String message) {
        Conversation conversation = conversationService.getConversation(conversationId);
        if (conversation == null || conversation.getUser() == null) {
            return message;
        }

        String userId = conversation.getUser().getId();
        // Asynchronously extract and store preference facts from current message
        userProfileService.extractAndStorePreferences(userId, message);

        // Retrieve user long-term preferences
        List<String> preferences = userProfileService.getUserPreferences(userId);

        StringBuilder enriched = new StringBuilder(message);

        // Retrieve current conversation specific TripRequest
        var tripReqOpt = tripRequestService.findEntityByConversationId(conversationId);
        if (tripReqOpt.isPresent()) {
            TripRequest tr = tripReqOpt.get();
            String serializedTrip = TripRequestPromptSerializer.serialize(tr);
            enriched.append("\n\n(Current Trip Configuration: ").append(serializedTrip).append(")");

            if (tr.getMaxBudget() != null) {
                enriched.append("\nCRITICAL BUDGET RULE: The traveler's fixed highest bar budget is exactly ")
                        .append(tr.getCurrency() != null ? tr.getCurrency() : "INR")
                        .append(" ")
                        .append(tr.getMaxBudget())
                        .append(". You must NEVER suggest, estimate, or recommend any itinerary, hotels, flights, or activities that would exceed this total budget constraint. Make sure all recommendations fit within this highest bar.");
            }
        }

        if (preferences != null && !preferences.isEmpty()) {
            enriched.append("\n\n(Context about the traveler's preference history for personalization: ")
                    .append(String.join(", ", preferences))
                    .append(")");
        }
        return enriched.toString();
    }

    public String chat(String conversationId, String message, String enrichedMessage) {
        String intent = classifyIntent(message);
        ChatClient targetClient = selectChatClient(intent);
        String finalMessage = enrichMessageWithPreferences(conversationId, enrichedMessage);

        String result = llmBulkheadManager.executeWithGoogle(() -> targetClient.prompt()
                .options(getRandomModelOptions())
                .advisors(spec -> spec
                        .param("chat_memory_conversation_id", conversationId)
                        .param("original_user_message", message))
                .user(finalMessage)
                .call()
                .content());

        postProcessAssistantMessage(conversationId);
        return result;
    }

    public Flux<String> chatStream(String conversationId, String message, String enrichedMessage) {
        String intent = classifyIntent(message);
        ChatClient targetClient = selectChatClient(intent);
        String finalMessage = enrichMessageWithPreferences(conversationId, enrichedMessage);

        Supplier<Flux<String>> streamSupplier = () -> targetClient.prompt()
                .options(getRandomModelOptions())
                .advisors(spec -> spec
                        .param("chat_memory_conversation_id", conversationId)
                        .param("original_user_message", message))
                .user(finalMessage)
                .stream()
                .content();

        // return llmBulkheadManager.executeStreamWithGroq(streamSupplier)
        return llmBulkheadManager.executeStreamWithGoogle(streamSupplier)
                .doOnComplete(() -> postProcessAssistantMessage(conversationId));
    }

    public Flux<org.springframework.http.codec.ServerSentEvent<String>> chatStreamSSE(
            String conversationId, String message, String enrichedMessage) {

        if (message != null && message.trim().equalsIgnoreCase("Generate Itinerary")) {
            return generateItineraryStream(conversationId);
        }

        var startEvent = org.springframework.http.codec.ServerSentEvent.builder("Analyzing intent...")
                .event("status")
                .build();

        String intent = classifyIntent(message);

        var routeEvent = org.springframework.http.codec.ServerSentEvent
                .builder("Routing to " + intent + " specialist...")
                .event("status")
                .build();

        ChatClient targetClient = selectChatClient(intent);
        String finalMessage = enrichMessageWithPreferences(conversationId, enrichedMessage);

        java.util.function.Function<String, Flux<org.springframework.http.codec.ServerSentEvent<String>>> createStream = (
                String modelToUse) -> {
            try {
                return targetClient.prompt()
                        .options(org.springframework.ai.chat.prompt.ChatOptions.builder().model(modelToUse).build())
                        .advisors(spec -> spec
                                .param("chat_memory_conversation_id", conversationId)
                                .param("original_user_message", message))
                        .user(finalMessage)
                        .stream()
                        .content()
                        .filter(token -> token != null && !token.isEmpty())
                        .map(token -> {
                            try {
                                String json = objectMapper.writeValueAsString(java.util.Map.of("content", token));
                                return org.springframework.http.codec.ServerSentEvent.builder(json)
                                        .event("text")
                                        .build();
                            } catch (Exception e) {
                                String escapedToken = token.replace("\\", "\\\\")
                                        .replace("\"", "\\\"")
                                        .replace("\n", "\\n")
                                        .replace("\r", "\\r")
                                        .replace("\t", "\\t");
                                return org.springframework.http.codec.ServerSentEvent
                                        .builder("{\"content\":\"" + escapedToken + "\"}")
                                        .event("text")
                                        .build();
                            }
                        })
                        .doOnComplete(() -> {
                            try {
                                postProcessAssistantMessage(conversationId);
                            } catch (Exception e) {
                                logger.warn("Post-processing assistant message error in chat stream", e);
                            }
                        });
            } catch (Exception ex) {
                logger.error("Error setting up stream supplier for conversation [{}] with model [{}]", conversationId,
                        modelToUse, ex);
                return Flux.error(ex);
            }
        };

        String selectedModel = getRandomModel();
        // Flux<org.springframework.http.codec.ServerSentEvent<String>> contentFlux =
        // llmBulkheadManager.executeStreamWithGroq(() ->
        // createStream.apply(selectedModel))
        Flux<org.springframework.http.codec.ServerSentEvent<String>> contentFlux = llmBulkheadManager
                .executeStreamWithGoogle(() -> createStream.apply(selectedModel))
                .onErrorResume(err -> {
                    logger.warn(
                            "Chat streaming error with model [{}], attempting fallback to [{}] for conversation [{}]",
                            selectedModel, PRIMARY_MODEL, conversationId, err);
                    if (!PRIMARY_MODEL.equals(selectedModel)) {
                        // return llmBulkheadManager.executeStreamWithGroq(() ->
                        // createStream.apply(PRIMARY_MODEL))
                        return llmBulkheadManager.executeStreamWithGoogle(() -> createStream.apply(PRIMARY_MODEL))
                                .onErrorResume(fallbackErr -> {
                                    logger.error("Fallback chat streaming error occurred for conversation [{}]",
                                            conversationId, fallbackErr);
                                    return buildFallbackErrorEvent(fallbackErr);
                                });
                    }
                    return buildFallbackErrorEvent(err);
                });

        return Flux.just(startEvent, routeEvent).concatWith(contentFlux);
    }

    private Flux<org.springframework.http.codec.ServerSentEvent<String>> buildFallbackErrorEvent(Throwable err) {
        String fallbackText = err.getMessage() != null
                && (err.getMessage().contains("429") || err.getMessage().toLowerCase().contains("rate limit"))
                        ? "I am currently handling a lot of travel requests. Please wait a moment and send your message again!"
                        : "I encountered a momentary issue processing your request. Please try again.";
        try {
            String json = objectMapper.writeValueAsString(java.util.Map.of("content", "\n\n" + fallbackText));
            return Flux.just(org.springframework.http.codec.ServerSentEvent.builder(json)
                    .event("text")
                    .build());
        } catch (Exception e) {
            return Flux.empty();
        }
    }

    public Flux<ServerSentEvent<String>> generateItineraryStream(String conversationId) {
        var startEvent = ServerSentEvent
                .builder("Gathering real-world details for destination...")
                .event("status")
                .build();

        return Flux.just(startEvent).concatWith(Flux.defer(() -> {
            try {
                TripRequest tripRequest = tripRequestService
                        .findByConversation_Id(conversationId)
                        .orElse(null);

                if (tripRequest == null) {
                    return Flux.just(ServerSentEvent.builder(
                            "{\"content\":\"Error: No travel preferences uploaded yet. Please tell me your destination and dates first!\"}")
                            .event("text")
                            .build());
                }

                String destination = tripRequest.getDestination();
                String source = tripRequest.getSource();
                String budgetPref = tripRequest.getBudgetPreference() != null ? tripRequest.getBudgetPreference().name()
                        : "MID";
                int totalDays = tripRequest.getTotalDays();
                String tripSummary = TripRequestPromptSerializer.serialize(tripRequest);

                List<String> hotelHints = retrievalHelper.search(destination + " hotels " + budgetPref, 3);
                List<String> activityHints = retrievalHelper.search(destination + " sightseeing attractions", 4);
                List<String> restaurantHints = retrievalHelper.search(destination + " local food restaurants", 3);
                List<String> transportHints = retrievalHelper
                        .search(source + " to " + destination + " flights transport", 2);
                List<String> countryHints = retrievalHelper.search(destination + " travel visa currency safety", 2);

                String destCtx = "Hotels: " + sanitizeHints(hotelHints) + "\n" +
                        "Activities: " + sanitizeHints(activityHints) + "\n" +
                        "Restaurants: " + sanitizeHints(restaurantHints) + "\n" +
                        "Transport: " + sanitizeHints(transportHints) + "\n" +
                        "Country/Visa: " + sanitizeHints(countryHints);

                int headcount = (tripRequest.getAdults() != null ? tripRequest.getAdults() : 1)
                        + (tripRequest.getChildren() != null ? tripRequest.getChildren() : 0);

                String frontMatter = String.format("""
                        ---
                        destination: %s
                        source: %s
                        start_date: %s
                        end_date: %s
                        total_days: %d
                        travellers: %d
                        budget: %s
                        ref_id: %s
                        ---
                        """,
                        destination, source,
                        tripRequest.getStartDate() != null ? tripRequest.getStartDate() : "TBD",
                        tripRequest.getEndDate() != null ? tripRequest.getEndDate() : "TBD",
                        totalDays, headcount, budgetPref, conversationId);

                StringBuilder fullMarkdownAccumulator = new StringBuilder();
                fullMarkdownAccumulator.append(frontMatter).append("\n");
                fullMarkdownAccumulator.append("# Trip Plan — ").append(destination).append("\n\n");

                List<String> rollingSummary = new ArrayList<>();
                List<String> dayMarkdowns = new ArrayList<>();

                Flux<ServerSentEvent<String>> daysFlux = Flux.empty();

                for (int dayNum = 1; dayNum <= totalDays; dayNum++) {
                    final int currentDay = dayNum;
                    String dayDate = tripRequest.getStartDate() != null
                            ? tripRequest.getStartDate().plusDays(currentDay - 1).toString()
                            : "TBD";

                    daysFlux = daysFlux.concatWith(Flux.defer(() -> {
                        String prevCtx = rollingSummary.isEmpty()
                                ? "This is Day 1 — arrival day."
                                : "Already covered: " + String.join(" → ", rollingSummary)
                                        + ". Do NOT repeat those activities.";

                        String dayPrompt = promptTemplateService.render("itineraryDayPrompt.st", Map.of(
                                "tripSummary", tripSummary.substring(0, Math.min(tripSummary.length(), 400)),
                                "destCtx", destCtx.substring(0, Math.min(destCtx.length(), 500)),
                                "prevCtx", prevCtx,
                                "currentDay", currentDay,
                                "totalDays", totalDays,
                                "dayDate", dayDate));

                        var dayStatusEvent = ServerSentEvent
                                .builder("Generating Day " + currentDay + " of " + totalDays + "...")
                                .event("status")
                                .build();

                        StringBuilder currentDayAccumulator = new StringBuilder();

                        String dayModel = getRandomModel();
                        // Flux<ServerSentEvent<String>> singleDayFlux =
                        // llmBulkheadManager.executeStreamWithGroq(() ->
                        Flux<ServerSentEvent<String>> singleDayFlux = llmBulkheadManager
                                .executeStreamWithGoogle(() -> exportChatClient.prompt()
                                        .options(org.springframework.ai.chat.prompt.ChatOptions.builder()
                                                .model(dayModel).build())
                                        .user(dayPrompt)
                                        .stream()
                                        .content())
                                .onErrorResume(err -> {
                                    logger.warn("Day generation error with model [{}], falling back to [{}]", dayModel,
                                            PRIMARY_MODEL, err);
                                    // return llmBulkheadManager.executeStreamWithGroq(() ->
                                    return llmBulkheadManager.executeStreamWithGoogle(() -> exportChatClient.prompt()
                                            .options(org.springframework.ai.chat.prompt.ChatOptions.builder()
                                                    .model(PRIMARY_MODEL).build())
                                            .user(dayPrompt)
                                            .stream()
                                            .content());
                                })
                                .map(token -> {
                                    currentDayAccumulator.append(token);
                                    String escapedToken = token.replace("\\", "\\\\")
                                            .replace("\"", "\\\"")
                                            .replace("\n", "\\n")
                                            .replace("\r", "\\r")
                                            .replace("\t", "\\t");
                                    return ServerSentEvent
                                            .builder("{\"content\":\"" + escapedToken + "\"}")
                                            .event("text")
                                            .build();
                                })
                                .doOnComplete(() -> {
                                    String dayMd = currentDayAccumulator.toString().replaceAll("```markdown\\s*", "")
                                            .replaceAll("```\\s*", "").trim();
                                    dayMarkdowns.add(dayMd);
                                    fullMarkdownAccumulator.append(dayMd).append("\n\n");

                                    String firstActivity = dayMd.lines()
                                            .filter(l -> l.trim().startsWith("- ") || l.trim().startsWith("* "))
                                            .findFirst()
                                            .map(l -> l.replaceAll("\\[([^\\]]+)\\]\\([^)]+\\)", "$1")
                                                    .replaceAll("\\*\\*|\\*", "")
                                                    .trim().substring(2))
                                            .orElse("sightseeing");
                                    if (firstActivity.length() > 40) {
                                        firstActivity = firstActivity.substring(0, 40);
                                    }
                                    rollingSummary.add(
                                            String.format("Day %d in %s (%s)", currentDay, destination, firstActivity));
                                });

                        return Flux.just(dayStatusEvent).concatWith(singleDayFlux);
                    }));
                }

                Flux<ServerSentEvent<String>> finalSectionFlux = Flux.defer(() -> {
                    String summaryOfDays = String.join("\n", rollingSummary);
                    String finalPrompt = promptTemplateService.render("itineraryOverviewPrompt.st", java.util.Map.of(
                            "tripSummary", tripSummary.substring(0, Math.min(tripSummary.length(), 300)),
                            "summaryOfDays", summaryOfDays,
                            "source", source,
                            "destination", destination,
                            "startDate",
                            tripRequest.getStartDate() != null ? tripRequest.getStartDate().toString() : "TBD",
                            "endDate", tripRequest.getEndDate() != null ? tripRequest.getEndDate().toString() : "TBD",
                            "totalDays", totalDays,
                            "headcount", headcount,
                            "budgetPref", budgetPref));

                    var summaryStatusEvent = ServerSentEvent
                            .builder("Finalising overview, transport details, and cost summary...")
                            .event("status")
                            .build();

                    StringBuilder finalSectionAccumulator = new StringBuilder();

                    // Flux<ServerSentEvent<String>>
                    // singleSummaryFlux = llmBulkheadManager.executeStreamWithGroq(() ->
                    Flux<ServerSentEvent<String>> singleSummaryFlux = llmBulkheadManager
                            .executeStreamWithGoogle(() -> exportChatClient.prompt()
                                    .options(getRandomModelOptions())
                                    .user(finalPrompt)
                                    .stream()
                                    .content())
                            .map(token -> {
                                finalSectionAccumulator.append(token);
                                String escapedToken = token.replace("\\", "\\\\")
                                        .replace("\"", "\\\"")
                                        .replace("\n", "\\n")
                                        .replace("\r", "\\r")
                                        .replace("\t", "\\t");
                                return ServerSentEvent
                                        .builder("{\"content\":\"" + escapedToken + "\"}")
                                        .event("text")
                                        .build();
                            })
                            .doOnComplete(() -> {
                                String summaryMd = finalSectionAccumulator.toString().replaceAll("```markdown\\s*", "")
                                        .replaceAll("```\\s*", "").trim();

                                StringBuilder finalFullMarkdown = new StringBuilder();
                                finalFullMarkdown.append(frontMatter).append("\n");
                                finalFullMarkdown.append("# Trip Plan — ").append(destination).append("\n\n");
                                finalFullMarkdown.append(summaryMd).append("\n\n");
                                finalFullMarkdown.append("## Day-by-Day Itinerary\n\n");
                                for (String dayMd : dayMarkdowns) {
                                    finalFullMarkdown.append(dayMd).append("\n\n");
                                }

                                try {
                                    String metaTrigger = "\n\n[PDF_DOWNLOAD_METADATA:{\"url\":\"/api/conversations/trips/"
                                            + conversationId + "/download\",\"destination\":\"" + destination + "\"}]";
                                    saveItineraryMessages(conversationId, finalFullMarkdown.toString() + metaTrigger,
                                            destination);
                                } catch (Exception e) {
                                    logger.error("Failed to save final messages", e);
                                }
                            });

                    return Flux.just(summaryStatusEvent).concatWith(singleSummaryFlux);
                });

                Flux<ServerSentEvent<String>> pdfDownloadEventFlux = Flux.defer(() -> {
                    String marker = "\n\n[PDF_DOWNLOAD_METADATA:{\"url\":\"/api/conversations/trips/" + conversationId
                            + "/download\",\"destination\":\"" + destination + "\"}]";
                    String escaped = marker.replace("\\", "\\\\").replace("\"", "\\\"");
                    return Flux.just(
                            ServerSentEvent.builder("{\"content\":\"" + escaped + "\"}")
                                    .event("text")
                                    .build());
                });

                return daysFlux.concatWith(finalSectionFlux).concatWith(pdfDownloadEventFlux);

            } catch (Exception e) {
                logger.error("Error setting up itinerary stream", e);
                return Flux.just(ServerSentEvent
                        .builder("{\"content\":\"Error setting up itinerary stream: " + e.getMessage() + "\"}")
                        .event("text")
                        .build());
            }
        }));
    }

    private void saveItineraryMessages(String conversationId, String finalMarkdown, String destination) {
        try {
            Conversation conversation = conversationService.getConversation(conversationId);
            Integer lastSequence = chatMessageRepository.findMaxSequenceByConversationId(conversationId);
            if (lastSequence == null)
                lastSequence = -1;

            chatMessageRepository.save(
                    com.learn.springai.model.ChatMessage.builder()
                            .conversation(conversation)
                            .role("USER")
                            .content("Generate Itinerary")
                            .sequenceNumber(++lastSequence)
                            .messageTimestamp(LocalDateTime.now())
                            .deleted(false)
                            .build());

            com.learn.springai.model.ChatMessage assistantMsg = com.learn.springai.model.ChatMessage.builder()
                    .conversation(conversation)
                    .role("ASSISTANT")
                    .content(finalMarkdown)
                    .sequenceNumber(++lastSequence)
                    .messageTimestamp(LocalDateTime.now())
                    .deleted(false)
                    .build();

            String cleanMarkdown = finalMarkdown;
            if (finalMarkdown.contains("[PDF_DOWNLOAD_METADATA:")) {
                int start = finalMarkdown.indexOf("[PDF_DOWNLOAD_METADATA:");
                int end = finalMarkdown.indexOf("]", start);
                if (end > start) {
                    String json = finalMarkdown.substring(start + "[PDF_DOWNLOAD_METADATA:".length(), end).trim();
                    assistantMsg.setMessageType("PDF_DOWNLOAD");
                    assistantMsg.setMetadataJson(json);
                    cleanMarkdown = finalMarkdown.replace(finalMarkdown.substring(start, end + 1), "").trim();
                    assistantMsg.setContent(cleanMarkdown);
                }
            }

            boolean isPublic = conversation != null && Boolean.TRUE.equals(conversation.getIsPublic());
            try {
                tripPdfService.generateAndSaveFromMarkdown(conversationId, cleanMarkdown, destination, isPublic);
            } catch (Exception e) {
                logger.error("Failed to compile and save PDF from streaming markdown", e);
            }

            chatMessageRepository.save(assistantMsg);
            chatMessageRepository.flush();

            extractAndSaveTripRequestDetails(conversationId, cleanMarkdown);

            java.util.Optional<com.learn.springai.model.TripRequest> tripReqOpt = tripRequestService
                    .findEntityByConversationId(conversationId);
            if (tripReqOpt.isPresent()) {
                com.learn.springai.model.TripRequest tr = tripReqOpt.get();
                String src = tr.getSource() != null ? tr.getSource().trim() : "";
                String dest = tr.getDestination() != null ? tr.getDestination().trim()
                        : (destination != null ? destination.trim() : "");
                String duration = tr.getTotalDays() > 0 ? " (" + tr.getTotalDays() + "d)" : "";
                String newTitle = !src.isBlank() && !dest.isBlank()
                        ? src + " → " + dest + duration
                        : (!dest.isBlank() ? "Trip to " + dest + duration : "New Trip");
                conversation.setTitle(newTitle);
            } else if (conversation.getTitle() == null || conversation.getTitle().equalsIgnoreCase("New Chat")
                    || conversation.getTitle().startsWith("Itinerary — ")
                    || conversation.getTitle().startsWith("Provided information of trip")) {
                conversation.setTitle(!destination.isBlank() ? "Trip to " + destination : "New Trip");
            }
            conversation.setLastUpdated(LocalDateTime.now());
            conversationService.updateConversation(conversation);
        } catch (Exception e) {
            logger.error("Failed to save itinerary messages", e);
        }
    }

    private String sanitizeHints(List<String> hints) {
        if (hints == null || hints.isEmpty())
            return "N/A";
        return hints.stream()
                .map(h -> {
                    if (h == null)
                        return "";
                    String firstLine = h.lines()
                            .filter(l -> !l.isBlank())
                            .findFirst()
                            .orElse(h);
                    return firstLine.length() > 80 ? firstLine.substring(0, 80) : firstLine;
                })
                .filter(h -> !h.isBlank())
                .collect(java.util.stream.Collectors.joining(" | "));
    }

    private void postProcessAssistantMessage(String conversationId) {
        try {
            List<com.learn.springai.model.ChatMessage> msgs = chatMessageRepository
                    .findTop20ByConversationIdAndDeletedFalseOrderBySequenceNumberDesc(conversationId);
            if (msgs.isEmpty())
                return;

            com.learn.springai.model.ChatMessage latestMsg = msgs.stream()
                    .filter(m -> "ASSISTANT".equals(m.getRole()))
                    .findFirst()
                    .orElse(null);

            if (latestMsg == null)
                return;

            String text = latestMsg.getContent();
            if (text == null)
                return;

            extractAndSaveTripRequestDetails(conversationId, text);

            if (text.contains("[HOTEL_RECOMMENDATION_METADATA:")) {
                int start = text.indexOf("[HOTEL_RECOMMENDATION_METADATA:");
                int end = text.indexOf("]", start);
                if (end > start) {
                    String json = text.substring(start + "[HOTEL_RECOMMENDATION_METADATA:".length(), end).trim();
                    latestMsg.setMessageType("HOTEL_LIST");
                    latestMsg.setMetadataJson(json);
                    latestMsg.setContent(text.replace(text.substring(start, end + 1), "").trim());
                    chatMessageRepository.save(latestMsg);
                }
            } else if (text.contains("[VISA_ALERT_METADATA:")) {
                int start = text.indexOf("[VISA_ALERT_METADATA:");
                int end = text.indexOf("]", start);
                if (end > start) {
                    String json = text.substring(start + "[VISA_ALERT_METADATA:".length(), end).trim();
                    latestMsg.setMessageType("VISA_ALERT");
                    latestMsg.setMetadataJson(json);
                    latestMsg.setContent(text.replace(text.substring(start, end + 1), "").trim());
                    chatMessageRepository.save(latestMsg);
                }
            } else if ((text.contains("Day 1") || text.contains("Day 01") || text.contains("## Day 1")
                    || text.contains("### Day 1")) &&
                    (text.contains("Day 2") || text.contains("Day 02") || text.contains("Accommodation")
                            || text.contains("Meals") || text.contains("Cost Breakdown"))) {
                latestMsg.setMessageType("PDF_DOWNLOAD");
                chatMessageRepository.save(latestMsg);
                try {
                    Conversation conv = conversationService.getConversation(conversationId);
                    String dest = "Custom Itinerary";
                    if (conv != null && conv.getTitle() != null && !conv.getTitle().equalsIgnoreCase("New Chat")) {
                        dest = conv.getTitle().replace("Trip to ", "").replaceAll("\\(.*\\)", "").trim();
                    }
                    boolean isPublic = conv != null && Boolean.TRUE.equals(conv.getIsPublic());
                    tripPdfService.generateAndSaveFromMarkdown(conversationId, text, dest, isPublic);
                    logger.info("Auto-compiled PDF for conversation [{}] with detected itinerary", conversationId);
                } catch (Exception pdfErr) {
                    logger.warn("Could not auto-compile PDF in postProcessAssistantMessage: {}", pdfErr.getMessage());
                }
            }
        } catch (Exception e) {
            logger.error("Failed to post-process assistant message for metadata", e);
        }
    }

    public void extractAndSaveTripRequestDetails(String conversationId, String content) {
        try {
            logger.info("Extracting structured trip details for conversation [{}]", conversationId);
            String systemInstruction = promptTemplateService.getTemplate("extractTripDetails.st");

            String jsonResult = llmBulkheadManager.executeWithGoogle(() -> generalChatClient.prompt()
                    .options(getRandomModelOptions())
                    .system(systemInstruction)
                    .user("Itinerary/Conversation Content:\n" + content)
                    .call()
                    .content());

            if (jsonResult != null && !jsonResult.trim().isEmpty()) {
                String cleanJson = jsonResult.trim();

                // Find first '{' and last '}' to extract raw JSON block
                int firstBrace = cleanJson.indexOf('{');
                int lastBrace = cleanJson.lastIndexOf('}');
                if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
                    cleanJson = cleanJson.substring(firstBrace, lastBrace + 1);
                }

                try {
                    TripRequestUpdateDTO patch = objectMapper.readValue(cleanJson, TripRequestUpdateDTO.class);

                    // Fetch existing trip request to merge/append places instead of clearing
                    java.util.Optional<com.learn.springai.model.TripRequest> existingOpt = tripRequestService
                            .findEntityByConversationId(conversationId);
                    if (existingOpt.isPresent()) {
                        com.learn.springai.model.TripRequest existing = existingOpt.get();

                        if (patch.getMustVisitPlaces() != null && !patch.getMustVisitPlaces().isEmpty()) {
                            java.util.Set<String> mergedMustVisit = new java.util.HashSet<>();
                            if (existing.getMustVisitPlaces() != null) {
                                mergedMustVisit.addAll(existing.getMustVisitPlaces());
                            }
                            mergedMustVisit.addAll(patch.getMustVisitPlaces());
                            patch.setMustVisitPlaces(mergedMustVisit);
                        }

                        if (patch.getAvoidPlaces() != null && !patch.getAvoidPlaces().isEmpty()) {
                            java.util.Set<String> mergedAvoid = new java.util.HashSet<>();
                            if (existing.getAvoidPlaces() != null) {
                                mergedAvoid.addAll(existing.getAvoidPlaces());
                            }
                            mergedAvoid.addAll(patch.getAvoidPlaces());
                            patch.setAvoidPlaces(mergedAvoid);
                        }
                    }

                    // Only apply update if there is at least one non-null field extracted
                    if (patch.getAdults() != null || patch.getChildren() != null || patch.getTravellerType() != null ||
                            patch.getCurrency() != null || patch.getMaxBudget() != null
                            || patch.getBudgetPreference() != null ||
                            patch.getMinHotelStars() != null || patch.getMaxHotelStars() != null
                            || patch.getCabinClass() != null ||
                            (patch.getMustVisitPlaces() != null && !patch.getMustVisitPlaces().isEmpty()) ||
                            (patch.getAvoidPlaces() != null && !patch.getAvoidPlaces().isEmpty())) {

                        tripRequestService.update(conversationId, patch);
                        logger.info("Successfully extracted and auto-updated TripRequest for conversation [{}]",
                                conversationId);
                    }
                } catch (Exception ex) {
                    logger.error("Failed to parse extracted JSON in extractAndSaveTripRequestDetails", ex);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to extract trip request details for conversation [" + conversationId + "]", e);
        }
    }
}
