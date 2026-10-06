package com.fixconnect.controller;

import com.fixconnect.dto.CommonDtos.PhotoResponse;
import com.fixconnect.dto.CustomerDtos.InvoiceResponse;
import com.fixconnect.dto.CustomerDtos.WarrantyResponse;
import com.fixconnect.dto.ProviderDtos.*;
import com.fixconnect.security.CurrentUser;
import com.fixconnect.service.ProviderInsightsService;
import com.fixconnect.service.ProviderJobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/provider")
@Tag(name = "11. Provider - Insights")
public class ProviderInsightsController {

    private final ProviderInsightsService insights;
    private final ProviderJobService jobs;
    private final CurrentUser currentUser;

    public ProviderInsightsController(ProviderInsightsService insights, ProviderJobService jobs, CurrentUser currentUser) {
        this.insights = insights;
        this.jobs = jobs;
        this.currentUser = currentUser;
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Provider dashboard: stats, incoming requests, today's schedule, feedback")
    public ProviderDashboardResponse dashboard() {
        return insights.dashboard(currentUser.id());
    }

    @GetMapping("/customers")
    @Operation(summary = "Customer directory (contact history)")
    public List<CustomerDirectoryItem> customers() {
        return insights.customers(currentUser.id());
    }

    @GetMapping("/reviews")
    @Operation(summary = "Ratings & reviews summary")
    public RatingsSummary reviews() {
        return insights.ratings(currentUser.id());
    }

    @GetMapping("/performance")
    @Operation(summary = "Performance analytics: acceptance/completion rates, monthly jobs & earnings")
    public PerformanceResponse performance(@RequestParam(defaultValue = "6") int months) {
        return insights.performance(currentUser.id(), months);
    }

    @GetMapping("/invoices")
    @Operation(summary = "Invoices I issued")
    public List<InvoiceResponse> invoices() {
        return jobs.invoices(currentUser.id());
    }

    @GetMapping("/warranties")
    @Operation(summary = "Warranty certificates issued")
    public List<WarrantyResponse> warranties() {
        return jobs.warranties(currentUser.id());
    }

    @GetMapping("/photos")
    @Operation(summary = "All before & after photos I uploaded")
    public List<PhotoResponse> photos() {
        return jobs.allPhotos(currentUser.id());
    }
}
