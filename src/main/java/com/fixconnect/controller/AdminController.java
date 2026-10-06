package com.fixconnect.controller;

import com.fixconnect.dto.AdminDtos.*;
import com.fixconnect.security.CurrentUser;
import com.fixconnect.service.AdminService;
import com.fixconnect.service.AdminService.StatusFilter;
import com.fixconnect.service.AdminService.VerificationFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "14. Admin")
public class AdminController {

    private final AdminService admin;
    private final CurrentUser currentUser;

    public AdminController(AdminService admin, CurrentUser currentUser) {
        this.admin = admin;
        this.currentUser = currentUser;
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Admin overview: counts, providers awaiting verification, newest customers")
    public AdminDashboardResponse dashboard() {
        return admin.dashboard(currentUser.id());
    }

    @GetMapping("/customers")
    @Operation(summary = "List customers", description = "status: ALL, ACTIVE, DEACTIVATED. q searches name, e-mail, phone.")
    public List<CustomerAdminView> customers(@RequestParam(required = false) String q,
                                             @RequestParam(defaultValue = "ALL") StatusFilter status) {
        return admin.customers(q, status);
    }

    @GetMapping("/customers/{id}")
    @Operation(summary = "Customer details")
    public CustomerAdminView customer(@PathVariable Long id) {
        return admin.customer(id);
    }

    @GetMapping("/providers")
    @Operation(summary = "List service providers",
            description = "status: ALL, ACTIVE, DEACTIVATED · verification: ALL, PENDING, VERIFIED · category: e.g. AC_REPAIR")
    public List<ProviderAdminView> providers(@RequestParam(required = false) String q,
                                             @RequestParam(defaultValue = "ALL") StatusFilter status,
                                             @RequestParam(defaultValue = "ALL") VerificationFilter verification,
                                             @RequestParam(required = false) String category) {
        return admin.providers(q, status, verification, category);
    }

    @GetMapping("/providers/{id}")
    @Operation(summary = "Service provider details incl. verification and ID proof link")
    public ProviderAdminView provider(@PathVariable Long id) {
        return admin.provider(id);
    }

    @PatchMapping("/providers/{id}/verification")
    @Operation(summary = "Verify a service provider (ID, licence, background check)",
            description = "All three true = approved: the provider can go online, appears in search and receives jobs.")
    public ProviderAdminView verify(@PathVariable Long id, @Valid @RequestBody VerificationUpdateRequest req) {
        return admin.updateVerification(id, req);
    }

    @PatchMapping("/users/{id}/status")
    @Operation(summary = "Deactivate or reactivate a customer / service provider",
            description = "Deactivated users cannot log in and their current sessions stop working immediately.")
    public AccountStatusResponse status(@PathVariable Long id, @Valid @RequestBody AccountStatusRequest req) {
        return admin.setStatus(currentUser.id(), id, req);
    }
}
