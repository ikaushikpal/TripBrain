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
public class DayDTO {

    @JsonProperty("day")
    private Integer day;

    @JsonProperty("date")
    private String date;

    @JsonProperty("base_city")
    private String baseCity;

    @JsonProperty("from_location")
    private String fromLocation;

    @JsonProperty("to_location")
    private String toLocation;

    @JsonProperty("weather")
    private WeatherDTO weather;

    @JsonProperty("activities")
    private List<ActivityDTO> activities;

    @JsonProperty("meals")
    private List<MealDTO> meals;

    @JsonProperty("intra_day_transport")
    private List<IntraDayTransportDTO> intraDayTransport;

    @JsonProperty("day_cost_summary")
    private DayCostSummaryDTO dayCostSummary;

    @JsonProperty("notes")
    private String notes;
}