package com.learn.springai.config;

import java.util.Set;

import com.learn.springai.model.TripRequest;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.stream.Collectors;

public class TripRequestPromptSerializer {

    public static String serialize(TripRequest request) {

        StringBuilder sb = new StringBuilder();
        sb.append("Trip Details:\n\n");

        // ───────────────── CORE ─────────────────
        section(sb, "Core");
        append(sb, "Source", request.getSource());
        append(sb, "Destination", request.getDestination());

        // ───────────────── TIME ─────────────────
        section(sb, "Time");
        append(sb, "Start Date", request.getStartDate());
        append(sb, "End Date", request.getEndDate());
        append(sb, "Total Days", request.getTotalDays());
        append(sb, "Nights", request.getNights());

        // ───────────────── TRAVELLERS ─────────────────
        section(sb, "Travellers");
        append(sb, "Adults", request.getAdults());
        append(sb, "Children", request.getChildren());
        append(sb, "Traveller Type", labelOf(request.getTravellerType()));

        // ───────────────── BUDGET ─────────────────
        section(sb, "Budget");
        append(sb, "Currency", request.getCurrency());
        append(sb, "Budget Preference", labelOf(request.getBudgetPreference()));
        append(sb, "Max Budget", request.getMaxBudget());
        append(sb, "Daily Budget Per Person", request.getDailyBudgetPerPerson());
        append(sb, "Flights Included In Budget", yesNo(request.getFlightsIncludedInBudget()));

        // ───────────────── FLIGHTS ─────────────────
        section(sb, "Flights");
        append(sb, "Cabin Class", labelOf(request.getCabinClass()));
        append(sb, "Direct Flights Only", yesNo(request.getDirectFlightsOnly()));

        // ───────────────── TRANSPORT ─────────────────
        section(sb, "Transport");
        append(sb, "Preferred Transport Modes", joinEnumSet(request.getPreferredTransportModes()));
        append(sb, "Private Transfers Preferred", yesNo(request.getPrivateTransferPreferred()));
        append(sb, "Max Travel Time Per Day (hrs)", request.getMaxTravelTimePerDay());

        // ───────────────── STAY ─────────────────
        section(sb, "Stay");
        append(sb, "Min Hotel Stars", request.getMinHotelStars());
        append(sb, "Max Hotel Stars", request.getMaxHotelStars());
        append(sb, "Accommodation Types", joinEnumSet(request.getAccommodationTypes()));
        append(sb, "Required Amenities", joinEnumSet(request.getRequiredAmenities()));

        // ───────────────── FOOD ─────────────────
        section(sb, "Food");
        append(sb, "Food Styles", joinEnumSet(request.getFoodStyles()));
        append(sb, "Food Allergies", joinSet(request.getFoodAllergies()));
        append(sb, "Dining Styles", joinEnumSet(request.getDiningStyles()));
        append(sb, "Include Food Tour", yesNo(request.getIncludeFoodTour()));

        // ───────────────── EXPERIENCE ─────────────────
        section(sb, "Experience");
        append(sb, "Vacation Styles", joinEnumSet(request.getVacationStyles()));
        append(sb, "Activity Intensity", labelOf(request.getActivityIntensity()));
        append(sb, "Interests", joinEnumSet(request.getInterests()));

        // ───────────────── EXTRAS ─────────────────
        section(sb, "Extras");
        append(sb, "Extras", joinEnumSet(request.getExtras()));
        append(sb, "Must Visit Places", joinSet(request.getMustVisitPlaces()));
        append(sb, "Avoid Places", joinSet(request.getAvoidPlaces()));

        // ───────────────── FLAGS ─────────────────
        section(sb, "Inclusions");
        append(sb, "Include Transport", yesNo(request.getIncludeTransport()));
        append(sb, "Include Hotels", yesNo(request.getIncludeHotels()));
        append(sb, "Include Restaurants", yesNo(request.getIncludeRestaurants()));
        append(sb, "Include Weather Forecast", yesNo(request.getIncludeWeatherForecast()));
        append(sb, "Generate Weather Fallbacks", yesNo(request.getGenerateWeatherFallbacks()));
        append(sb, "Include Cost Breakdown", yesNo(request.getIncludeCostBreakdown()));
        append(sb, "Include Visa Info", yesNo(request.getIncludeVisaInfo()));

        // ───────────────── PERSONAL ─────────────────
        section(sb, "Traveller Info");
        append(sb, "Nationality", request.getNationality());
        append(sb, "Passport Country", request.getPassportCountry());
        append(sb, "Accessibility Required", yesNo(request.getAccessibilityRequired()));

        // ───────────────── NOTES ─────────────────
        section(sb, "Notes");
        append(sb, "Additional Notes", request.getNotes());

        return sb.toString();
    }

    // ───────────────── HELPERS ─────────────────

    private static void section(StringBuilder sb, String title) {
        sb.append(title).append(":\n");
    }

    private static void append(StringBuilder sb, String key, Object value) {
        if (value == null)
            return;

        if (value instanceof String && ((String) value).isBlank())
            return;

        sb.append("- ").append(key).append(": ").append(value).append("\n");
    }

    private static String yesNo(Boolean value) {
        if (value == null)
            return null;
        return value ? "Yes" : "No";
    }

    private static String joinEnumSet(Set<?> set) {
        if (set == null || set.isEmpty())
            return null;

        return set.stream()
                .map(TripRequestPromptSerializer::labelOf)
                .collect(Collectors.joining(", "));
    }

    private static String joinSet(Set<?> set) {
        if (set == null || set.isEmpty())
            return null;

        return set.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(", "));
    }

    /**
     * Safely extracts `getLabel()` if present, else falls back to toString()
     */
    private static String labelOf(Object obj) {
        if (obj == null)
            return null;

        try {
            Method method = obj.getClass().getMethod("getLabel");
            Object value = method.invoke(obj);
            return value != null ? value.toString() : obj.toString();
        } catch (Exception e) {
            return obj.toString();
        }
    }
}