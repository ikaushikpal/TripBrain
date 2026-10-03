package com.learn.springai.config;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.stream.Collectors;

import com.learn.springai.model.TripRequest;

public class TripRequestPromptSerializer {

    public static String serialize(TripRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("<trip_configuration>\n");

        // ───────────────── CORE ─────────────────
        openTag(sb, "core");
        appendTag(sb, "source", request.getSource());
        appendTag(sb, "destination", request.getDestination());
        closeTag(sb, "core");

        // ───────────────── TIME ─────────────────
        openTag(sb, "time");
        appendTag(sb, "start_date", request.getStartDate());
        appendTag(sb, "end_date", request.getEndDate());
        appendTag(sb, "total_days", request.getTotalDays());
        appendTag(sb, "nights", request.getNights());
        closeTag(sb, "time");

        // ───────────────── TRAVELLERS ─────────────────
        openTag(sb, "travellers");
        appendTag(sb, "adults", request.getAdults());
        appendTag(sb, "children", request.getChildren());
        appendTag(sb, "traveller_type", labelOf(request.getTravellerType()));
        closeTag(sb, "travellers");

        // ───────────────── BUDGET ─────────────────
        openTag(sb, "budget");
        appendTag(sb, "currency", request.getCurrency());
        appendTag(sb, "budget_preference", labelOf(request.getBudgetPreference()));
        appendTag(sb, "max_budget", request.getMaxBudget());
        appendTag(sb, "daily_budget_per_person", request.getDailyBudgetPerPerson());
        appendTag(sb, "flights_included_in_budget", yesNo(request.getFlightsIncludedInBudget()));
        closeTag(sb, "budget");

        // ───────────────── FLIGHTS ─────────────────
        openTag(sb, "flights");
        appendTag(sb, "cabin_class", labelOf(request.getCabinClass()));
        appendTag(sb, "direct_flights_only", yesNo(request.getDirectFlightsOnly()));
        closeTag(sb, "flights");

        // ───────────────── TRANSPORT ─────────────────
        openTag(sb, "transport");
        appendTag(sb, "preferred_transport_modes", joinEnumSet(request.getPreferredTransportModes()));
        appendTag(sb, "private_transfers_preferred", yesNo(request.getPrivateTransferPreferred()));
        appendTag(sb, "max_travel_time_per_day_hrs", request.getMaxTravelTimePerDay());
        closeTag(sb, "transport");

        // ───────────────── STAY ─────────────────
        openTag(sb, "stay");
        appendTag(sb, "min_hotel_stars", request.getMinHotelStars());
        appendTag(sb, "max_hotel_stars", request.getMaxHotelStars());
        appendTag(sb, "accommodation_types", joinEnumSet(request.getAccommodationTypes()));
        appendTag(sb, "required_amenities", joinEnumSet(request.getRequiredAmenities()));
        closeTag(sb, "stay");

        // ───────────────── FOOD ─────────────────
        openTag(sb, "food");
        appendTag(sb, "food_styles", joinEnumSet(request.getFoodStyles()));
        appendTag(sb, "food_allergies", joinSet(request.getFoodAllergies()));
        appendTag(sb, "dining_styles", joinEnumSet(request.getDiningStyles()));
        appendTag(sb, "include_food_tour", yesNo(request.getIncludeFoodTour()));
        closeTag(sb, "food");

        // ───────────────── EXPERIENCE ─────────────────
        openTag(sb, "experience");
        appendTag(sb, "vacation_styles", joinEnumSet(request.getVacationStyles()));
        appendTag(sb, "activity_intensity", labelOf(request.getActivityIntensity()));
        appendTag(sb, "interests", joinEnumSet(request.getInterests()));
        closeTag(sb, "experience");

        // ───────────────── EXTRAS & PLACES ─────────────────
        openTag(sb, "places_and_extras");
        appendTag(sb, "extras", joinEnumSet(request.getExtras()));
        appendTag(sb, "must_visit_places", joinSet(request.getMustVisitPlaces()));
        appendTag(sb, "avoid_places", joinSet(request.getAvoidPlaces()));
        closeTag(sb, "places_and_extras");

        // ───────────────── FLAGS / INCLUSIONS ─────────────────
        openTag(sb, "inclusions");
        appendTag(sb, "include_transport", yesNo(request.getIncludeTransport()));
        appendTag(sb, "include_hotels", yesNo(request.getIncludeHotels()));
        appendTag(sb, "include_restaurants", yesNo(request.getIncludeRestaurants()));
        appendTag(sb, "include_weather_forecast", yesNo(request.getIncludeWeatherForecast()));
        appendTag(sb, "generate_weather_fallbacks", yesNo(request.getGenerateWeatherFallbacks()));
        appendTag(sb, "include_cost_breakdown", yesNo(request.getIncludeCostBreakdown()));
        appendTag(sb, "include_visa_info", yesNo(request.getIncludeVisaInfo()));
        closeTag(sb, "inclusions");

        // ───────────────── PERSONAL ─────────────────
        openTag(sb, "traveller_info");
        appendTag(sb, "nationality", request.getNationality());
        appendTag(sb, "passport_country", request.getPassportCountry());
        appendTag(sb, "accessibility_required", yesNo(request.getAccessibilityRequired()));
        closeTag(sb, "traveller_info");

        // ───────────────── NOTES ─────────────────
        if (request.getNotes() != null && !request.getNotes().isBlank()) {
            openTag(sb, "notes");
            appendTag(sb, "additional_notes", request.getNotes());
            closeTag(sb, "notes");
        }

        sb.append("</trip_configuration>");
        return sb.toString();
    }

    // ───────────────── HELPERS ─────────────────

    private static void openTag(StringBuilder sb, String tag) {
        sb.append("  <").append(tag).append(">\n");
    }

    private static void closeTag(StringBuilder sb, String tag) {
        sb.append("  </").append(tag).append(">\n");
    }

    private static void appendTag(StringBuilder sb, String tag, Object value) {
        if (value == null) return;
        if (value instanceof String && ((String) value).isBlank()) return;

        sb.append("    <").append(tag).append(">")
          .append(escapeXml(value.toString()))
          .append("</").append(tag).append(">\n");
    }

    private static String escapeXml(String text) {
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;");
    }

    private static String yesNo(Boolean value) {
        if (value == null) return null;
        return value ? "true" : "false";
    }

    private static String joinEnumSet(Set<?> set) {
        if (set == null || set.isEmpty()) return null;
        return set.stream()
                .map(TripRequestPromptSerializer::labelOf)
                .collect(Collectors.joining(", "));
    }

    private static String joinSet(Set<?> set) {
        if (set == null || set.isEmpty()) return null;
        return set.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(", "));
    }

    private static String labelOf(Object obj) {
        if (obj == null) return null;
        try {
            Method method = obj.getClass().getMethod("getLabel");
            Object value = method.invoke(obj);
            return value != null ? value.toString() : obj.toString();
        } catch (Exception e) {
            return obj.toString();
        }
    }
}