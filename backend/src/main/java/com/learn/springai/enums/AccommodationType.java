package com.learn.springai.enums;

public enum AccommodationType {
    HOSTEL("HOSTEL"),
    BUDGET_HOTEL("BUDGET_HOTEL"),
    BOUTIQUE_HOTEL("BOUTIQUE_HOTEL"),
    RESORT("RESORT"),
    VILLA("VILLA"),
    AIRBNB("AIRBNB"),
    GUESTHOUSE("GUESTHOUSE"),
    HOMESTAY("HOMESTAY"),
    LUXURY_HOTEL("LUXURY_HOTEL"),
    CAPSULE_HOTEL("CAPSULE_HOTEL");

    private final String label;

    AccommodationType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static AccommodationType fromLabel(String label) {
        for (AccommodationType type : AccommodationType.values()) {
            if (type.label.equalsIgnoreCase(label)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown accommodation type: " + label);
    }
}
