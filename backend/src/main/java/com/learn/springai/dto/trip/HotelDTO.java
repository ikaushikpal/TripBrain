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
public class HotelDTO {

    @JsonProperty("city")
    private String city;

    @JsonProperty("name")
    private String name;

    @JsonProperty("stars")
    private Integer stars;

    @JsonProperty("address")
    private String address;

    @JsonProperty("check_in")
    private String checkIn;

    @JsonProperty("check_out")
    private String checkOut;

    @JsonProperty("nights")
    private Integer nights;

    @JsonProperty("rate_per_night_inr")
    private Double ratePerNightInr;

    @JsonProperty("total_cost_inr")
    private Double totalCostInr;

    @JsonProperty("amenities")
    private List<String> amenities;

    @JsonProperty("couple_friendly")
    private Boolean coupleFriendly;

    @JsonProperty("booking_platform")
    private String bookingPlatform;

    @JsonProperty("booking_url")
    private String bookingUrl;
}