package com.learn.springai.tool;

import java.util.List;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import com.learn.springai.dto.countryVisa.CountryVisaStats;
import com.learn.springai.dto.countryVisa.VisaResponse;
import com.learn.springai.service.VisaService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class VisaTool {

    private final VisaService visaService;

    @Tool(description = """
            Get visa requirements between two countries using ISO 2 country codes.
            Use this when user asks about visa, entry rules, or travel eligibility.
            Example: IN (India) → TH (Thailand)
            """)
    public String getVisaInfo(

            @ToolParam(required = true, description = "Passport country code (ISO2), e.g., 'IN', 'US'") String passportCode,

            @ToolParam(required = true, description = "Destination country code (ISO2), e.g., 'TH', 'JP'") String destinationCode) {

        passportCode = passportCode.toUpperCase();
        destinationCode = destinationCode.toUpperCase();

        try {
            VisaResponse response = visaService.getVisaInfo(passportCode, destinationCode);

            if (response == null) {
                return "Visa information not available.";
            }

            return formatVisaResponse(response);

        } catch (Exception e) {
            return "Error fetching visa info. Please try again.";
        }
    }

    @Tool(description = """
            Get full visa access summary for a passport.
            Returns visa-free, visa-on-arrival, eVisa and restricted countries.
            """)
    public String getVisaStats(

            @ToolParam(required = true, description = "Passport country code (ISO2), e.g., 'IN', 'US'") String passportCode) {

        passportCode = passportCode.toUpperCase();

        try {
            CountryVisaStats response = visaService.getVisaStats(passportCode);

            if (response == null) {
                return "Visa stats not available.";
            }

            return String.format("""
                    🌍 Passport Strength: %s

                    Visa Free: %d countries
                    Visa on Arrival: %d countries
                    eVisa: %d countries
                    Visa Required: %d countries

                    Last Updated: %s
                    """,
                    response.getName(),
                    size(response.getVF()),
                    size(response.getVOA()),
                    size(response.getEV()),
                    size(response.getVR()),
                    response.getLast_updated());

        } catch (Exception e) {
            return "Error fetching visa stats.";
        }
    }

    private int size(List<?> list) {
        return list == null ? 0 : list.size();
    }

    private String formatVisaResponse(VisaResponse res) {
        return String.format("""
                Visa Information

                Passport: %s (%s)
                Destination: %s (%s)

                Visa Type: %s
                Stay Duration: %s days

                Last Updated: %s
                """,
                res.getPassport().getName(),
                res.getPassport().getCode(),
                res.getDestination().getName(),
                res.getDestination().getCode(),
                res.getCategory().getName(),
                res.getDur() != null ? res.getDur() : "N/A",
                res.getLast_updated());
    }
}