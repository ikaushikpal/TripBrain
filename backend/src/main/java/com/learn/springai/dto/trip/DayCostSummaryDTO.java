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
public class DayCostSummaryDTO {

    @JsonProperty("activities_total_inr")
    private Double activitiesTotalInr;

    @JsonProperty("food_total_inr")
    private Double foodTotalInr;

    @JsonProperty("transport_total_inr")
    private Double transportTotalInr;

    @JsonProperty("day_total_inr")
    private Double dayTotalInr;
}