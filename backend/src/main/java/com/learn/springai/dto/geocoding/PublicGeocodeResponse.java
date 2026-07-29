package com.learn.springai.dto.geocoding;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PublicGeocodeResponse {
    private String lat;
    private String lon;
    private String display_name;
}
