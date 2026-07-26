package com.learn.springai.enums;

public enum ExtraPreference {
    PET_FRIENDLY("PET_FRIENDLY"),
    FAMILY_FRIENDLY("FAMILY_FRIENDLY"),
    COUPLE_FRIENDLY("COUPLE_FRIENDLY"),
    SOLO_TRAVEL("SOLO_TRAVEL"),
    WORKATION("WORKATION"),
    LOCAL_EXPERIENCE("LOCAL_EXPERIENCE"),
    ACCESSIBLE("ACCESSIBLE"),
    ECO_FRIENDLY("ECO_FRIENDLY"),
    LUXURY_EXPERIENCE("LUXURY_EXPERIENCE"),
    BUDGET_MAXIMISER("BUDGET_MAXIMISER");

    private final String label;

    ExtraPreference(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static ExtraPreference fromLabel(String label) {
        for (ExtraPreference pref : ExtraPreference.values()) {
            if (pref.label.equalsIgnoreCase(label)) {
                return pref;
            }
        }
        throw new IllegalArgumentException("Unknown extra preference: " + label);
    }
}