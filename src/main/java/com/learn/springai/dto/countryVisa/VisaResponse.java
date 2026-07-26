package com.learn.springai.dto.countryVisa;

import lombok.Data;

@Data
public class VisaResponse {

    private String id;
    private Country passport;
    private Country destination;
    private Integer dur;
    private VisaCategory category;
    private String last_updated;

    @Data
    public static class Country {
        private String name;
        private String code;
    }

    @Data
    public static class VisaCategory {
        private String name; // e.g. Visa Free
        private String code; // VF, VOA, EV, VR
    }
}