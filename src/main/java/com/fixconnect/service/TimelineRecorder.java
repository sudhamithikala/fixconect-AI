package com.fixconnect.service;

import com.fixconnect.domain.RequestStatus;
import com.fixconnect.domain.ServiceRequest;
import com.fixconnect.domain.StatusEvent;
import com.fixconnect.repository.StatusEventRepository;
import org.springframework.stereotype.Component;

/** Appends milestones to the ETA / Service Status timeline. */
@Component
public class TimelineRecorder {

    private final StatusEventRepository events;

    public TimelineRecorder(StatusEventRepository events) {
        this.events = events;
    }

    public void record(ServiceRequest request, RequestStatus status, String message) {
        events.save(new StatusEvent(request, status, message, request.getEtaMinutes()));
    }
}
