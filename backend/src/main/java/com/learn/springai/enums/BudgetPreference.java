package com.learn.springai.enums;

public enum BudgetPreference {
    BACKPACKER("BACKPACKER"), // hostels, street food
    MID("MID"), // budget hotels, decent food
    LUXURY("LUXURY"); // premium hotels, flights, fine dining

    private final String label;

    BudgetPreference(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static BudgetPreference fromLabel(String label) {
        for (BudgetPreference pref : BudgetPreference.values()) {
            if (pref.label.equalsIgnoreCase(label)) {
                return pref;
            }
        }
        throw new IllegalArgumentException("Unknown budget preference: " + label);
    }
}