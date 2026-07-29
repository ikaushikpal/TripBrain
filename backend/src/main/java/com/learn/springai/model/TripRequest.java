package com.learn.springai.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.annotation.JsonIgnore;
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

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "trip_requests", indexes = {
        @Index(name = "idx_trip_req_conv", columnList = "conversation_id")
})
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TripRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false, unique = true)
    @JsonIgnore
    private Conversation conversation;

    @Column(nullable = false)
    private String source;

    @Column(nullable = false)
    private String destination;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Transient
    public int getNights() {
        if (startDate == null || endDate == null)
            return 0;
        return (int) ChronoUnit.DAYS.between(startDate, endDate);
    }

    @Transient
    public int getTotalDays() {
        return getNights() + 1;
    }

    @Builder.Default
    @Column(nullable = false)
    private Integer adults = 1;

    @Builder.Default
    @Column(nullable = false)
    private Integer children = 0;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TravellerType travellerType = TravellerType.SOLO;

    /** ISO 4217 currency code, e.g. "INR", "USD". Default: "INR" */
    @Builder.Default
    @Column(nullable = false, length = 3)
    private String currency = "INR";

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BudgetPreference budgetPreference = BudgetPreference.MID;

    /** Hard ceiling in `currency`. Null = no cap. */
    private Double maxBudget;

    /** Per-person daily spend cap. Null = not set. */
    private Double dailyBudgetPerPerson;

    @Builder.Default
    @Column(nullable = false)
    private Boolean flightsIncludedInBudget = true;

    @Builder.Default
    @Column(nullable = false)
    private Integer maxTravelTimePerDay = 4;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CabinClass cabinClass = CabinClass.ECONOMY;

    @Builder.Default
    @Column(nullable = false)
    private Boolean directFlightsOnly = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Set<TransportMode> preferredTransportModes;

    @Builder.Default
    @Column(nullable = false)
    private Boolean privateTransferPreferred = false;

    @Builder.Default
    @Column(nullable = false)
    private Integer minHotelStars = 2;

    @Builder.Default
    @Column(nullable = false)
    private Integer maxHotelStars = 4;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Set<AccommodationType> accommodationTypes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Set<HotelAmenity> requiredAmenities;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Set<FoodStyle> foodStyles = Set.of(FoodStyle.ANY);

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Set<String> foodAllergies;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Set<DiningStyle> diningStyles;

    @Builder.Default
    @Column(nullable = false)
    private Boolean includeFoodTour = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Set<VacationStyle> vacationStyles;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Set<ExtraPreference> extras;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActivityIntensity activityIntensity = ActivityIntensity.MODERATE;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Set<TravelInterest> interests;

    @Builder.Default
    @Column(nullable = false)
    private Boolean includeTransport = true;
    @Builder.Default
    @Column(nullable = false)
    private Boolean includeHotels = true;
    @Builder.Default
    @Column(nullable = false)
    private Boolean includeRestaurants = true;
    @Builder.Default
    @Column(nullable = false)
    private Boolean includeWeatherForecast = true;
    @Builder.Default
    @Column(nullable = false)
    private Boolean includeCostBreakdown = true;
    @Builder.Default
    @Column(nullable = false)
    private Boolean generateWeatherFallbacks = true;
    @Builder.Default
    @Column(nullable = false)
    private Boolean includeVisaInfo = true;

    /** Traveller nationality for visa notes, e.g. "Indian" */
    private String nationality;

    /** ISO-2 passport country, e.g. "IN" */
    private String passportCountry;

    @Builder.Default
    @Column(nullable = false)
    private Boolean accessibilityRequired = false;

    /**
     * Free-text user notes passed verbatim to the LLM.
     * Use for: preferred hotels, occasions, local contacts,
     * flexible day adjustments, anything unstructured.
     */
    @Column(columnDefinition = "text")
    private String notes;

    /**
     * Named places the traveller explicitly wants included.
     * e.g. ["Wat Pho", "Chatuchak Market", "Railay Beach"]
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Set<String> mustVisitPlaces;

    /**
     * Places or experiences to explicitly exclude.
     * e.g. ["Pattaya", "Khaosan Road"]
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Set<String> avoidPlaces;
}