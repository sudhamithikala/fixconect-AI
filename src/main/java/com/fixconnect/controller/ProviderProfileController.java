package com.fixconnect.controller;

import com.fixconnect.common.MessageResponse;
import com.fixconnect.dto.CommonDtos.OfferingResponse;
import com.fixconnect.dto.ProviderDtos.*;
import com.fixconnect.security.CurrentUser;
import com.fixconnect.service.ProviderAccountService;
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
@Tag(name = "09. Provider - Profile & Settings")
public class ProviderProfileController {

    private final ProviderAccountService service;
    private final CurrentUser currentUser;

    public ProviderProfileController(ProviderAccountService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping("/profile")
    @Operation(summary = "Technician profile")
    public ProviderProfileResponse profile() {
        return service.profile(currentUser.id());
    }

    @PutMapping("/profile")
    @Operation(summary = "Update technician information (name, specialization, skills, experience, area)")
    public ProviderProfileResponse update(@Valid @RequestBody UpdateProviderProfileRequest req) {
        return service.update(currentUser.id(), req);
    }

    @PostMapping(value = "/profile/id-proof", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload Government Photo ID (Aadhaar / PAN)")
    public VerificationResponse uploadIdProof(@RequestPart("file") MultipartFile file) {
        return service.uploadIdProof(currentUser.id(), file);
    }

    @GetMapping("/verification")
    @Operation(summary = "Verification & credentials status (ID, trade licence, background check, Pro badge)")
    public VerificationResponse verification() {
        return service.verification(currentUser.id());
    }

    @PatchMapping("/availability")
    @Operation(summary = "Online / offline availability toggle")
    public AvailabilityResponse availability(@Valid @RequestBody AvailabilityRequest req) {
        return service.setAvailability(currentUser.id(), req.available());
    }

    @PutMapping("/location")
    @Operation(summary = "Update live location (used for tracking, distance and ETA)")
    public MessageResponse location(@Valid @RequestBody LocationUpdateRequest req) {
        service.updateLocation(currentUser.id(), req);
        return MessageResponse.ok("Location updated");
    }

    @GetMapping("/settings")
    @Operation(summary = "Work preferences, bank payout details, notification preferences")
    public ProviderSettingsResponse settings() {
        return service.settings(currentUser.id());
    }

    @PutMapping("/settings")
    @Operation(summary = "Update preferences (service radius, max daily jobs, payout details). Null fields unchanged.")
    public ProviderSettingsResponse updateSettings(@Valid @RequestBody ProviderSettingsRequest req) {
        return service.updateSettings(currentUser.id(), req);
    }

    @GetMapping("/services")
    @Operation(summary = "My services & rates")
    public List<OfferingResponse> offerings() {
        return service.offerings(currentUser.id());
    }

    @PostMapping("/services")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a service offering with base rate")
    public OfferingResponse addOffering(@Valid @RequestBody OfferingRequest req) {
        return service.addOffering(currentUser.id(), req);
    }

    @PutMapping("/services/{id}")
    @Operation(summary = "Update a service offering")
    public OfferingResponse updateOffering(@PathVariable Long id, @Valid @RequestBody OfferingRequest req) {
        return service.updateOffering(currentUser.id(), id, req);
    }

    @DeleteMapping("/services/{id}")
    @Operation(summary = "Delete a service offering")
    public MessageResponse deleteOffering(@PathVariable Long id) {
        service.deleteOffering(currentUser.id(), id);
        return MessageResponse.ok("Service removed");
    }
}
