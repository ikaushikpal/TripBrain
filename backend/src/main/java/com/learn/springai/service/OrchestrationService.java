package com.learn.springai.service;

import java.util.List;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import com.learn.springai.model.Conversation;
import com.learn.springai.model.TripRequest;
import com.learn.springai.model.TripPdf;
import com.learn.springai.model.ChatMessage;
import com.learn.springai.repository.ChatMessageRepository;
import reactor.core.publisher.Flux;
import java.time.LocalDateTime;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learn.springai.dto.tripRequest.TripRequestUpdateDTO;

@Service
public class OrchestrationService {

    private static final List<String> MODELS_POOL = List.of(
        "llama-3.1-8b-instant",
        "llama-3.3-70b-versatile",
        "openai/gpt-oss-20b",
        "openai/gpt-oss-120b"
    );
    private static final java.util.Random RANDOM = new java.util.Random();

    private String getRandomModel() {
        return MODELS_POOL.get(RANDOM.nextInt(MODELS_POOL.size()));
    }

    private org.springframework.ai.chat.prompt.ChatOptions getRandomModelOptions() {
        String model = getRandomModel();
        logger.info("Load-balancing: dynamically selected Groq model = {}", model);
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
            ObjectMapper objectMapper) {
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
    }

    private String classifyIntent(String message) {
        return llmBulkheadManager.executeWithGroq(() -> {
            try {
                String classificationPrompt = """
                    You are a routing agent for a travel system. Classify the user prompt into one of the categories:
                    1. ITINERARY: For queries asking about destinations, planning itineraries, daily stop schedules, weather, packing lists, attractions.
                    2. VISA: For queries checking visa rules, passport strength, travel eligibility, entry requirements.
                    3. BUDGET: For currency conversions, cost calculations, exchange rates.
                    4. GENERAL: For general conversational greetings, chit-chat, preferences customization.

                    Respond ONLY with one word: ITINERARY, VISA, BUDGET, or GENERAL.
                    """;

                String response = routerChatClient.prompt()
                        .options(getRandomModelOptions())
                        .system(classificationPrompt)
                        .user(message)
                        .call()
                        .content();

                if (response != null) {
                    String clean = response.trim().toUpperCase();
                    if (clean.contains("ITINERARY")) return "ITINERARY";
                    if (clean.contains("VISA")) return "VISA";
                    if (clean.contains("BUDGET")) return "BUDGET";
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
            com.learn.springai.model.TripRequest tr = tripReqOpt.get();
            String serializedTrip = com.learn.springai.config.TripRequestPromptSerializer.serialize(tr);
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

        String result = llmBulkheadManager.executeWithGroq(() -> targetClient.prompt()
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

        return llmBulkheadManager.executeStreamWithGroq(streamSupplier)
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

        var routeEvent = org.springframework.http.codec.ServerSentEvent.builder("Routing to " + intent + " specialist...")
                .event("status")
                .build();

        ChatClient targetClient = selectChatClient(intent);
        String finalMessage = enrichMessageWithPreferences(conversationId, enrichedMessage);

        Supplier<Flux<org.springframework.http.codec.ServerSentEvent<String>>> streamSupplier = () -> targetClient.prompt()
                .options(getRandomModelOptions())
                .advisors(spec -> spec
                        .param("chat_memory_conversation_id", conversationId)
                        .param("original_user_message", message))
                .user(finalMessage)
                .stream()
                .content()
                .map(token -> {
                    String escapedToken = token.replace("\\", "\\\\")
                                               .replace("\"", "\\\"")
                                               .replace("\n", "\\n")
                                               .replace("\r", "\\r")
                                               .replace("\t", "\\t");
                    String json = "{\"content\":\"" + escapedToken + "\"}";
                    return org.springframework.http.codec.ServerSentEvent.builder(json)
                            .event("text")
                            .build();
                })
                .doOnComplete(() -> postProcessAssistantMessage(conversationId));

        return Flux.just(startEvent, routeEvent)
                .concatWith(llmBulkheadManager.executeStreamWithGroq(streamSupplier));
    }

    public Flux<org.springframework.http.codec.ServerSentEvent<String>> generateItineraryStream(String conversationId) {
        var startEvent = org.springframework.http.codec.ServerSentEvent.builder("Gathering real-world details for destination...")
                .event("status")
                .build();

        return Flux.just(startEvent).concatWith(Flux.defer(() -> {
            try {
                TripRequest tripRequest = tripRequestService
                        .findByConversation_Id(conversationId)
                        .orElse(null);

                if (tripRequest == null) {
                    return Flux.just(org.springframework.http.codec.ServerSentEvent.builder("{\"content\":\"Error: No travel preferences uploaded yet. Please tell me your destination and dates first!\"}")
                            .event("text")
                            .build());
                }

                String destination = tripRequest.getDestination();
                String source = tripRequest.getSource();
                String budgetPref = tripRequest.getBudgetPreference() != null ? tripRequest.getBudgetPreference().name() : "MID";
                int totalDays = tripRequest.getTotalDays();
                String tripSummary = com.learn.springai.config.TripRequestPromptSerializer.serialize(tripRequest);

                List<String> hotelHints = retrievalHelper.search(destination + " hotels " + budgetPref, 3);
                List<String> activityHints = retrievalHelper.search(destination + " sightseeing attractions", 4);
                List<String> restaurantHints = retrievalHelper.search(destination + " local food restaurants", 3);
                List<String> transportHints = retrievalHelper.search(source + " to " + destination + " flights transport", 2);
                List<String> countryHints = retrievalHelper.search(destination + " travel visa currency safety", 2);

                String destCtx =
                        "Hotels: " + sanitizeHints(hotelHints) + "\n" +
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

                java.util.List<String> rollingSummary = new java.util.ArrayList<>();
                java.util.List<String> dayMarkdowns = new java.util.ArrayList<>();

                Flux<org.springframework.http.codec.ServerSentEvent<String>> daysFlux = Flux.empty();

                for (int dayNum = 1; dayNum <= totalDays; dayNum++) {
                    final int currentDay = dayNum;
                    String dayDate = tripRequest.getStartDate() != null
                            ? tripRequest.getStartDate().plusDays(currentDay - 1).toString() : "TBD";

                    daysFlux = daysFlux.concatWith(Flux.defer(() -> {
                        String prevCtx = rollingSummary.isEmpty()
                                ? "This is Day 1 — arrival day."
                                : "Already covered: " + String.join(" → ", rollingSummary) + ". Do NOT repeat those activities.";

                        String dayPrompt = String.format("""
                                Trip: %s
                                
                                Reference data (use for inspiration, do NOT quote verbatim):
                                %s
                                
                                %s
                                
                                Write ONLY the Markdown itinerary section for Day %d of %d (%s).
                                
                                Use this exact structure:
                                ### Day %d — [City Name] (%s)
                                **Weather:** [condition, temp range, rain chance]
                                
                                **Activities:**
                                - [Activity name]([Google Maps URL]) — [duration], ₹[cost] — [1-line note]
                                - [Activity name]([Google Maps URL]) — [duration], ₹[cost] — [1-line note]
                                
                                **Meals:**
                                - Breakfast: [Restaurant name] — [cuisine] — ₹[cost]
                                - Lunch: [Restaurant name] — [cuisine] — ₹[cost]
                                - Dinner: [Restaurant name] — [cuisine] — ₹[cost]
                                
                                **Local Transport:** [from → to, mode, ₹cost]
                                
                                | Category | Cost (₹) |
                                |---|---|
                                | Activities | [amount] |
                                | Food | [amount] |
                                | Transport | [amount] |
                                | **Day Total** | **[amount]** |
                                
                                Output ONLY the Markdown above. No JSON. No preamble. Start with '###'.
                                """,
                                tripSummary.substring(0, Math.min(tripSummary.length(), 400)),
                                destCtx.substring(0, Math.min(destCtx.length(), 500)),
                                prevCtx,
                                currentDay, totalDays, dayDate,
                                currentDay, dayDate
                        );

                        var dayStatusEvent = org.springframework.http.codec.ServerSentEvent.builder("Generating Day " + currentDay + " of " + totalDays + "...")
                                .event("status")
                                .build();

                        StringBuilder currentDayAccumulator = new StringBuilder();

                        Flux<org.springframework.http.codec.ServerSentEvent<String>> singleDayFlux = llmBulkheadManager.executeStreamWithGroq(() ->
                                exportChatClient.prompt()
                                        .options(getRandomModelOptions())
                                        .user(dayPrompt)
                                        .stream()
                                        .content()
                        )
                        .map(token -> {
                            currentDayAccumulator.append(token);
                            String escapedToken = token.replace("\\", "\\\\")
                                                       .replace("\"", "\\\"")
                                                       .replace("\n", "\\n")
                                                       .replace("\r", "\\r")
                                                       .replace("\t", "\\t");
                            return org.springframework.http.codec.ServerSentEvent.builder("{\"content\":\"" + escapedToken + "\"}")
                                    .event("text")
                                    .build();
                        })
                        .doOnComplete(() -> {
                            String dayMd = currentDayAccumulator.toString().replaceAll("```markdown\\s*", "").replaceAll("```\\s*", "").trim();
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
                            rollingSummary.add(String.format("Day %d in %s (%s)", currentDay, destination, firstActivity));
                        });

                        return Flux.just(dayStatusEvent).concatWith(singleDayFlux);
                    }));
                }

                Flux<org.springframework.http.codec.ServerSentEvent<String>> finalSectionFlux = Flux.defer(() -> {
                    String summaryOfDays = String.join("\n", rollingSummary);
                    String finalPrompt = String.format("""
                            Trip: %s
                            
                            Days summary:
                            %s
                            
                            Write the following Markdown sections (in order). No JSON. No preamble.
                            
                            ## Overview
                            | Field | Value |
                            |---|---|
                            | From | %s |
                            | To | %s |
                            | Dates | %s → %s |
                            | Duration | %d days |
                            | Travellers | %d |
                            | Budget | %s |
                            
                            ## Flights, Trains & Inter-city Transport
                            | Leg | Route | Date | Mode | Operator | Depart | Arrive | Duration | Cost (₹) | Book |
                            |---|---|---|---|---|---|---|---|---|---|
                            | 1 | [from → to] | [date] | [mode] | [airline or train name] | [time] | [time] | [Xh] | [amount] | [Book]([URL]) |
                            | 2 | [return] | [date] | [mode] | [airline or train name] | [time] | [time] | [Xh] | [amount] | [Book]([URL]) |
                            
                            ## Accommodation
                            | City | Hotel | Stars | Check-in | Check-out | Nights | Per Night (₹) | Total (₹) | Book |
                            |---|---|---|---|---|---|---|---|---|
                            | [city] | [name]([booking URL]) | [★★★] | [date] | [date] | [n] | [rate] | [total] | [Book]([URL]) |
                            
                            ## Cost Summary
                            | Category | Amount (₹) |
                            |---|---|
                            | Flights | [amount] |
                            | Hotels | [amount] |
                            | Food (all days) | [amount] |
                            | Activities (all days) | [amount] |
                            | Local Transport | [amount] |
                            | **Grand Total** | **[amount]** |
                            | Budget Remaining | [amount] |
                            
                            ## Travel Tips & Warnings
                            - [Visa / entry requirement tip]
                            - [Weather / packing tip]
                            - [Currency / payment tip]
                            
                            ## Summary & Closing Thoughts
                            [Provide a thoughtful travel summary and closure notes here. Mention local customs, culture, safety guidelines, and general recommendations for an excellent trip.]

                            CRITICAL FORMATTING INSTRUCTIONS:
                            1. You MUST generate ALL the sections and tables listed above. Do NOT omit any table.
                            2. Every table MUST contain all columns specified. If data is unknown or not set, use 'N/A' or estimated values.
                            3. In the Flights, Trains & Inter-city Transport table, include any inter-city rail or flight options as relevant.
                            4. Output ONLY valid Markdown. Do not enclose the output in code fences (e.g. ```markdown ... ```).
                            
                            Output ONLY the Markdown. Start with '## Overview'.
                            """,
                            tripSummary.substring(0, Math.min(tripSummary.length(), 300)),
                            summaryOfDays,
                            source, destination,
                            tripRequest.getStartDate() != null ? tripRequest.getStartDate().toString() : "TBD",
                            tripRequest.getEndDate() != null ? tripRequest.getEndDate().toString() : "TBD",
                            totalDays, headcount, budgetPref
                    );

                    var summaryStatusEvent = org.springframework.http.codec.ServerSentEvent.builder("Finalising overview, transport details, and cost summary...")
                            .event("status")
                            .build();

                    StringBuilder finalSectionAccumulator = new StringBuilder();

                    Flux<org.springframework.http.codec.ServerSentEvent<String>> singleSummaryFlux = llmBulkheadManager.executeStreamWithGroq(() ->
                            exportChatClient.prompt()
                                    .options(getRandomModelOptions())
                                    .user(finalPrompt)
                                    .stream()
                                    .content()
                    )
                    .map(token -> {
                        finalSectionAccumulator.append(token);
                        String escapedToken = token.replace("\\", "\\\\")
                                                   .replace("\"", "\\\"")
                                                   .replace("\n", "\\n")
                                                   .replace("\r", "\\r")
                                                   .replace("\t", "\\t");
                        return org.springframework.http.codec.ServerSentEvent.builder("{\"content\":\"" + escapedToken + "\"}")
                                .event("text")
                                .build();
                    })
                    .doOnComplete(() -> {
                        String summaryMd = finalSectionAccumulator.toString().replaceAll("```markdown\\s*", "").replaceAll("```\\s*", "").trim();

                        StringBuilder finalFullMarkdown = new StringBuilder();
                        finalFullMarkdown.append(frontMatter).append("\n");
                        finalFullMarkdown.append("# Trip Plan — ").append(destination).append("\n\n");
                        finalFullMarkdown.append(summaryMd).append("\n\n");
                        finalFullMarkdown.append("## Day-by-Day Itinerary\n\n");
                        for (String dayMd : dayMarkdowns) {
                            finalFullMarkdown.append(dayMd).append("\n\n");
                        }

                        try {
                            String metaTrigger = "\n\n[PDF_DOWNLOAD_METADATA:{\"url\":\"/api/conversations/trips/" + conversationId + "/download\",\"destination\":\"" + destination + "\"}]";
                            saveItineraryMessages(conversationId, finalFullMarkdown.toString() + metaTrigger, destination);
                        } catch (Exception e) {
                            logger.error("Failed to save final messages", e);
                        }
                    });

                    return Flux.just(summaryStatusEvent).concatWith(singleSummaryFlux);
                });

                Flux<org.springframework.http.codec.ServerSentEvent<String>> pdfDownloadEventFlux = Flux.defer(() -> {
                    String marker = "\n\n[PDF_DOWNLOAD_METADATA:{\"url\":\"/api/conversations/trips/" + conversationId + "/download\",\"destination\":\"" + destination + "\"}]";
                    String escaped = marker.replace("\\", "\\\\").replace("\"", "\\\"");
                    return Flux.just(org.springframework.http.codec.ServerSentEvent.builder("{\"content\":\"" + escaped + "\"}")
                            .event("text")
                            .build());
                });

                return daysFlux.concatWith(finalSectionFlux).concatWith(pdfDownloadEventFlux);

            } catch (Exception e) {
                logger.error("Error setting up itinerary stream", e);
                return Flux.just(org.springframework.http.codec.ServerSentEvent.builder("{\"content\":\"Error setting up itinerary stream: " + e.getMessage() + "\"}")
                        .event("text")
                        .build());
            }
        }));
    }

    private void saveItineraryMessages(String conversationId, String finalMarkdown, String destination) {
        try {
            Conversation conversation = conversationService.getConversation(conversationId);
            Integer lastSequence = chatMessageRepository.findMaxSequenceByConversationId(conversationId);
            if (lastSequence == null) lastSequence = -1;

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

            java.util.Optional<com.learn.springai.model.TripRequest> tripReqOpt = tripRequestService.findEntityByConversationId(conversationId);
            if (tripReqOpt.isPresent()) {
                com.learn.springai.model.TripRequest tr = tripReqOpt.get();
                String src = tr.getSource() != null ? tr.getSource() : "unknown";
                String dest = tr.getDestination() != null ? tr.getDestination() : (destination != null ? destination : "unknown");
                int peopleCount = tr.getAdults() + (tr.getChildren() != null ? tr.getChildren() : 0);
                String type = tr.getTravellerType() != null ? tr.getTravellerType().name() : "SOLO";
                String newTitle = src.toLowerCase().trim() + "-" + dest.toLowerCase().trim() + "-" + peopleCount + "-" + type.toLowerCase().trim();
                conversation.setTitle(newTitle);
            } else if (conversation.getTitle() == null || conversation.getTitle().equalsIgnoreCase("New Chat") || conversation.getTitle().startsWith("Itinerary — ") || conversation.getTitle().startsWith("Provided information of trip")) {
                conversation.setTitle("Itinerary — " + (destination != null ? destination : "Trip"));
            }
            conversation.setLastUpdated(LocalDateTime.now());
            conversationService.updateConversation(conversation);
        } catch (Exception e) {
            logger.error("Failed to save itinerary messages", e);
        }
    }

    private String sanitizeHints(List<String> hints) {
        if (hints == null || hints.isEmpty()) return "N/A";
        return hints.stream()
                .map(h -> {
                    if (h == null) return "";
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
            if (msgs.isEmpty()) return;

            com.learn.springai.model.ChatMessage latestMsg = msgs.stream()
                    .filter(m -> "ASSISTANT".equals(m.getRole()))
                    .findFirst()
                    .orElse(null);

            if (latestMsg == null) return;

            String text = latestMsg.getContent();
            if (text == null) return;

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
            }
        } catch (Exception e) {
            logger.error("Failed to post-process assistant message for metadata", e);
        }
    }

    private void extractAndSaveTripRequestDetails(String conversationId, String content) {
        try {
            logger.info("Extracting structured trip details for conversation [{}]", conversationId);
            String systemInstruction = """
                    You are a data extraction agent. Analyze the provided conversation snippet or itinerary to extract the structured trip parameters.
                    You MUST return ONLY a JSON block containing the following fields:
                    - 'adults': Integer
                    - 'children': Integer
                    - 'travellerType': String (exactly one of: SOLO, COUPLE, HONEYMOON, FAMILY_WITH_KIDS, GROUP_FRIENDS)
                    - 'currency': String (e.g. INR, USD)
                    - 'maxBudget': Double
                    - 'budgetPreference': String (exactly one of: BACKPACKER, MID, LUXURY)
                    - 'minHotelStars': Integer
                    - 'maxHotelStars': Integer
                    - 'cabinClass': String (exactly one of: ECONOMY, BUSINESS)
                    - 'mustVisitPlaces': Array of Strings (places explicitly mentioned to visit or include)
                    - 'avoidPlaces': Array of Strings (places explicitly mentioned to avoid)
                    
                    Return null or omit any field that is NOT mentioned, NOT changed, or NOT present in the content.
                    Do not add any preamble, conversational text, explanations, or code blocks. Just return the raw JSON object.
                    """;

            String jsonResult = llmBulkheadManager.executeWithGroq(() -> generalChatClient.prompt()
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
                    java.util.Optional<com.learn.springai.model.TripRequest> existingOpt = tripRequestService.findEntityByConversationId(conversationId);
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
                        patch.getCurrency() != null || patch.getMaxBudget() != null || patch.getBudgetPreference() != null ||
                        patch.getMinHotelStars() != null || patch.getMaxHotelStars() != null || patch.getCabinClass() != null ||
                        (patch.getMustVisitPlaces() != null && !patch.getMustVisitPlaces().isEmpty()) ||
                        (patch.getAvoidPlaces() != null && !patch.getAvoidPlaces().isEmpty())) {
                        
                        tripRequestService.update(conversationId, patch);
                        logger.info("Successfully extracted and auto-updated TripRequest for conversation [{}]", conversationId);
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
