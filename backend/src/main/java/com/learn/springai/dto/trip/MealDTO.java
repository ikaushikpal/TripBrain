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
public class MealDTO {

    @JsonProperty("type")
    private String type;

    @JsonProperty("restaurant_name")
    private String restaurantName;

    @JsonProperty("cuisine")
    private String cuisine;

    @JsonProperty("diet_fit")
    private String dietFit;

    @JsonProperty("cost_per_person_inr")
    private Double costPerPersonInr;

    @JsonProperty("cost_total_inr")
    private Double costTotalInr;
}