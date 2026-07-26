package com.learn.springai.enums;

public enum VacationStyle {
    RELAXATION("RELAXATION"),
    NIGHTLIFE("NIGHTLIFE"),
    FOOD_EXPLORATION("FOOD_EXPLORATION"),
    ADVENTURE("ADVENTURE"),
    CULTURE("CULTURE"),
    NATURE("NATURE"),
    SHOPPING("SHOPPING"),
    PHOTOGRAPHY("PHOTOGRAPHY"),
    WELLNESS("WELLNESS"),
    PARTY("PARTY");

    private final String label;

    VacationStyle(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static VacationStyle fromLabel(String label) {
        for (VacationStyle style : VacationStyle.values()) {
            if (style.label.equalsIgnoreCase(label)) {
                return style;
            }
        }
        throw new IllegalArgumentException("Unknown vacation style: " + label);
    }
}
