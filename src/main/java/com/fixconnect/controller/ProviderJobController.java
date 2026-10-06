package com.fixconnect.controller;

import com.fixconnect.domain.PhotoType;
import com.fixconnect.dto.CommonDtos.PhotoResponse;
import com.fixconnect.dto.CommonDtos.StatusEventResponse;
import com.fixconnect.dto.CustomerDtos.InvoiceResponse;
import com.fixconnect.dto.ProviderDtos.*;
import com.fixconnect.dto.RequestDtos.ServiceRequestResponse;
import com.fixconnect.security.CurrentUser;
import com.fixconnect.service.ProviderJobService;
import com.fixconnect.service.ProviderJobService.JobScope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/provider")
@Tag(name = "10. Provider - Requests & Jobs")
public class ProviderJobController {

    private final ProviderJobService service;
    private final CurrentUser currentUser;

    public ProviderJobController(ProviderJobService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping("/requests")
    @Operation(summary = "Incoming service requests matched to my category & radius (emergencies first)")
    public List<ServiceRequestResponse> incoming() {
        return service.incoming(currentUser.id());
    }

    @PostMapping("/requests/{id}/accept")
    @Operation(summary = "Accept request - becomes booking #BK-xxxx, customer notified")
    public ServiceRequestResponse accept(@PathVariable Long id) {
        return service.accept(currentUser.id(), id);
    }

    @PostMapping("/requests/{id}/decline")
    @Operation(summary = "Decline request")
    public ServiceRequestResponse decline(@PathVariable Long id, @Valid @RequestBody(required = false) DeclineRequest req) {
        return service.decline(currentUser.id(), id, req);
    }

    @GetMapping("/jobs")
    @Operation(summary = "My jobs",
            description = "scope: TODAY (today's schedule), UPCOMING (upcoming bookings), ONGOING, COMPLETED (service history), CANCELLED, ALL")
    public List<ServiceRequestResponse> jobs(@RequestParam(defaultValue = "ALL") JobScope scope) {
        return service.jobs(currentUser.id(), scope);
    }

    @GetMapping("/jobs/{id}")
    @Operation(summary = "Job details with customer contact and photos")
    public ServiceRequestResponse job(@PathVariable Long id) {
        return service.job(currentUser.id(), id);
    }

    @GetMapping("/jobs/{id}/timeline")
    @Operation(summary = "Job status timeline")
    public List<StatusEventResponse> timeline(@PathVariable Long id) {
        return service.jobTimeline(currentUser.id(), id);
    }

    @PatchMapping("/jobs/{id}/status")
    @Operation(summary = "Broadcast job status: EN_ROUTE, ARRIVED, IN_PROGRESS, COMPLETED (completion issues a 30-day warranty)")
    public ServiceRequestResponse status(@PathVariable Long id, @Valid @RequestBody JobStatusUpdateRequest req) {
        return service.updateStatus(currentUser.id(), id, req);
    }

    @PatchMapping("/jobs/{id}/eta")
    @Operation(summary = "Update customer ETA")
    public ServiceRequestResponse eta(@PathVariable Long id, @Valid @RequestBody EtaUpdateRequest req) {
        return service.updateEta(currentUser.id(), id, req);
    }

    @PostMapping(value = "/jobs/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Upload BEFORE / AFTER proof-of-work photo")
    public PhotoResponse uploadPhoto(@PathVariable Long id, @RequestParam PhotoType type,
                                     @RequestPart("file") MultipartFile file) {
        return service.uploadPhoto(currentUser.id(), id, type, file);
    }

    @GetMapping("/jobs/{id}/photos")
    @Operation(summary = "Photos of a job")
    public List<PhotoResponse> photos(@PathVariable Long id) {
        return service.photos(currentUser.id(), id);
    }

    @PostMapping("/jobs/{id}/invoice")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Generate & send itemised invoice (labour + parts)")
    public InvoiceResponse invoice(@PathVariable Long id, @Valid @RequestBody CreateInvoiceRequest req) {
        return service.createInvoice(currentUser.id(), id, req);
    }
}
