package com.learn.springai.enums;

public enum HotelAmenity {
    POOL("POOL"),
    SPA("SPA"),
    GYM("GYM"),
    FREE_WIFI("FREE_WIFI"),
    BREAKFAST_INCLUDED("BREAKFAST_INCLUDED"),
    AIRPORT_SHUTTLE("AIRPORT_SHUTTLE"),
    BEACHFRONT("BEACHFRONT"),
    PET_ALLOWED("PET_ALLOWED"),
    PARKING("PARKING"),
    ROOFTOP("ROOFTOP"),
    KITCHEN("KITCHEN"),
    AIR_CONDITIONING("AIR_CONDITIONING"),
    EV_CHARGER("EV_CHARGER");

    private final String label;

    HotelAmenity(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static HotelAmenity fromLabel(String label) {
        for (HotelAmenity amenity : HotelAmenity.values()) {
            if (amenity.label.equalsIgnoreCase(label)) {
                return amenity;
            }
        }
        throw new IllegalArgumentException("Unknown hotel amenity: " + label);
    }
}
