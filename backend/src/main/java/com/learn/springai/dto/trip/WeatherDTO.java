package com.learn.springai.dto.trip;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WeatherDTO {

    @JsonProperty("condition")
    private String condition;

    @JsonProperty("temp_range")
    private String tempRange;

    @JsonProperty("rain_probability_pct")
    private Integer rainProbabilityPct;

    @JsonProperty("weather_alert")
    private Boolean weatherAlert;
}