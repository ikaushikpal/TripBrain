package com.learn.springai.enums;

public enum TravellerType {
    SOLO("SOLO"),
    COUPLE("COUPLE"),
    FAMILY_WITH_YOUNG_KIDS("FAMILY_WITH_YOUNG_KIDS"),
    FAMILY_WITH_OLDER_KIDS("FAMILY_WITH_OLDER_KIDS"),
    GROUP_FRIENDS("GROUP_FRIENDS"),
    GROUP_CORPORATE("GROUP_CORPORATE"),
    HONEYMOON("HONEYMOON"),
    SENIOR("SENIOR");

    private final String label;

    TravellerType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static TravellerType fromLabel(String label) {
        for (TravellerType type : TravellerType.values()) {
            if (type.label.equalsIgnoreCase(label)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown traveller type: " + label);
    }
}
