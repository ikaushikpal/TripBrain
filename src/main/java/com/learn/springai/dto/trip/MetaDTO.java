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
public class MetaDTO {

    @JsonProperty("source")
    private String source;

    @JsonProperty("destination")
    private String destination;

    @JsonProperty("start_date")
    private String startDate;

    @JsonProperty("end_date")
    private String endDate;

    @JsonProperty("total_days")
    private Integer totalDays;

    @JsonProperty("headcount")
    private Integer headcount;

    @JsonProperty("budget_preference")
    private String budgetPreference;

    @JsonProperty("max_budget_inr")
    private Double maxBudgetInr;

    @JsonProperty("currency_conversion_rate")
    private String currencyConversionRate;

    @JsonProperty("flights_in_budget")
    private Boolean flightsInBudget;

    @JsonProperty("generated_at")
    private String generatedAt;
}