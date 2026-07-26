package com.learn.springai.enums;

public enum TransportMode {
    FLIGHT("FLIGHT"),
    TRAIN("TRAIN"),
    BUS("BUS"),
    TAXI("TAXI"),
    TUK_TUK("TUK_TUK"),
    METRO("METRO"),
    FERRY("FERRY"),
    SPEEDBOAT("SPEEDBOAT"),
    RENTAL_CAR("RENTAL_CAR"),
    MOTORBIKE_RENTAL("MOTORBIKE_RENTAL"),
    SHARED_SONGTHAEW("SHARED_SONGTHAEW"),
    PRIVATE_TRANSFER("PRIVATE_TRANSFER"),
    CYCLING("CYCLING"),
    WALKING("WALKING");

    private final String label;

    TransportMode(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static TransportMode fromLabel(String label) {
        for (TransportMode mode : TransportMode.values()) {
            if (mode.label.equalsIgnoreCase(label)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown transport mode: " + label);
    }
}
