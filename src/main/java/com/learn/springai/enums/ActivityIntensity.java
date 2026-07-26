package com.learn.springai.enums;

public enum ActivityIntensity {
    LOW("LOW"), // leisurely, no exertion — spa, strolls, cafés
    MODERATE("MODERATE"), // light walking, sightseeing, casual tours
    HIGH("HIGH"), // trekking, water sports, multi-hour excursions
    EXTREME("EXTREME"); // bungee, rock climbing, multi-day hikes

    private final String label;

    ActivityIntensity(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static ActivityIntensity fromLabel(String label) {
        for (ActivityIntensity intensity : ActivityIntensity.values()) {
            if (intensity.label.equalsIgnoreCase(label)) {
                return intensity;
            }
        }
        throw new IllegalArgumentException("Unknown activity intensity: " + label);
    }
}