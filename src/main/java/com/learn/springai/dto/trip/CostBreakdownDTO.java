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
public class CostBreakdownDTO {

    @JsonProperty("flights")
    private FlightCostDTO flights;

    @JsonProperty("hotels_grand_total_inr")
    private Double hotelsGrandTotalInr;

    @JsonProperty("food_grand_total_inr")
    private Double foodGrandTotalInr;

    @JsonProperty("activities_grand_total_inr")
    private Double activitiesGrandTotalInr;

    @JsonProperty("local_transport_grand_total_inr")
    private Double localTransportGrandTotalInr;

    @JsonProperty("grand_total_inr")
    private Double grandTotalInr;

    @JsonProperty("budget_remaining_inr")
    private Double budgetRemainingInr;

    @JsonProperty("within_budget")
    private boolean withinBudget;
}