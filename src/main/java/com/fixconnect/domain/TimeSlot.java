package com.fixconnect.domain;

public enum TimeSlot {
    MORNING("Morning (9 AM - 12 PM)"),
    AFTERNOON("Afternoon (12 PM - 3 PM)"),
    EVENING("Evening (3 PM - 6 PM)"),
    IMMEDIATE("Immediate Emergency (SOS)");

    private final String label;

    TimeSlot(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
