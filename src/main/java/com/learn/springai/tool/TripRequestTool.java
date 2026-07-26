package com.learn.springai.tool;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import com.learn.springai.dto.tripRequest.TripRequestUpdateDTO;
import com.learn.springai.enums.BudgetPreference;
import com.learn.springai.enums.CabinClass;
import com.learn.springai.enums.FoodStyle;
import com.learn.springai.enums.TravellerType;
import com.learn.springai.service.TripRequestService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TripRequestTool {

    private static final Logger logger = LoggerFactory.getLogger(TripRequestTool.class);

    private final TripRequestService tripRequestService;

    // ─────────────────────────────
    // READ
    // ─────────────────────────────

    @Tool(name = "getTripRequest", description = "Get current trip details")
    public String getTripRequest(String conversationId) {

        logger.info("[Tool] getTripRequest | conversationId={}", conversationId);

        try {
            String result = tripRequestService.getByConversationId(conversationId).toString();
            logger.info("[Tool] getTripRequest SUCCESS | conversationId={}", conversationId);
            return result;

        } catch (Exception e) {
            logger.error("[Tool] getTripRequest FAILED | conversationId={}", conversationId, e);
            return "No trip found";
        }
    }

    // ─────────────────────────────
    // CORE DETAILS
    // ─────────────────────────────

    @Tool(name = "updateDestination", description = "Change destination")
    public String updateDestination(String conversationId, String destination) {

        logger.info("[Tool] updateDestination | conversationId={} | destination={}",
                conversationId, destination);

        return apply(
                TripRequestUpdateDTO.builder()
                        .destination(destination)
                        .build(),
                "destination",
                conversationId);
    }

    @Tool(name = "updateSource", description = "Change source city")
    public String updateSource(String conversationId, String source) {

        logger.info("[Tool] updateSource | conversationId={} | source={}",
                conversationId, source);

        return apply(
                TripRequestUpdateDTO.builder()
                        .source(source)
                        .build(),
                "source",
                conversationId);
    }

    @Tool(name = "updateDates", description = "Update travel dates")
    public String updateDates(String conversationId, String startDate, String endDate) {

        logger.info("[Tool] updateDates | conversationId={} | startDate={} | endDate={}",
                conversationId, startDate, endDate);

        return apply(
                TripRequestUpdateDTO.builder()
                        .startDate(startDate != null ? LocalDate.parse(startDate) : null)
                        .endDate(endDate != null ? LocalDate.parse(endDate) : null)
                        .build(),
                "dates",
                conversationId);
    }

    // ─────────────────────────────
    // TRAVELLERS
    // ─────────────────────────────

    @Tool(name = "updateAdults", description = "Update number of adults")
    public String updateAdults(String conversationId, Integer adults) {

        logger.info("[Tool] updateAdults | conversationId={} | adults={}",
                conversationId, adults);

        return apply(
                TripRequestUpdateDTO.builder()
                        .adults(adults)
                        .build(),
                "adults",
                conversationId);
    }

    @Tool(name = "updateChildren", description = "Update number of children")
    public String updateChildren(String conversationId, Integer children) {

        logger.info("[Tool] updateChildren | conversationId={} | children={}",
                conversationId, children);

        return apply(
                TripRequestUpdateDTO.builder()
                        .children(children)
                        .build(),
                "children",
                conversationId);
    }

    @Tool(name = "updateTravellerType", description = "Update traveller type like SOLO, COUPLE")
    public String updateTravellerType(String conversationId, String travellerType) {

        logger.info("[Tool] updateTravellerType | conversationId={} | travellerType={}",
                conversationId, travellerType);

        return apply(
                TripRequestUpdateDTO.builder()
                        .travellerType(travellerType != null ? TravellerType.valueOf(travellerType) : null)
                        .build(),
                "traveller type",
                conversationId);
    }

    // ─────────────────────────────
    // BUDGET
    // ─────────────────────────────

    @Tool(name = "updateMaxBudget", description = "Update total trip budget amount")
    public String updateMaxBudget(String conversationId, Double maxBudget) {

        logger.info("[Tool] updateMaxBudget | conversationId={} | maxBudget={}",
                conversationId, maxBudget);

        return apply(
                TripRequestUpdateDTO.builder()
                        .maxBudget(maxBudget)
                        .build(),
                "budget",
                conversationId);
    }

    @Tool(name = "updateDailyBudget", description = "Update daily per person budget")
    public String updateDailyBudget(String conversationId, Double dailyBudgetPerPerson) {

        logger.info("[Tool] updateDailyBudget | conversationId={} | dailyBudgetPerPerson={}",
                conversationId, dailyBudgetPerPerson);

        return apply(
                TripRequestUpdateDTO.builder()
                        .dailyBudgetPerPerson(dailyBudgetPerPerson)
                        .build(),
                "daily budget",
                conversationId);
    }

    @Tool(name = "updateCurrency", description = "Update currency like INR, USD")
    public String updateCurrency(String conversationId, String currency) {

        logger.info("[Tool] updateCurrency | conversationId={} | currency={}",
                conversationId, currency);

        return apply(
                TripRequestUpdateDTO.builder()
                        .currency(currency)
                        .build(),
                "currency",
                conversationId);
    }

    @Tool(name = "updateBudgetPreference", description = "Update budget type BACKPACKER, MID, LUXURY")
    public String updateBudgetPreference(String conversationId, String budgetPreference) {

        logger.info("[Tool] updateBudgetPreference | conversationId={} | budgetPreference={}",
                conversationId, budgetPreference);

        return apply(
                TripRequestUpdateDTO.builder()
                        .budgetPreference(budgetPreference != null
                                ? BudgetPreference.valueOf(budgetPreference)
                                : null)
                        .build(),
                "budget preference",
                conversationId);
    }

    // ─────────────────────────────
    // TRANSPORT
    // ─────────────────────────────

    @Tool(name = "updateCabinClass", description = "Update flight class ECONOMY, BUSINESS")
    public String updateCabinClass(String conversationId, String cabinClass) {

        logger.info("[Tool] updateCabinClass | conversationId={} | cabinClass={}",
                conversationId, cabinClass);

        return apply(
                TripRequestUpdateDTO.builder()
                        .cabinClass(cabinClass != null ? CabinClass.valueOf(cabinClass) : null)
                        .build(),
                "cabin class",
                conversationId);
    }

    @Tool(name = "updateDirectFlights", description = "Enable or disable direct flights")
    public String updateDirectFlights(String conversationId, Boolean directFlightsOnly) {

        logger.info("[Tool] updateDirectFlights | conversationId={} | directFlightsOnly={}",
                conversationId, directFlightsOnly);

        return apply(
                TripRequestUpdateDTO.builder()
                        .directFlightsOnly(directFlightsOnly)
                        .build(),
                "direct flights",
                conversationId);
    }

    // ─────────────────────────────
    // ACCOMMODATION
    // ─────────────────────────────

    @Tool(name = "updateHotelStars", description = "Update hotel star rating")
    public String updateHotelStars(String conversationId, Integer minStars, Integer maxStars) {

        logger.info("[Tool] updateHotelStars | conversationId={} | minStars={} | maxStars={}",
                conversationId, minStars, maxStars);

        return apply(
                TripRequestUpdateDTO.builder()
                        .minHotelStars(minStars)
                        .maxHotelStars(maxStars)
                        .build(),
                "hotel stars",
                conversationId);
    }

    // ─────────────────────────────
    // FOOD
    // ─────────────────────────────

    @Tool(name = "updateFoodStyles", description = "Update food preferences like VEG")
    public String updateFoodStyles(String conversationId, String foodStyles) {

        logger.info("[Tool] updateFoodStyles | conversationId={} | foodStyles={}",
                conversationId, foodStyles);

        return apply(
                TripRequestUpdateDTO.builder()
                        .foodStyles(parseEnumSet(foodStyles, FoodStyle.class))
                        .build(),
                "food styles",
                conversationId);
    }

    // ─────────────────────────────
    // NOTES
    // ─────────────────────────────

    @Tool(name = "addNotes", description = "Add notes or special instructions")
    public String addNotes(String conversationId, String notes) {

        logger.info("[Tool] addNotes | conversationId={} | notes={}",
                conversationId, notes);

        return apply(
                TripRequestUpdateDTO.builder()
                        .notes(notes)
                        .build(),
                "notes",
                conversationId);
    }

    // ─────────────────────────────
    // INTERNAL
    // ─────────────────────────────

    private String apply(TripRequestUpdateDTO patch, String section, String conversationId) {

        logger.info("[Tool] APPLY START | section={} | conversationId={}", section, conversationId);

        try {
            var updated = tripRequestService.update(conversationId, patch);

            logger.info("[Tool] APPLY SUCCESS | section={} | conversationId={}",
                    section, conversationId);

            return "Updated " + section + ": " + updated;

        } catch (Exception e) {

            logger.error("[Tool] APPLY FAILED | section={} | conversationId={}",
                    section, conversationId, e);

            return "Failed to update " + section + ": " + e.getMessage();
        }
    }

    private <E extends Enum<E>> Set<E> parseEnumSet(String csv, Class<E> enumClass) {

        if (csv == null || csv.isBlank()) {
            logger.info("[Tool] parseEnumSet | empty input");
            return null;
        }

        Set<E> result = new HashSet<>();

        for (String token : csv.split(",")) {
            result.add(Enum.valueOf(enumClass, token.trim()));
        }

        logger.info("[Tool] parseEnumSet | parsed={}", result);

        return result;
    }
}