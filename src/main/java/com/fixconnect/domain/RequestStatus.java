package com.fixconnect.domain;

import java.util.EnumSet;
import java.util.Set;

/** Lifecycle of a service request / booking / complaint ticket. */
public enum RequestStatus {
    PENDING("Awaiting Technician"),
    ACCEPTED("Request Accepted"),
    EN_ROUTE("Technician En Route"),
    ARRIVED("Technician Arrived"),
    IN_PROGRESS("Repair In Progress"),
    COMPLETED("Resolved / Completed"),
    CANCELLED("Cancelled");

    private final String label;

    RequestStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static final Set<RequestStatus> ONGOING = EnumSet.of(EN_ROUTE, ARRIVED, IN_PROGRESS);
    public static final Set<RequestStatus> ACTIVE = EnumSet.of(PENDING, ACCEPTED, EN_ROUTE, ARRIVED, IN_PROGRESS);
    public static final Set<RequestStatus> ASSIGNED = EnumSet.of(ACCEPTED, EN_ROUTE, ARRIVED, IN_PROGRESS, COMPLETED);
}
