package com.fixconnect.controller;

import com.fixconnect.common.MessageResponse;
import com.fixconnect.dto.CustomerDtos.*;
import com.fixconnect.security.CurrentUser;
import com.fixconnect.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customer")
@Tag(name = "05. Customer - Profile")
public class CustomerProfileController {

    private final CustomerService customers;
    private final CurrentUser currentUser;

    public CustomerProfileController(CustomerService customers, CurrentUser currentUser) {
        this.customers = customers;
        this.currentUser = currentUser;
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Dashboard overview: stats, ongoing service, upcoming services, top technicians, notifications")
    public CustomerDashboardResponse dashboard() {
        return customers.dashboard(currentUser.id());
    }

    @GetMapping("/profile")
    @Operation(summary = "My profile with summary counters")
    public CustomerProfileResponse profile() {
        return customers.profile(currentUser.id());
    }

    @PutMapping("/profile")
    @Operation(summary = "Update profile details")
    public CustomerProfileResponse updateProfile(@Valid @RequestBody UpdateCustomerProfileRequest req) {
        return customers.updateProfile(currentUser.id(), req);
    }

    @GetMapping("/addresses")
    @Operation(summary = "Saved service locations")
    public List<AddressResponse> addresses() {
        return customers.addresses(currentUser.id());
    }

    @PostMapping("/addresses")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add address")
    public AddressResponse addAddress(@Valid @RequestBody AddressRequest req) {
        return customers.addAddress(currentUser.id(), req);
    }

    @PutMapping("/addresses/{id}")
    @Operation(summary = "Update address")
    public AddressResponse updateAddress(@PathVariable Long id, @Valid @RequestBody AddressRequest req) {
        return customers.updateAddress(currentUser.id(), id, req);
    }

    @PatchMapping("/addresses/{id}/primary")
    @Operation(summary = "Make address primary")
    public AddressResponse makePrimary(@PathVariable Long id) {
        return customers.setPrimary(currentUser.id(), id);
    }

    @DeleteMapping("/addresses/{id}")
    @Operation(summary = "Delete address")
    public MessageResponse deleteAddress(@PathVariable Long id) {
        customers.deleteAddress(currentUser.id(), id);
        return MessageResponse.ok("Address deleted");
    }

    @GetMapping("/settings")
    @Operation(summary = "Notification preferences (SMS emergency alerts, WhatsApp status updates, e-mail)")
    public NotificationSettings settings() {
        return customers.settings(currentUser.id());
    }

    @PutMapping("/settings")
    @Operation(summary = "Update notification preferences (null fields are left unchanged)")
    public NotificationSettings updateSettings(@RequestBody NotificationSettings req) {
        return customers.updateSettings(currentUser.id(), req);
    }
}
