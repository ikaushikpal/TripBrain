package com.learn.springai.dto.trip;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TransportLegDTO {

    @JsonProperty("leg_number")
    private Integer legNumber;

    @JsonProperty("date")
    private String date;

    @JsonProperty("from")
    private String from;

    @JsonProperty("to")
    private String to;

    @JsonProperty("mode")
    private String mode;

    @JsonProperty("operator")
    private String operator;

    @JsonProperty("departure_time")
    private String departureTime;

    @JsonProperty("arrival_time")
    private String arrivalTime;

    @JsonProperty("duration_hrs")
    private Double durationHrs;

    @JsonProperty("distance_km")
    private Double distanceKm;

    @JsonProperty("cost_per_person_inr")
    private Double costPerPersonInr;

    @JsonProperty("cost_total_inr")
    private Double costTotalInr;

    @JsonProperty("booking_tip")
    private String bookingTip;

    @JsonProperty("weather_risk")
    private String weatherRisk;
}