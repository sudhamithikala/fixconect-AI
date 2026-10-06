package com.fixconnect.controller;

import com.fixconnect.dto.CommonDtos.*;
import com.fixconnect.service.CatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Tag(name = "02. Public Catalog")
@SecurityRequirements
public class CatalogController {

    private final CatalogService catalog;

    public CatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/api/services")
    @Operation(summary = "List service categories (Plumbing, Electrical, AC Repair, Appliance Repair, Cleaning, Carpentry, Painting...)")
    public List<CategoryResponse> categories() {
        return catalog.categories();
    }

    @GetMapping("/api/services/{code}")
    @Operation(summary = "Get one service category by code")
    public CategoryResponse category(@PathVariable String code) {
        return catalog.category(code);
    }

    @GetMapping("/api/emergency/categories")
    @Operation(summary = "Emergency SOS categories, hotline and dispatch radius (emergency.html)")
    public EmergencyInfoResponse emergency() {
        return catalog.emergencyInfo();
    }

    @PostMapping("/api/contact")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Submit the contact form (contact.html)")
    public ContactResponse contact(@Valid @RequestBody ContactRequest req) {
        return catalog.submitContact(req);
    }
}
