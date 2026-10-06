package com.fixconnect.dto;

import com.fixconnect.domain.Role;
import com.fixconnect.dto.CommonDtos.CategoryRef;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public final class AdminDtos {

    private AdminDtos() {
    }

    public record AdminStats(
            long totalCustomers, long activeCustomers, long deactivatedCustomers,
            long totalProviders, long verifiedProviders, long pendingVerification, long deactivatedProviders,
            long onlineProviders, long openRequests, long completedRequests) {
    }

    public record AdminDashboardResponse(
            String adminName,
            AdminStats stats,
            List<ProviderAdminView> pendingProviders,
            List<CustomerAdminView> recentCustomers) {
    }

    public record CustomerAdminView(
            Long id, String fullName, String email, String phone, String primaryAddress,
            boolean active, LocalDateTime deactivatedAt, String deactivationReason,
            long totalRequests, long completedRequests, int loyaltyPoints, LocalDateTime createdAt) {
    }

    public record ProviderAdminView(
            Long id, String fullName, String email, String phone, CategoryRef category, String headline,
            String skills, int experienceYears, String serviceArea,
            boolean active, LocalDateTime deactivatedAt, String deactivationReason,
            @Schema(description = "Approved by an admin (ID + licence + background check)") boolean verified,
            boolean idVerified, boolean licenseVerified, boolean backgroundCheckCleared,
            boolean idProofUploaded, String idProofUrl, String verificationNote, LocalDateTime verificationUpdatedAt,
            boolean available, double rating, int reviewCount, long completedJobs, LocalDateTime createdAt) {
    }

    @Schema(description = "Deactivate (active=false) or reactivate (active=true) a customer or service provider")
    public record AccountStatusRequest(
            @NotNull Boolean active,
            @Schema(example = "Repeated no-shows reported by customers") @Size(max = 300) String reason) {
    }

    public record AccountStatusResponse(boolean success, String message, Long userId, Role role, boolean active,
                                        int affectedRequests) {
    }

    @Schema(description = "Set each verification check. Approve = all three true.")
    public record VerificationUpdateRequest(
            @NotNull Boolean idVerified,
            @NotNull Boolean licenseVerified,
            @NotNull Boolean backgroundCheckCleared,
            @Schema(example = "Aadhaar matched, trade licence checked") @Size(max = 300) String note) {
    }
}
