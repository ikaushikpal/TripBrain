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
public class FlightCostDTO {

    @JsonProperty("outbound_total_inr")
    private Double outboundTotalInr;

    @JsonProperty("inbound_total_inr")
    private Double inboundTotalInr;

    @JsonProperty("inter_city_total_inr")
    private Double interCityTotalInr;

    @JsonProperty("flights_grand_total_inr")
    private Double flightsGrandTotalInr;
}
