package com.learn.springai.dto.trip;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ValidationDTO {

    @JsonProperty("within_budget")
    private Boolean withinBudget;

    @JsonProperty("max_daily_travel_respected")
    private Boolean maxDailyTravelRespected;

    @JsonProperty("route_logical")
    private Boolean routeLogical;

    @JsonProperty("weather_considered")
    private Boolean weatherConsidered;

    @JsonProperty("data_missing_fields")
    private List<String> dataMissingFields;

    @JsonProperty("warnings")
    private List<String> warnings;

    @JsonProperty("notes")
    private String notes;
}