package com.learn.springai.controller;

import static org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import javax.validation.Valid;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learn.springai.config.TripRequestPromptSerializer;
import com.learn.springai.dto.tripRequest.TripRequestDTO;
import com.learn.springai.model.TripPdf;
import com.learn.springai.model.TripRequest;
import com.learn.springai.service.TripPdfService;
import com.learn.springai.service.TripRequestService;
import com.learn.springai.service.OrchestrationService;
import com.learn.springai.service.ConversationService;
import com.learn.springai.tool.RetrievalHelper;
import com.learn.springai.service.UserRateLimiterService;
import com.learn.springai.service.UserService;
import com.learn.springai.model.Conversation;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private Resource enricherTemplate;
    private final ChatClient chatClient;
    private final ChatClient exportChatClient;
    private final TripRequestService tripRequestService;
    private final TripPdfService tripPdfService;
    private final ObjectMapper objectMapper;
    private final OrchestrationService orchestrationService;
    private final ConversationService conversationService;
    private final UserRateLimiterService userRateLimiterService;
    private final UserService userService;
    private final com.learn.springai.config.LlmBulkheadManager llmBulkheadManager;
    private final RetrievalHelper retrievalHelper;

    public ChatController(
            @Qualifier("groqChatClient") ChatClient chatClient,
            @Qualifier("exportChatClient") ChatClient exportChatClient,
            TripRequestService tripRequestService,
            TripPdfService tripPdfService,
            ObjectMapper objectMapper,
            OrchestrationService orchestrationService,
            ConversationService conversationService,
            UserRateLimiterService userRateLimiterService,
            UserService userService,
            com.learn.springai.config.LlmBulkheadManager llmBulkheadManager,
            RetrievalHelper retrievalHelper,
            @Value("classpath:/promptTemplates/userMessageEnricher.st") Resource enricherTemplate) {

        this.chatClient = chatClient;
        this.exportChatClient = exportChatClient;
        this.tripRequestService = tripRequestService;
        this.tripPdfService = tripPdfService;
        this.objectMapper = objectMapper;
        this.orchestrationService = orchestrationService;
        this.conversationService = conversationService;
        this.userRateLimiterService = userRateLimiterService;
        this.userService = userService;
        this.llmBulkheadManager = llmBulkheadManager;
        this.retrievalHelper = retrievalHelper;
        this.enricherTemplate = enricherTemplate;
    }

    @GetMapping("/{conversationId}")
    public ResponseEntity<String> chat(
            @PathVariable String conversationId,
            @RequestParam String message) {

        Conversation conversation = conversationService.getConversation(conversationId);
        if (conversation != null && conversation.getUser() != null) {
            if (!userRateLimiterService.isAllowed(conversation.getUser().getId())) {
                throw new ResponseStatusException(
                        org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                        "Rate limit exceeded. Please wait a minute before sending more queries.");
            }
            userService.incrementApiCallCount(conversation.getUser().getId());
        }

        String enrichedMessage = buildEnrichedMessage(conversationId, message);
        String response = orchestrationService.chat(conversationId, message, enrichedMessage);
        return ResponseEntity.ok(response);
    }

    @GetMapping(value = "/{conversationId}/stream", produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    public reactor.core.publisher.Flux<org.springframework.http.codec.ServerSentEvent<String>> chatStream(
            @PathVariable String conversationId,
            @RequestParam String message) {

        Conversation conversation = conversationService.getConversation(conversationId);
        if (conversation != null && conversation.getUser() != null) {
            if (!userRateLimiterService.isAllowed(conversation.getUser().getId())) {
                throw new ResponseStatusException(
                        org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                        "Rate limit exceeded. Please wait a minute before sending more queries.");
            }
            userService.incrementApiCallCount(conversation.getUser().getId());
        }

        String enrichedMessage = buildEnrichedMessage(conversationId, message);
        return orchestrationService.chatStreamSSE(conversationId, message, enrichedMessage);
    }

    @PostMapping("/{conversationId}/uploadPreferences")
    public ResponseEntity<String> uploadTripPreferences(
            @PathVariable("conversationId") String conversationId,
            @Valid @RequestBody TripRequestDTO tripRequestDTO) {

        Conversation conversation = conversationService.getConversation(conversationId);
        if (conversation != null && conversation.getUser() != null) {
            if (!userRateLimiterService.isAllowed(conversation.getUser().getId())) {
                throw new ResponseStatusException(
                        org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                        "Rate limit exceeded. Please wait a minute before sending more queries.");
            }
            userService.incrementApiCallCount(conversation.getUser().getId());
        }

        TripRequest saved = tripRequestService.createOrUpdate(
                conversationId, tripRequestDTO);

        String userPrompt = "Provided information of trip";
        String response = orchestrationService.chat(conversationId, userPrompt, buildEnrichedMessage(conversationId, userPrompt));

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{conversationId}/export")
    public ResponseEntity<TripPdf> export(@PathVariable("conversationId") String conversationId)
            throws IOException {
        TripRequest tripRequest = tripRequestService
                .findByConversation_Id(conversationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No trip request found for conversation: " + conversationId));

        if (tripRequest.getConversation() != null && tripRequest.getConversation().getUser() != null) {
            userService.incrementApiCallCount(tripRequest.getConversation().getUser().getId());
        }

        // ─── Context retrieval ────────────────────────────────────────────
        String destination = tripRequest.getDestination();
        String source      = tripRequest.getSource();
        String budgetPref  = tripRequest.getBudgetPreference() != null ? tripRequest.getBudgetPreference().name() : "MID";
        int    totalDays   = tripRequest.getTotalDays();
        String tripSummary = TripRequestPromptSerializer.serialize(tripRequest);

        List<String> hotelHints      = retrievalHelper.search(destination + " hotels " + budgetPref, 3);
        List<String> activityHints   = retrievalHelper.search(destination + " sightseeing attractions " + (tripRequest.getInterests() != null ? tripRequest.getInterests() : ""), 4);
        List<String> restaurantHints = retrievalHelper.search(destination + " local food restaurants", 3);
        List<String> transportHints  = retrievalHelper.search(source + " to " + destination + " flights transport", 2);
        List<String> countryHints    = retrievalHelper.search(destination + " travel visa currency safety", 2);

        String destCtx =
                "Hotels: "       + sanitizeHints(hotelHints)      + "\n" +
                "Activities: "   + sanitizeHints(activityHints)   + "\n" +
                "Restaurants: "  + sanitizeHints(restaurantHints) + "\n" +
                "Transport: "    + sanitizeHints(transportHints)  + "\n" +
                "Country/Visa: " + sanitizeHints(countryHints);

        // ─── Markdown front-matter cover ──────────────────────────────────
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
                tripRequest.getEndDate()   != null ? tripRequest.getEndDate()   : "TBD",
                totalDays, headcount, budgetPref, conversationId);

        // ─── Phase 1: One Markdown section per day ────────────────────────
        java.util.List<String> dayMarkdowns = new java.util.ArrayList<>();
        java.util.List<String> rollingSummary = new java.util.ArrayList<>();

        for (int dayNum = 1; dayNum <= totalDays; dayNum++) {
            final int currentDay = dayNum;
            String dayDate = tripRequest.getStartDate() != null
                    ? tripRequest.getStartDate().plusDays(currentDay - 1).toString() : "TBD";

            String prevCtx = rollingSummary.isEmpty()
                    ? "This is Day 1 — arrival day."
                    : "Already covered: " + String.join(" → ", rollingSummary) + ". Do NOT repeat those activities.";

            String dayPrompt = String.format("""
                    Trip: %s
                    
                    Reference data (use for inspiration, do NOT quote verbatim):
                    %s
                    
                    %s
                    
                    Write ONLY the Markdown itinerary section for Day %d of %d (%s).
                    
                    Use this exact structure (fill in real content):
                    ### Day %d — [City Name] (%s)
                    **Weather:** [condition, temp range, rain chance]
                    
                    **Activities:**
                    - [Activity name]([Google Maps URL]) — [duration], ₹[cost for group] — [1-line note]
                    - [Activity name]([Google Maps URL]) — [duration], ₹[cost for group] — [1-line note]
                    
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

            String dayMd = llmBulkheadManager.executeWithGroq(() -> exportChatClient.prompt()
                    .user(dayPrompt).call().content());

            if (dayMd == null || dayMd.isBlank()) {
                dayMd = "### Day " + currentDay + " — " + destination + " (" + dayDate + ")\n\n*Data generation failed for this day.*\n";
            }
            // Strip any accidental ``` fences
            dayMd = dayMd.replaceAll("```markdown\\s*", "").replaceAll("```\\s*", "").trim();
            log.info("Day {} markdown generated ({} chars)", currentDay, dayMd.length());
            dayMarkdowns.add(dayMd);

            // Extract first activity line for rolling summary (everything after first "- ")
            String firstActivity = dayMd.lines()
                    .filter(l -> l.trim().startsWith("- ") || l.trim().startsWith("* "))
                    .findFirst()
                    .map(l -> l.replaceAll("\\[([^\\]]+)\\]\\([^)]+\\)", "$1")
                              .replaceAll("\\*\\*|\\*", "")
                              .trim().substring(2))
                    .orElse("sightseeing");
            rollingSummary.add(String.format("Day %d in %s (%s)", currentDay,
                    destination, firstActivity.substring(0, Math.min(firstActivity.length(), 40))));
        }

        // ─── Phase 2: Overview + Hotels + Transport + Cost summary ────────
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
                | Dates | [start] → [end] |
                | Duration | %d days |
                | Travellers | %d |
                | Budget | %s |
                
                ## Flights & Inter-city Transport
                | Leg | Route | Date | Mode | Operator | Depart | Arrive | Duration | Cost (₹) | Book |
                |---|---|---|---|---|---|---|---|---|---|
                | 1 | [from → to] | [date] | [mode] | [airline/train] | [time] | [time] | [Xh] | [amount] | [Book]([URL]) |
                | 2 | [return leg] | [date] | [mode] | [airline/train] | [time] | [time] | [Xh] | [amount] | [Book]([URL]) |
                
                ## Accommodation
                | City | Hotel | Stars | Check-in | Check-out | Nights | Per Night (₹) | Total (₹) | Book |
                |---|---|---|---|---|---|---|---|---|
                | [city] | [name]([booking URL]) | [★★★] | [date] | [date] | [n] | [rate] | [total] | [Book]([URL]) |
                
                ## Cost Summary
                | Category | Amount (₹) |
                |---|---|
                | Flights (outbound + return) | [amount] |
                | Hotels | [amount] |
                | Food (all days) | [amount] |
                | Activities (all days) | [amount] |
                | Local Transport (all days) | [amount] |
                | **Grand Total** | **[amount]** |
                | Budget Remaining | [amount] |
                
                ## Travel Tips & Warnings
                - [Visa / entry requirement tip]
                - [Weather / packing tip]
                - [Currency / payment tip]
                
                Output ONLY the Markdown. Start with '## Overview'.
                """,
                tripSummary.substring(0, Math.min(tripSummary.length(), 300)),
                summaryOfDays,
                source, destination, totalDays, headcount, budgetPref
        );

        String finalMd = llmBulkheadManager.executeWithGroq(() -> exportChatClient.prompt()
                .user(finalPrompt).call().content());

        if (finalMd == null) finalMd = "## Overview\n\n*Generation failed*\n";
        finalMd = finalMd.replaceAll("```markdown\\s*", "").replaceAll("```\\s*", "").trim();

        // ─── Phase 3: Assemble full Markdown document ─────────────────────
        StringBuilder fullMarkdown = new StringBuilder();
        fullMarkdown.append(frontMatter).append("\n");
        fullMarkdown.append("# Trip Plan — ").append(destination).append("\n\n");
        fullMarkdown.append(finalMd).append("\n\n");
        fullMarkdown.append("## Day-by-Day Itinerary\n\n");
        for (String dayMd : dayMarkdowns) {
            fullMarkdown.append(dayMd).append("\n\n");
        }

        // ─── Phase 4: Deterministic Markdown → PDF ────────────────────────
        com.learn.springai.model.Conversation conv = conversationService.getConversation(conversationId);
        boolean isPublic = conv != null && Boolean.TRUE.equals(conv.getIsPublic());
        TripPdf tripPdf = tripPdfService.generateAndSaveFromMarkdown(
                conversationId, fullMarkdown.toString(), destination, isPublic);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(tripPdf);
    }

    /**
     * Trims each retrieval snippet to its first line and max 80 characters,
     * preventing raw web-snippet text from leaking into LLM JSON output.
     */
    private String sanitizeHints(List<String> hints) {
        if (hints == null || hints.isEmpty()) return "N/A";
        return hints.stream()
                .map(h -> {
                    if (h == null) return "";
                    // Take first non-blank line
                    String firstLine = h.lines()
                            .filter(l -> !l.isBlank())
                            .findFirst()
                            .orElse(h);
                    // Cap at 80 characters
                    return firstLine.length() > 80 ? firstLine.substring(0, 80) : firstLine;
                })
                .filter(h -> !h.isBlank())
                .collect(java.util.stream.Collectors.joining(" | "));
    }

    private String buildEnrichedMessage(String conversationId, String userMessage) {
        try {
            String template = enricherTemplate.getContentAsString(StandardCharsets.UTF_8);
            return template
                    .replace("{conversationId}", conversationId)
                    .replace("{userMessage}", userMessage);
        } catch (IOException e) {
            return userMessage;
        }
    }
}
