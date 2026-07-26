package com.learn.springai.enums;

public enum TravelInterest {
    HISTORY("HISTORY"),
    ARCHITECTURE("ARCHITECTURE"),
    ART("ART"),
    MUSIC("MUSIC"),
    WILDLIFE("WILDLIFE"),
    MARINE_LIFE("MARINE_LIFE"),
    SPIRITUALITY("SPIRITUALITY"),
    ASTROLOGY_STARGAZING("ASTROLOGY_STARGAZING"),
    COOKING("COOKING"),
    FASHION("FASHION"),
    SPORTS("SPORTS"),
    CRAFT_BEER("CRAFT_BEER"),
    WINE("WINE"),
    TEA_CULTURE("TEA_CULTURE"),
    FILM_LOCATIONS("FILM_LOCATIONS"),
    STREET_ART("STREET_ART");

    private final String label;

    TravelInterest(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static TravelInterest fromLabel(String label) {
        for (TravelInterest interest : TravelInterest.values()) {
            if (interest.label.equalsIgnoreCase(label)) {
                return interest;
            }
        }
        throw new IllegalArgumentException("Unknown travel interest: " + label);
    }
}
