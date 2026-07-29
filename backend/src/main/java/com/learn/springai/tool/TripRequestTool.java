package com.learn.springai.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learn.springai.dto.tripRequest.TripRequestUpdateDTO;
import com.learn.springai.service.TripRequestService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TripRequestTool {

    private static final Logger logger = LoggerFactory.getLogger(TripRequestTool.class);
    private final TripRequestService tripRequestService;
    private final ObjectMapper objectMapper;

    @Tool(name = "getTripRequest", description = "Get current trip details. Accepts ONLY conversationId.")
    public String getTripRequest(String conversationId) {
        logger.info("[Tool] getTripRequest | conversationId={}", conversationId);
        try {
            var requestOpt = tripRequestService.findEntityByConversationId(conversationId);
            if (requestOpt.isEmpty()) {
                return "No trip found";
            }
            String result = com.learn.springai.config.TripRequestPromptSerializer.serialize(requestOpt.get());
            logger.info("[Tool] getTripRequest SUCCESS | conversationId={}", conversationId);
            return result;
        } catch (Exception e) {
            logger.error("[Tool] getTripRequest FAILED | conversationId={}", conversationId, e);
            return "No trip found";
        }
    }

    @Tool(name = "updateTripRequest", description = "Update travel/trip parameters. Pass ONLY the updated fields inside the updates map. Valid keys: source, destination, startDate (YYYY-MM-DD), endDate (YYYY-MM-DD), adults, children, travellerType (SOLO, COUPLE, FAMILY, GROUP), maxBudget, dailyBudgetPerPerson, currency, budgetPreference (BACKPACKER, MID, LUXURY), cabinClass (ECONOMY, BUSINESS), directFlightsOnly (boolean), minHotelStars, maxHotelStars, privateTransfersPreferred (boolean), notes")
    public String updateTripRequest(
            String conversationId,
            @ToolParam(description = "Map of key-value pairs representing the updates to apply.") java.util.Map<String, Object> updates) {

        logger.info("[Tool] updateTripRequest | conversationId={} | updates={}", conversationId, updates);

        try {
            TripRequestUpdateDTO patch = objectMapper.convertValue(updates, TripRequestUpdateDTO.class);
            var updated = tripRequestService.update(conversationId, patch);
            logger.info("[Tool] updateTripRequest SUCCESS | conversationId={}", conversationId);
            return "Updated trip request details: " + updated;
        } catch (Exception e) {
            logger.error("[Tool] updateTripRequest FAILED | conversationId={}", conversationId, e);
            return "Failed to update trip details: " + e.getMessage();
        }
    }
}