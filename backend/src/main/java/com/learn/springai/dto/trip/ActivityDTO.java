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
public class ActivityDTO {

    @JsonProperty("name")
    private String name;

    @JsonProperty("type")
    private String type;

    @JsonProperty("duration_hrs")
    private Double durationHrs;

    @JsonProperty("start_time")
    private String startTime;

    @JsonProperty("cost_per_person_inr")
    private Double costPerPersonInr;

    @JsonProperty("cost_total_inr")
    private Double costTotalInr;

    @JsonProperty("booking_required")
    private Boolean bookingRequired;

    @JsonProperty("couple_friendly")
    private Boolean coupleFriendly;

    @JsonProperty("notes")
    private String notes;

    @JsonProperty("fallback_activity")
    private String fallbackActivity;

    @JsonProperty("map_url")
    private String mapUrl;

    @JsonProperty("booking_url")
    private String bookingUrl;
}
