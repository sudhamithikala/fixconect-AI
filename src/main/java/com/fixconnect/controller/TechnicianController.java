package com.fixconnect.controller;

import com.fixconnect.dto.CommonDtos.ReviewResponse;
import com.fixconnect.dto.CommonDtos.TechnicianDetail;
import com.fixconnect.dto.CommonDtos.TechnicianSummary;
import com.fixconnect.security.CurrentUser;
import com.fixconnect.service.TechnicianService;
import com.fixconnect.service.TechnicianService.SortBy;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/technicians")
@Tag(name = "03. Technicians")
public class TechnicianController {

    private final TechnicianService technicians;
    private final CurrentUser currentUser;

    public TechnicianController(TechnicianService technicians, CurrentUser currentUser) {
        this.technicians = technicians;
        this.currentUser = currentUser;
    }

    @GetMapping
    @Operation(summary = "Find technicians",
            description = "Public. If called with a customer token, distance is measured from the customer's primary address.")
    public List<TechnicianSummary> search(
            @Parameter(description = "Category code, e.g. AC_REPAIR") @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean availableOnly,
            @RequestParam(required = false) Boolean verifiedOnly,
            @RequestParam(required = false) Double minRating,
            @Parameter(description = "Free-text search on name, skills, area") @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "RATING") SortBy sort,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng) {
        return technicians.search(category, availableOnly, minRating, verifiedOnly, q, sort, lat, lng,
                currentUser.optionalId().orElse(null));
    }

    @GetMapping("/top")
    @Operation(summary = "Top rated verified technicians (dashboard widget)")
    public List<TechnicianSummary> top(@RequestParam(defaultValue = "3") int limit) {
        return technicians.top(Math.max(1, Math.min(limit, 20)), currentUser.optionalId().orElse(null));
    }

    @GetMapping("/{technicianId}")
    @Operation(summary = "Technician profile with services, rates and recent reviews")
    public TechnicianDetail detail(@PathVariable Long technicianId) {
        return technicians.detail(technicianId, currentUser.optionalId().orElse(null));
    }

    @GetMapping("/{technicianId}/reviews")
    @Operation(summary = "All reviews of a technician")
    public List<ReviewResponse> reviews(@PathVariable Long technicianId) {
        return technicians.reviews(technicianId);
    }
}
