package com.learn.springai.enums;

public enum DiningStyle {
    STREET_FOOD("STREET_FOOD"),
    CASUAL_RESTAURANT("CASUAL_RESTAURANT"),
    FINE_DINING("FINE_DINING"),
    ROOFTOP_BAR("ROOFTOP_BAR"),
    CAFE("CAFE"),
    BUFFET("BUFFET"),
    LOCAL_HOME_COOKED("LOCAL_HOME_COOKED"),
    FOOD_TOUR("FOOD_TOUR");

    private final String label;

    DiningStyle(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static DiningStyle fromLabel(String label) {
        for (DiningStyle style : DiningStyle.values()) {
            if (style.label.equalsIgnoreCase(label)) {
                return style;
            }
        }
        throw new IllegalArgumentException("Unknown dining style: " + label);
    }
}
