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
public class TripPlanDTO {

    @JsonProperty("meta")
    private MetaDTO meta;

    @JsonProperty("route_overview")
    private List<RouteStopDTO> routeOverview;

    @JsonProperty("transport_legs")
    private List<TransportLegDTO> transportLegs;

    @JsonProperty("days")
    private List<DayDTO> days;

    @JsonProperty("hotels")
    private List<HotelDTO> hotels;

    @JsonProperty("section_summaries")
    private List<SectionSummaryDTO> sectionSummaries;

    @JsonProperty("cost_breakdown")
    private CostBreakdownDTO costBreakdown;

    @JsonProperty("validation")
    private ValidationDTO validation;
}