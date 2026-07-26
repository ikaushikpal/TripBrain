package com.learn.springai.dto.tripRequest;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import com.learn.springai.enums.AccommodationType;
import com.learn.springai.enums.ActivityIntensity;
import com.learn.springai.enums.BudgetPreference;
import com.learn.springai.enums.CabinClass;
import com.learn.springai.enums.DiningStyle;
import com.learn.springai.enums.ExtraPreference;
import com.learn.springai.enums.FoodStyle;
import com.learn.springai.enums.HotelAmenity;
import com.learn.springai.enums.TransportMode;
import com.learn.springai.enums.TravelInterest;
import com.learn.springai.enums.TravellerType;
import com.learn.springai.enums.VacationStyle;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Builder;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripRequestDTO {

    // Required
    @NonNull
    private String source;
    @NonNull
    private String destination;
    @NonNull
    private LocalDate startDate;
    @NonNull
    private LocalDate endDate;

    // People
    private Integer adults;
    private Integer children;
    private TravellerType travellerType;

    // Budget
    private String currency;
    private BudgetPreference budgetPreference;
    private Double maxBudget;
    private Double dailyBudgetPerPerson;
    private Boolean flightsIncludedInBudget;

    // Transport
    private Integer maxTravelTimePerDay;
    private CabinClass cabinClass;
    private Boolean directFlightsOnly;
    private Set<TransportMode> preferredTransportModes;
    private Boolean privateTransferPreferred;

    // Accommodation
    private Integer minHotelStars;
    private Integer maxHotelStars;
    private Set<AccommodationType> accommodationTypes;
    private Set<HotelAmenity> requiredAmenities;

    // Food
    private Set<FoodStyle> foodStyles;
    private Set<String> foodAllergies;
    private Set<DiningStyle> diningStyles;
    private Boolean includeFoodTour;

    // Vacation style
    private Set<VacationStyle> vacationStyles;
    private Set<ExtraPreference> extras;
    private ActivityIntensity activityIntensity;
    private Set<TravelInterest> interests;

    // Output control
    private Boolean includeTransport;
    private Boolean includeHotels;
    private Boolean includeRestaurants;
    private Boolean includeWeatherForecast;
    private Boolean includeCostBreakdown;
    private Boolean generateWeatherFallbacks;
    private Boolean includeVisaInfo;

    // Traveller context
    private String nationality;
    private String passportCountry;
    private Boolean accessibilityRequired;

    // Notes
    private String notes;
    private Set<String> mustVisitPlaces;
    private Set<String> avoidPlaces;
}