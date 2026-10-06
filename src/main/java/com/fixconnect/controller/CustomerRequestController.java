package com.fixconnect.controller;

import com.fixconnect.dto.CommonDtos.PhotoResponse;
import com.fixconnect.dto.CommonDtos.ReviewResponse;
import com.fixconnect.dto.RequestDtos.*;
import com.fixconnect.security.CurrentUser;
import com.fixconnect.service.CustomerRequestService;
import com.fixconnect.service.CustomerRequestService.Scope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/customer")
@Tag(name = "06. Customer - Requests & Bookings")
public class CustomerRequestController {

    private final CustomerRequestService service;
    private final CurrentUser currentUser;

    public CustomerRequestController(CustomerRequestService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @PostMapping("/requests")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Request a home service / raise a complaint",
            description = "Creates ticket #CMP-xxxx, runs AI severity analysis and notifies matching nearby technicians. "
                    + "Pass technicianId to book a specific technician.")
    public ServiceRequestResponse create(@Valid @RequestBody CreateServiceRequest req) {
        return service.create(currentUser.id(), req);
    }

    @PostMapping(value = "/requests/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Upload problem photo for a request")
    public PhotoResponse uploadPhoto(@PathVariable Long id, @RequestPart("file") MultipartFile file) {
        return service.uploadProblemPhoto(currentUser.id(), id, file);
    }

    @GetMapping("/requests")
    @Operation(summary = "My complaints / bookings",
            description = "scope: ALL (complaints & tickets), ACTIVE, PENDING, UPCOMING, ONGOING, COMPLETED, CANCELLED")
    public List<ServiceRequestResponse> list(@Parameter @RequestParam(defaultValue = "ALL") Scope scope) {
        return service.list(currentUser.id(), scope);
    }

    @GetMapping("/requests/{id}")
    @Operation(summary = "Request / booking details including photos")
    public ServiceRequestResponse get(@PathVariable Long id) {
        return service.get(currentUser.id(), id);
    }

    @PatchMapping("/requests/{id}/reschedule")
    @Operation(summary = "Reschedule visit")
    public ServiceRequestResponse reschedule(@PathVariable Long id, @Valid @RequestBody RescheduleRequest req) {
        return service.reschedule(currentUser.id(), id, req);
    }

    @PostMapping("/requests/{id}/cancel")
    @Operation(summary = "Cancel request / booking")
    public ServiceRequestResponse cancel(@PathVariable Long id, @Valid @RequestBody(required = false) CancelRequest req) {
        return service.cancel(currentUser.id(), id, req);
    }

    @GetMapping("/requests/{id}/tracking")
    @Operation(summary = "Live technician tracking (location, distance, ETA)")
    public TrackingResponse tracking(@PathVariable Long id) {
        return service.tracking(currentUser.id(), id);
    }

    @GetMapping("/requests/{id}/timeline")
    @Operation(summary = "ETA / Service status milestones")
    public TimelineResponse timeline(@PathVariable Long id) {
        return service.timeline(currentUser.id(), id);
    }

    @GetMapping("/requests/{id}/photos")
    @Operation(summary = "Problem + before & after photos of a request")
    public List<PhotoResponse> photos(@PathVariable Long id) {
        return service.photos(currentUser.id(), id);
    }

    @PostMapping("/requests/{id}/review")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Rate & review the technician after completion")
    public ReviewResponse review(@PathVariable Long id, @Valid @RequestBody ReviewRequest req) {
        return service.review(currentUser.id(), id, req);
    }

    @GetMapping("/photos")
    @Operation(summary = "All before & after photos across my jobs")
    public List<PhotoResponse> allPhotos() {
        return service.allPhotos(currentUser.id());
    }

    @GetMapping("/history")
    @Operation(summary = "Service history (date, service, technician, cost, rating)")
    public List<HistoryItem> history() {
        return service.history(currentUser.id());
    }
}
