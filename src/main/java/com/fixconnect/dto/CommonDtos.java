package com.fixconnect.dto;

import com.fixconnect.domain.EmergencyType;
import com.fixconnect.domain.PhotoType;
import com.fixconnect.domain.RequestStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class CommonDtos {

    private CommonDtos() {
    }

    public record CategoryResponse(Long id, String code, String name, String description, String specialistTitle,
                                   String icon, BigDecimal baseVisitFee, BigDecimal typicalCostMin,
                                   BigDecimal typicalCostMax, boolean emergencySupported) {
    }

    public record CategoryRef(String code, String name) {
    }

    public record PersonRef(Long id, String fullName, String phone, String photoUrl) {
    }

    public record TechnicianSummary(
            Long id,
            String fullName,
            String phone,
            CategoryRef category,
            String headline,
            int experienceYears,
            double rating,
            int reviewCount,
            boolean verified,
            boolean available,
            @Schema(example = "Available Now") String availabilityLabel,
            String serviceArea,
            Double distanceKm,
            BigDecimal startingRate,
            @Schema(description = "Profile picture URL, null when not uploaded") String photoUrl) {
    }

    public record OfferingResponse(Long id, String title, String description, BigDecimal baseRate, boolean active) {
    }

    public record ReviewResponse(Long id, Long requestId, String serviceTitle, Long customerId, String customerName,
                                 Long technicianId, String technicianName, int rating, String comment,
                                 LocalDateTime createdAt) {
    }

    public record TechnicianDetail(
            TechnicianSummary technician,
            String skills,
            int serviceRadiusKm,
            long completedJobs,
            boolean favourite,
            List<OfferingResponse> services,
            List<ReviewResponse> recentReviews) {
    }

    public record PhotoResponse(Long id, Long requestId, PhotoType type, String url, String originalName,
                                LocalDateTime uploadedAt) {
    }

    public record NotificationResponse(Long id, String title, String message, String type, Long referenceId,
                                       boolean read, LocalDateTime createdAt) {
    }

    public record NotificationListResponse(long unreadCount, List<NotificationResponse> notifications) {
    }

    public record StatusEventResponse(RequestStatus status, String label, String message, Integer etaMinutes,
                                      LocalDateTime at) {
    }

    public record EmergencyCategoryResponse(EmergencyType type, String label, String categoryCode) {
    }

    public record EmergencyInfoResponse(String hotline, int dispatchRadiusKm, String promise,
                                        List<EmergencyCategoryResponse> categories) {
    }

    public record ContactRequest(
            @Schema(example = "Naga Sudha") @NotBlank @Pattern(regexp = AuthDtos.NAME_REGEX, message = AuthDtos.NAME_MESSAGE) String name,
            @Schema(example = "nagasudha@example.com") @NotBlank @Email @Size(max = 120) @Pattern(regexp = AuthDtos.EMAIL_REGEX, message = AuthDtos.EMAIL_MESSAGE) String email,
            @Schema(example = "+91 98765 43210") @Pattern(regexp = "^$|" + AuthDtos.PHONE_REGEX, message = AuthDtos.PHONE_MESSAGE) String phone,
            @Schema(example = "AC Repair & Maintenance") String service,
            @Schema(example = "Need AMC quote") @Size(max = 200) String subject,
            @Schema(example = "I want a yearly maintenance plan for 3 split ACs.") @NotBlank @Size(min = 10, max = 3000, message = "Details must be 10-3000 characters") @Pattern(regexp = AuthDtos.TEXT_REGEX, message = AuthDtos.TEXT_MESSAGE) String details) {
    }

    public record ContactResponse(boolean success, String message, Long referenceId) {
    }

    public record HealthResponse(String status, String service, LocalDateTime time) {
    }
}
