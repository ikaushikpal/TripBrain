package com.learn.springai.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.learn.springai.dto.tripRequest.TripRequestDTO;
import com.learn.springai.dto.tripRequest.TripRequestResponseDTO;
import com.learn.springai.dto.tripRequest.TripRequestUpdateDTO;
import com.learn.springai.model.Conversation;
import com.learn.springai.model.TripRequest;
import com.learn.springai.repository.ConversationRepository;
import com.learn.springai.repository.TripRequestRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class TripRequestService {

    private final TripRequestRepository tripRequestRepository;
    private final ConversationRepository conversationRepository;

    @Transactional
    public TripRequest createOrUpdate(String conversationId, TripRequestDTO dto) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Conversation not found: " + conversationId));

        TripRequest entity = tripRequestRepository
                .findByConversationId(conversationId)
                .orElse(null);

        if (entity == null) {
            entity = toEntity(dto);
            entity.setConversation(conversation);
        } else {
            applyPatch(entity, dto); // ✅ THIS is the key change
        }

        TripRequest saved = tripRequestRepository.save(entity);
        log.info("Upserted TripRequest [{}] for conversation [{}]", saved.getId(), conversationId);

        return saved;
    }

    @Transactional(readOnly = true)
    public TripRequestResponseDTO getByConversationId(String conversationId) {
        return tripRequestRepository.findByConversationId(conversationId)
                .map(TripRequestService::toResponse)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No TripRequest found for conversation: " + conversationId));
    }

    @Transactional(readOnly = true)
    public Optional<TripRequest> findEntityByConversationId(String conversationId) {
        return tripRequestRepository.findByConversationId(conversationId);
    }

    @Transactional
    public TripRequestResponseDTO update(String conversationId, TripRequestUpdateDTO patch) {
        TripRequest entity = tripRequestRepository.findByConversationId(conversationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No TripRequest found for conversation: " + conversationId));

        applyPatch(entity, patch);

        TripRequest saved = tripRequestRepository.save(entity);
        log.info("Updated TripRequest [{}] for conversation [{}]", saved.getId(), conversationId);
        return toResponse(saved);
    }

    public static TripRequest toEntity(TripRequestDTO dto) {
        TripRequest.TripRequestBuilder b = TripRequest.builder()
                .source(dto.getSource())
                .destination(dto.getDestination())
                .startDate(dto.getStartDate())
                .endDate(dto.getEndDate());

        // People
        if (dto.getAdults() != null)
            b.adults(dto.getAdults());
        if (dto.getChildren() != null)
            b.children(dto.getChildren());
        if (dto.getTravellerType() != null)
            b.travellerType(dto.getTravellerType());

        // Budget
        if (dto.getCurrency() != null)
            b.currency(dto.getCurrency());
        if (dto.getBudgetPreference() != null)
            b.budgetPreference(dto.getBudgetPreference());
        if (dto.getMaxBudget() != null)
            b.maxBudget(dto.getMaxBudget());
        if (dto.getDailyBudgetPerPerson() != null)
            b.dailyBudgetPerPerson(dto.getDailyBudgetPerPerson());
        if (dto.getFlightsIncludedInBudget() != null)
            b.flightsIncludedInBudget(dto.getFlightsIncludedInBudget());

        // Transport
        if (dto.getMaxTravelTimePerDay() != null)
            b.maxTravelTimePerDay(dto.getMaxTravelTimePerDay());
        if (dto.getCabinClass() != null)
            b.cabinClass(dto.getCabinClass());
        if (dto.getDirectFlightsOnly() != null)
            b.directFlightsOnly(dto.getDirectFlightsOnly());
        if (dto.getPreferredTransportModes() != null)
            b.preferredTransportModes(dto.getPreferredTransportModes());
        if (dto.getPrivateTransferPreferred() != null)
            b.privateTransferPreferred(dto.getPrivateTransferPreferred());

        // Accommodation
        if (dto.getMinHotelStars() != null)
            b.minHotelStars(dto.getMinHotelStars());
        if (dto.getMaxHotelStars() != null)
            b.maxHotelStars(dto.getMaxHotelStars());
        if (dto.getAccommodationTypes() != null)
            b.accommodationTypes(dto.getAccommodationTypes());
        if (dto.getRequiredAmenities() != null)
            b.requiredAmenities(dto.getRequiredAmenities());

        // Food
        if (dto.getFoodStyles() != null)
            b.foodStyles(dto.getFoodStyles());
        if (dto.getFoodAllergies() != null)
            b.foodAllergies(dto.getFoodAllergies());
        if (dto.getDiningStyles() != null)
            b.diningStyles(dto.getDiningStyles());
        if (dto.getIncludeFoodTour() != null)
            b.includeFoodTour(dto.getIncludeFoodTour());

        // Vacation style
        if (dto.getVacationStyles() != null)
            b.vacationStyles(dto.getVacationStyles());
        if (dto.getExtras() != null)
            b.extras(dto.getExtras());
        if (dto.getActivityIntensity() != null)
            b.activityIntensity(dto.getActivityIntensity());
        if (dto.getInterests() != null)
            b.interests(dto.getInterests());

        // Output flags
        if (dto.getIncludeTransport() != null)
            b.includeTransport(dto.getIncludeTransport());
        if (dto.getIncludeHotels() != null)
            b.includeHotels(dto.getIncludeHotels());
        if (dto.getIncludeRestaurants() != null)
            b.includeRestaurants(dto.getIncludeRestaurants());
        if (dto.getIncludeWeatherForecast() != null)
            b.includeWeatherForecast(dto.getIncludeWeatherForecast());
        if (dto.getIncludeCostBreakdown() != null)
            b.includeCostBreakdown(dto.getIncludeCostBreakdown());
        if (dto.getGenerateWeatherFallbacks() != null)
            b.generateWeatherFallbacks(dto.getGenerateWeatherFallbacks());
        if (dto.getIncludeVisaInfo() != null)
            b.includeVisaInfo(dto.getIncludeVisaInfo());

        // Traveller context
        if (dto.getNationality() != null)
            b.nationality(dto.getNationality());
        if (dto.getPassportCountry() != null)
            b.passportCountry(dto.getPassportCountry());
        if (dto.getAccessibilityRequired() != null)
            b.accessibilityRequired(dto.getAccessibilityRequired());

        // Notes
        if (dto.getNotes() != null)
            b.notes(dto.getNotes());
        if (dto.getMustVisitPlaces() != null)
            b.mustVisitPlaces(dto.getMustVisitPlaces());
        if (dto.getAvoidPlaces() != null)
            b.avoidPlaces(dto.getAvoidPlaces());

        return b.build();
    }

    // ─────────────────────────────────────────────────────────────
    // PATCH APPLIER — merges non-null fields from update DTO
    // ─────────────────────────────────────────────────────────────

    public static void applyPatch(TripRequest e, TripRequestUpdateDTO p) {
        // Core
        if (p.getSource() != null)
            e.setSource(p.getSource());
        if (p.getDestination() != null)
            e.setDestination(p.getDestination());
        if (p.getStartDate() != null)
            e.setStartDate(p.getStartDate());
        if (p.getEndDate() != null)
            e.setEndDate(p.getEndDate());

        // People
        if (p.getAdults() != null)
            e.setAdults(p.getAdults());
        if (p.getChildren() != null)
            e.setChildren(p.getChildren());
        if (p.getTravellerType() != null)
            e.setTravellerType(p.getTravellerType());

        // Budget
        if (p.getCurrency() != null)
            e.setCurrency(p.getCurrency());
        if (p.getBudgetPreference() != null)
            e.setBudgetPreference(p.getBudgetPreference());
        if (p.getMaxBudget() != null)
            e.setMaxBudget(p.getMaxBudget());
        if (p.getDailyBudgetPerPerson() != null)
            e.setDailyBudgetPerPerson(p.getDailyBudgetPerPerson());
        if (p.getFlightsIncludedInBudget() != null)
            e.setFlightsIncludedInBudget(p.getFlightsIncludedInBudget());

        // Transport
        if (p.getMaxTravelTimePerDay() != null)
            e.setMaxTravelTimePerDay(p.getMaxTravelTimePerDay());
        if (p.getCabinClass() != null)
            e.setCabinClass(p.getCabinClass());
        if (p.getDirectFlightsOnly() != null)
            e.setDirectFlightsOnly(p.getDirectFlightsOnly());
        if (p.getPreferredTransportModes() != null)
            e.setPreferredTransportModes(p.getPreferredTransportModes());
        if (p.getPrivateTransferPreferred() != null)
            e.setPrivateTransferPreferred(p.getPrivateTransferPreferred());

        // Accommodation
        if (p.getMinHotelStars() != null)
            e.setMinHotelStars(p.getMinHotelStars());
        if (p.getMaxHotelStars() != null)
            e.setMaxHotelStars(p.getMaxHotelStars());
        if (p.getAccommodationTypes() != null)
            e.setAccommodationTypes(p.getAccommodationTypes());
        if (p.getRequiredAmenities() != null)
            e.setRequiredAmenities(p.getRequiredAmenities());

        // Food
        if (p.getFoodStyles() != null)
            e.setFoodStyles(p.getFoodStyles());
        if (p.getFoodAllergies() != null)
            e.setFoodAllergies(p.getFoodAllergies());
        if (p.getDiningStyles() != null)
            e.setDiningStyles(p.getDiningStyles());
        if (p.getIncludeFoodTour() != null)
            e.setIncludeFoodTour(p.getIncludeFoodTour());

        // Vacation style
        if (p.getVacationStyles() != null)
            e.setVacationStyles(p.getVacationStyles());
        if (p.getExtras() != null)
            e.setExtras(p.getExtras());
        if (p.getActivityIntensity() != null)
            e.setActivityIntensity(p.getActivityIntensity());
        if (p.getInterests() != null)
            e.setInterests(p.getInterests());

        // Output flags
        if (p.getIncludeTransport() != null)
            e.setIncludeTransport(p.getIncludeTransport());
        if (p.getIncludeHotels() != null)
            e.setIncludeHotels(p.getIncludeHotels());
        if (p.getIncludeRestaurants() != null)
            e.setIncludeRestaurants(p.getIncludeRestaurants());
        if (p.getIncludeWeatherForecast() != null)
            e.setIncludeWeatherForecast(p.getIncludeWeatherForecast());
        if (p.getIncludeCostBreakdown() != null)
            e.setIncludeCostBreakdown(p.getIncludeCostBreakdown());
        if (p.getGenerateWeatherFallbacks() != null)
            e.setGenerateWeatherFallbacks(p.getGenerateWeatherFallbacks());
        if (p.getIncludeVisaInfo() != null)
            e.setIncludeVisaInfo(p.getIncludeVisaInfo());

        // Traveller context
        if (p.getNationality() != null)
            e.setNationality(p.getNationality());
        if (p.getPassportCountry() != null)
            e.setPassportCountry(p.getPassportCountry());
        if (p.getAccessibilityRequired() != null)
            e.setAccessibilityRequired(p.getAccessibilityRequired());

        // Notes
        if (p.getNotes() != null)
            e.setNotes(p.getNotes());
        if (p.getMustVisitPlaces() != null)
            e.setMustVisitPlaces(p.getMustVisitPlaces());
        if (p.getAvoidPlaces() != null)
            e.setAvoidPlaces(p.getAvoidPlaces());
    }

    // ─────────────────────────────────────────────────────────────
    // MAPPER — Entity → Response DTO
    // ─────────────────────────────────────────────────────────────

    public static TripRequestResponseDTO toResponse(TripRequest e) {
        return TripRequestResponseDTO.builder()
                .id(e.getId().toString())
                .conversationId(e.getConversation().getId().toString())
                .source(e.getSource())
                .destination(e.getDestination())
                .startDate(e.getStartDate())
                .endDate(e.getEndDate())
                .nights(e.getNights())
                .totalDays(e.getTotalDays())
                .adults(e.getAdults())
                .children(e.getChildren())
                .travellerType(e.getTravellerType())
                .currency(e.getCurrency())
                .budgetPreference(e.getBudgetPreference())
                .maxBudget(e.getMaxBudget())
                .dailyBudgetPerPerson(e.getDailyBudgetPerPerson())
                .flightsIncludedInBudget(e.getFlightsIncludedInBudget())
                .maxTravelTimePerDay(e.getMaxTravelTimePerDay())
                .cabinClass(e.getCabinClass())
                .directFlightsOnly(e.getDirectFlightsOnly())
                .preferredTransportModes(e.getPreferredTransportModes())
                .privateTransferPreferred(e.getPrivateTransferPreferred())
                .minHotelStars(e.getMinHotelStars())
                .maxHotelStars(e.getMaxHotelStars())
                .accommodationTypes(e.getAccommodationTypes())
                .requiredAmenities(e.getRequiredAmenities())
                .foodStyles(e.getFoodStyles())
                .foodAllergies(e.getFoodAllergies())
                .diningStyles(e.getDiningStyles())
                .includeFoodTour(e.getIncludeFoodTour())
                .vacationStyles(e.getVacationStyles())
                .extras(e.getExtras())
                .activityIntensity(e.getActivityIntensity())
                .interests(e.getInterests())
                .includeTransport(e.getIncludeTransport())
                .includeHotels(e.getIncludeHotels())
                .includeRestaurants(e.getIncludeRestaurants())
                .includeWeatherForecast(e.getIncludeWeatherForecast())
                .includeCostBreakdown(e.getIncludeCostBreakdown())
                .generateWeatherFallbacks(e.getGenerateWeatherFallbacks())
                .includeVisaInfo(e.getIncludeVisaInfo())
                .nationality(e.getNationality())
                .passportCountry(e.getPassportCountry())
                .accessibilityRequired(e.getAccessibilityRequired())
                .notes(e.getNotes())
                .mustVisitPlaces(e.getMustVisitPlaces())
                .avoidPlaces(e.getAvoidPlaces())
                .build();
    }

    public Optional<TripRequest> findByConversation_Id(String conversationId) {
        return this.tripRequestRepository.findByConversationId(conversationId);
    }

    private void applyPatch(TripRequest entity, TripRequestDTO dto) {

        // ─────────────────────────────
        // CORE
        // ─────────────────────────────
        if (dto.getSource() != null)
            entity.setSource(dto.getSource());

        if (dto.getDestination() != null)
            entity.setDestination(dto.getDestination());

        if (dto.getStartDate() != null)
            entity.setStartDate(dto.getStartDate());

        if (dto.getEndDate() != null)
            entity.setEndDate(dto.getEndDate());

        // ─────────────────────────────
        // PEOPLE
        // ─────────────────────────────
        if (dto.getAdults() != null)
            entity.setAdults(dto.getAdults());

        if (dto.getChildren() != null)
            entity.setChildren(dto.getChildren());

        if (dto.getTravellerType() != null)
            entity.setTravellerType(dto.getTravellerType());

        // ─────────────────────────────
        // BUDGET
        // ─────────────────────────────
        if (dto.getCurrency() != null)
            entity.setCurrency(dto.getCurrency());

        if (dto.getBudgetPreference() != null)
            entity.setBudgetPreference(dto.getBudgetPreference());

        if (dto.getMaxBudget() != null)
            entity.setMaxBudget(dto.getMaxBudget());

        if (dto.getDailyBudgetPerPerson() != null)
            entity.setDailyBudgetPerPerson(dto.getDailyBudgetPerPerson());

        if (dto.getFlightsIncludedInBudget() != null)
            entity.setFlightsIncludedInBudget(dto.getFlightsIncludedInBudget());

        // ─────────────────────────────
        // TRANSPORT
        // ─────────────────────────────
        if (dto.getMaxTravelTimePerDay() != null)
            entity.setMaxTravelTimePerDay(dto.getMaxTravelTimePerDay());

        if (dto.getCabinClass() != null)
            entity.setCabinClass(dto.getCabinClass());

        if (dto.getDirectFlightsOnly() != null)
            entity.setDirectFlightsOnly(dto.getDirectFlightsOnly());

        if (dto.getPreferredTransportModes() != null)
            entity.setPreferredTransportModes(dto.getPreferredTransportModes());

        if (dto.getPrivateTransferPreferred() != null)
            entity.setPrivateTransferPreferred(dto.getPrivateTransferPreferred());

        // ─────────────────────────────
        // ACCOMMODATION
        // ─────────────────────────────
        if (dto.getMinHotelStars() != null)
            entity.setMinHotelStars(dto.getMinHotelStars());

        if (dto.getMaxHotelStars() != null)
            entity.setMaxHotelStars(dto.getMaxHotelStars());

        if (dto.getAccommodationTypes() != null)
            entity.setAccommodationTypes(dto.getAccommodationTypes());

        if (dto.getRequiredAmenities() != null)
            entity.setRequiredAmenities(dto.getRequiredAmenities());

        // ─────────────────────────────
        // FOOD
        // ─────────────────────────────
        if (dto.getFoodStyles() != null)
            entity.setFoodStyles(dto.getFoodStyles());

        if (dto.getFoodAllergies() != null)
            entity.setFoodAllergies(dto.getFoodAllergies());

        if (dto.getDiningStyles() != null)
            entity.setDiningStyles(dto.getDiningStyles());

        if (dto.getIncludeFoodTour() != null)
            entity.setIncludeFoodTour(dto.getIncludeFoodTour());

        // ─────────────────────────────
        // VACATION STYLE
        // ─────────────────────────────
        if (dto.getVacationStyles() != null)
            entity.setVacationStyles(dto.getVacationStyles());

        if (dto.getExtras() != null)
            entity.setExtras(dto.getExtras());

        if (dto.getActivityIntensity() != null)
            entity.setActivityIntensity(dto.getActivityIntensity());

        if (dto.getInterests() != null)
            entity.setInterests(dto.getInterests());

        // ─────────────────────────────
        // OUTPUT FLAGS
        // ─────────────────────────────
        if (dto.getIncludeTransport() != null)
            entity.setIncludeTransport(dto.getIncludeTransport());

        if (dto.getIncludeHotels() != null)
            entity.setIncludeHotels(dto.getIncludeHotels());

        if (dto.getIncludeRestaurants() != null)
            entity.setIncludeRestaurants(dto.getIncludeRestaurants());

        if (dto.getIncludeWeatherForecast() != null)
            entity.setIncludeWeatherForecast(dto.getIncludeWeatherForecast());

        if (dto.getIncludeCostBreakdown() != null)
            entity.setIncludeCostBreakdown(dto.getIncludeCostBreakdown());

        if (dto.getGenerateWeatherFallbacks() != null)
            entity.setGenerateWeatherFallbacks(dto.getGenerateWeatherFallbacks());

        if (dto.getIncludeVisaInfo() != null)
            entity.setIncludeVisaInfo(dto.getIncludeVisaInfo());

        // ─────────────────────────────
        // TRAVELLER CONTEXT
        // ─────────────────────────────
        if (dto.getNationality() != null)
            entity.setNationality(dto.getNationality());

        if (dto.getPassportCountry() != null)
            entity.setPassportCountry(dto.getPassportCountry());

        if (dto.getAccessibilityRequired() != null)
            entity.setAccessibilityRequired(dto.getAccessibilityRequired());

        // ─────────────────────────────
        // NOTES
        // ─────────────────────────────
        if (dto.getNotes() != null)
            entity.setNotes(dto.getNotes());

        if (dto.getMustVisitPlaces() != null)
            entity.setMustVisitPlaces(dto.getMustVisitPlaces());

        if (dto.getAvoidPlaces() != null)
            entity.setAvoidPlaces(dto.getAvoidPlaces());
    }
}