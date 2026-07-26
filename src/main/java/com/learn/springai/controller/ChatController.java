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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learn.springai.config.TripRequestPromptSerializer;
import com.learn.springai.dto.trip.TripPlanDTO;
import com.learn.springai.dto.tripRequest.TripRequestDTO;
import com.learn.springai.model.TripPdf;
import com.learn.springai.model.TripRequest;
import com.learn.springai.service.TripPdfService;
import com.learn.springai.service.TripRequestService;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private Resource enricherTemplate;
    private final ChatClient chatClient;
    private final ChatClient exportChatClient;
    private final TripRequestService tripRequestService;
    private final TripPdfService tripPdfService;
    private final ObjectMapper objectMapper;

    public ChatController(
            @Qualifier("geminiChatClient") ChatClient chatClient,
            @Qualifier("exportChatClient") ChatClient exportChatClient,
            TripRequestService tripRequestService,
            TripPdfService tripPdfService,
            ObjectMapper objectMapper,
            @Value("classpath:/promptTemplates/userMessageEnricher.st") Resource enricherTemplate) {

        this.chatClient = chatClient;
        this.exportChatClient = exportChatClient;
        this.tripRequestService = tripRequestService;
        this.tripPdfService = tripPdfService;
        this.objectMapper = objectMapper;
        this.enricherTemplate = enricherTemplate;
    }

    @GetMapping("/{conversationId}")
    public ResponseEntity<String> chat(
            @PathVariable String conversationId,
            @RequestParam String message) {

        String enrichedMessage = buildEnrichedMessage(conversationId, message);

        String response = chatClient.prompt()
                .advisors(spec -> spec.param(CONVERSATION_ID, conversationId))
                .user(enrichedMessage)
                .call()
                .content();

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{conversationId}/uploadPreferences")
    public ResponseEntity<String> uploadTripPreferences(
            @PathVariable("conversationId") String conversationId,
            @Valid @RequestBody TripRequestDTO tripRequestDTO) {

        TripRequest saved = tripRequestService.createOrUpdate(
                conversationId, tripRequestDTO);

        String response = chatClient.prompt()
                .advisors(spec -> spec.param(CONVERSATION_ID, conversationId))
                .user(TripRequestPromptSerializer.serialize(saved))
                .call()
                .content();

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

        String tripJson;
        tripJson = objectMapper.writeValueAsString(tripRequest);
        TripPlanDTO response = exportChatClient.prompt()
                .user(tripJson)
                .call()
                .entity(TripPlanDTO.class);

        TripPdf tripPdf = tripPdfService.generateAndSave(conversationId, response, false);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(tripPdf);
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
