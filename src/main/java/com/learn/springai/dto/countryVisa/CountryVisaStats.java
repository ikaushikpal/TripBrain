package com.learn.springai.dto.countryVisa;

import lombok.Data;
import java.util.List;

@Data
public class CountryVisaStats {

    private String name;
    private String code;

    private List<VisaCountry> VR; // Visa Required
    private List<VisaCountry> VOA; // Visa on Arrival
    private List<VisaCountry> VF; // Visa Free
    private List<VisaCountry> EV; // eVisa
    private List<VisaCountry> NA; // Not Allowed

    private String last_updated;

    @Data
    public static class VisaCountry {
        private String name;
        private String code;
        private Integer duration;
    }
}
