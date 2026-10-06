package com.fixconnect.domain;

/** SOS categories offered on emergency.html, mapped to the service category that handles them. */
public enum EmergencyType {
    GENERAL("General Emergency SOS", "GENERAL"),
    WATER_LEAKAGE("Water Leakage / Pipe Burst", "PLUMBING"),
    ELECTRICAL_FAULT("Electrical Short Circuit / Fault", "ELECTRICAL"),
    AC_GAS_LEAK("AC Breakdown / Gas Leakage", "AC_REPAIR"),
    DOOR_LOCK("Door Lock Jam / Key Failure", "CARPENTRY");

    private final String label;
    private final String categoryCode;

    EmergencyType(String label, String categoryCode) {
        this.label = label;
        this.categoryCode = categoryCode;
    }

    public String getLabel() {
        return label;
    }

    public String getCategoryCode() {
        return categoryCode;
    }
}
