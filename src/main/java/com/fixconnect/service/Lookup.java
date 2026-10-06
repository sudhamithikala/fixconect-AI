package com.fixconnect.service;

import com.fixconnect.common.ApiException;
import com.fixconnect.domain.*;
import com.fixconnect.repository.*;
import org.springframework.stereotype.Component;

/** Central "find or 404" helpers. */
@Component
public class Lookup {

    private final UserRepository users;
    private final ServiceCategoryRepository categories;
    private final ProviderProfileRepository profiles;
    private final ServiceRequestRepository requests;

    public Lookup(UserRepository users, ServiceCategoryRepository categories, ProviderProfileRepository profiles,
                  ServiceRequestRepository requests) {
        this.users = users;
        this.categories = categories;
        this.profiles = profiles;
        this.requests = requests;
    }

    public User user(Long id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("User"));
    }

    public ServiceCategory category(String code) {
        if (code == null || code.isBlank()) {
            throw ApiException.badRequest("categoryCode is required");
        }
        return categories.findByCodeIgnoreCase(code.trim())
                .orElseThrow(() -> ApiException.badRequest("Unknown service category: " + code
                        + ". See GET /api/services for valid codes."));
    }

    public ProviderProfile profileOf(Long userId) {
        return profiles.findByUser_Id(userId).orElseThrow(() -> ApiException.notFound("Technician profile"));
    }

    public ProviderProfile technician(Long userId) {
        User u = users.findById(userId).orElseThrow(() -> ApiException.notFound("Technician"));
        if (u.getRole() != Role.PROVIDER || !u.isActive()) {
            throw ApiException.notFound("Technician");
        }
        ProviderProfile p = profileOf(userId);
        if (!p.isApproved()) {
            throw ApiException.notFound("Technician");
        }
        return p;
    }

    public ServiceRequest customerRequest(Long requestId, Long customerId) {
        return requests.findByIdAndCustomer_Id(requestId, customerId)
                .orElseThrow(() -> ApiException.notFound("Service request"));
    }

    /** A job visible to a provider: assigned to them, or targeted to them while pending. */
    public ServiceRequest providerJob(Long requestId, Long providerId) {
        ServiceRequest r = requests.findById(requestId).orElseThrow(() -> ApiException.notFound("Job"));
        if (r.getProvider() == null || !r.getProvider().getId().equals(providerId)) {
            throw ApiException.notFound("Job");
        }
        return r;
    }

    public ServiceRequest request(Long id) {
        return requests.findById(id).orElseThrow(() -> ApiException.notFound("Service request"));
    }
}
