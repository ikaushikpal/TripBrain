package com.learn.springai.enums;

public enum FoodStyle {
    VEG("VEG"),
    NON_VEG("NON_VEG"),
    VEGAN("VEGAN"),
    JAIN("JAIN"),
    HALAL("HALAL"),
    KOSHER("KOSHER"),
    GLUTEN_FREE("GLUTEN_FREE"),
    ANY("ANY");

    private final String label;

    FoodStyle(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static FoodStyle fromLabel(String label) {
        for (FoodStyle style : FoodStyle.values()) {
            if (style.label.equalsIgnoreCase(label)) {
                return style;
            }
        }
        throw new IllegalArgumentException("Unknown food style: " + label);
    }
}