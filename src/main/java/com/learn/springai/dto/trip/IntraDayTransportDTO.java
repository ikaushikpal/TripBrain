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
public class IntraDayTransportDTO {

    @JsonProperty("from")
    private String from;

    @JsonProperty("to")
    private String to;

    @JsonProperty("mode")
    private String mode;

    @JsonProperty("duration_mins")
    private Integer durationMins;

    @JsonProperty("cost_total_inr")
    private Double costTotalInr;
}