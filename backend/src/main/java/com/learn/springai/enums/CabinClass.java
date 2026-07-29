package com.learn.springai.enums;

public enum CabinClass {
    ECONOMY("ECONOMY"),
    PREMIUM_ECONOMY("PREMIUM_ECONOMY"),
    BUSINESS("BUSINESS"),
    FIRST("FIRST");

    private final String label;

    CabinClass(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static CabinClass fromLabel(String label) {
        for (CabinClass cls : CabinClass.values()) {
            if (cls.label.equalsIgnoreCase(label)) {
                return cls;
            }
        }
        throw new IllegalArgumentException("Unknown cabin class: " + label);
    }
}
