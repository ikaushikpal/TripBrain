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
public class SectionSummaryDTO {

    @JsonProperty("section_name")
    private String sectionName;

    @JsonProperty("days_covered")
    private List<Integer> daysCovered;

    @JsonProperty("hotel_cost_inr")
    private Double hotelCostInr;

    @JsonProperty("food_cost_inr")
    private Double foodCostInr;

    @JsonProperty("activities_cost_inr")
    private Double activitiesCostInr;

    @JsonProperty("local_transport_cost_inr")
    private Double localTransportCostInr;

    @JsonProperty("section_total_inr")
    private Double sectionTotalInr;
}