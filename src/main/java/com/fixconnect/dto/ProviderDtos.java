package com.fixconnect.dto;

import com.fixconnect.domain.RequestStatus;
import com.fixconnect.dto.CommonDtos.CategoryRef;
import com.fixconnect.dto.CommonDtos.NotificationResponse;
import com.fixconnect.dto.CommonDtos.ReviewResponse;
import com.fixconnect.dto.RequestDtos.ServiceRequestResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class ProviderDtos {

    private ProviderDtos() {
    }

    public record ProviderProfileResponse(
            Long id, String fullName, String email, String phone, CategoryRef category, String headline,
            String skills, int experienceYears, String serviceArea, int serviceRadiusKm, int maxDailyJobs,
            boolean available, boolean verified, double rating, int reviewCount, long completedJobs,
            double completionRate, double acceptanceRate, LocalDateTime memberSince) {
    }

    public record UpdateProviderProfileRequest(
            @NotBlank @Pattern(regexp = AuthDtos.NAME_REGEX, message = AuthDtos.NAME_MESSAGE) String fullName,
            @NotBlank @Pattern(regexp = AuthDtos.PHONE_REGEX, message = AuthDtos.PHONE_MESSAGE) String phone,
            @NotBlank @Email @Size(max = 120) @Pattern(regexp = AuthDtos.EMAIL_REGEX, message = AuthDtos.EMAIL_MESSAGE) String email,
            @Schema(example = "AC_REPAIR") @NotBlank String categoryCode,
            @Schema(example = "AC & Home Appliance Specialist") @Size(max = 120) @Pattern(regexp = AuthDtos.TEXT_REGEX, message = "Headline " + AuthDtos.TEXT_MESSAGE) String headline,
            @Schema(example = "Split AC, Window AC, Gas charging, Washing machines") @NotBlank @Size(min = 3, max = 500, message = "Skills must be 3-500 characters") @Pattern(regexp = AuthDtos.TEXT_REGEX, message = "Skills: " + AuthDtos.TEXT_MESSAGE) String skills,
            @NotNull @Min(0) @Max(60) Integer experienceYears,
            @Schema(example = "Jubilee Hills, Banjara Hills & Madhapur") @NotBlank @Pattern(regexp = AuthDtos.AREA_REGEX, message = AuthDtos.AREA_MESSAGE) String serviceArea) {
    }

    public record AvailabilityRequest(@NotNull Boolean available) {
    }

    public record AvailabilityResponse(boolean available, String statusLabel, String message) {
    }

    public record LocationUpdateRequest(@NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
                                        @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude) {
    }

    public record ProviderSettingsRequest(
            @Min(1) @Max(100) Integer serviceRadiusKm,
            @Min(1) @Max(30) Integer maxDailyJobs,
            @Pattern(regexp = "^$|" + AuthDtos.NAME_REGEX, message = "Account holder: " + AuthDtos.NAME_MESSAGE) String bankAccountHolder,
            @Pattern(regexp = "^$|^[0-9]{9,18}$", message = "Account number must be 9-18 digits") String bankAccountNumber,
            @Pattern(regexp = "^$|^[A-Z]{4}0[A-Z0-9]{6}$", message = "Invalid IFSC code") String bankIfsc,
            @Pattern(regexp = "^$|^[A-Za-z0-9._-]{2,256}@[A-Za-z][A-Za-z0-9]{1,63}$", message = "Enter a valid UPI ID (e.g. rahul@okaxis)") String upiId,
            Boolean smsAlerts,
            Boolean whatsappUpdates,
            Boolean emailNotifications) {
    }

    public record ProviderSettingsResponse(int serviceRadiusKm, int maxDailyJobs, String bankAccountHolder,
                                           @Schema(description = "Masked") String bankAccountNumber, String bankIfsc,
                                           String upiId, boolean smsAlerts, boolean whatsappUpdates,
                                           boolean emailNotifications) {
    }

    public record VerificationResponse(boolean idProofUploaded, String idProofUrl, boolean idVerified,
                                       LocalDateTime idVerifiedAt, boolean licenseVerified,
                                       boolean backgroundCheckCleared, boolean proBadge) {
    }

    public record OfferingRequest(
            @Schema(example = "AC Servicing & Repair") @NotBlank @Size(min = 3, max = 120, message = "Service title must be 3-120 characters") @Pattern(regexp = AuthDtos.TEXT_REGEX, message = "Title: " + AuthDtos.TEXT_MESSAGE) String title,
            @Schema(example = "Split AC, Window AC deep cleaning, gas charging, leak detection.") @Size(max = 500) @Pattern(regexp = AuthDtos.TEXT_REGEX, message = "Description: " + AuthDtos.TEXT_MESSAGE) String description,
            @Schema(example = "499") @NotNull @Positive @DecimalMax(value = "100000", message = "Base rate cannot exceed ₹1,00,000") BigDecimal baseRate,
            Boolean active) {
    }

    public record DeclineRequest(@Size(max = 300) String reason) {
    }

    @Schema(description = "Move a job forward: EN_ROUTE -> ARRIVED -> IN_PROGRESS -> COMPLETED")
    public record JobStatusUpdateRequest(
            @NotNull RequestStatus status,
            @Schema(example = "Arrived at location!") @Size(max = 300) String note,
            @Min(0) @Max(600) Integer etaMinutes) {
    }

    public record EtaUpdateRequest(@NotNull @Min(0) @Max(600) Integer etaMinutes,
                                   @Schema(example = "Stuck in traffic, +10 mins") @Size(max = 300) String note) {
    }

    public record CreateInvoiceRequest(
            @Schema(example = "499") @NotNull @PositiveOrZero @DecimalMax(value = "1000000", message = "Labour cannot exceed ₹10,00,000") BigDecimal laborCost,
            @Schema(example = "350") @PositiveOrZero @DecimalMax(value = "1000000", message = "Parts cost cannot exceed ₹10,00,000") BigDecimal partsCost,
            @Schema(example = "Capacitor 45uF") @Size(max = 500) String partsDescription,
            @PositiveOrZero BigDecimal discount,
            @Size(max = 500) String notes) {
    }

    public record ProviderStats(long newRequests, long highPriority, long todaysJobs, long ongoing,
                                long completedJobs, double completionRate, double acceptanceRate, double rating,
                                int reviewCount, long unreadNotifications) {
    }

    public record ProviderDashboardResponse(
            String fullName, String headline, boolean available, boolean verified, String serviceArea,
            int serviceRadiusKm, ProviderStats stats, List<ServiceRequestResponse> incomingRequests,
            List<ServiceRequestResponse> todaySchedule, List<ReviewResponse> recentReviews,
            List<NotificationResponse> recentNotifications) {
    }

    public record CustomerDirectoryItem(Long customerId, String name, String phone, String location, long totalJobs,
                                        LocalDate lastServiceDate) {
    }

    public record RatingsSummary(double average, int count, Map<Integer, Long> distribution,
                                 List<ReviewResponse> reviews) {
    }

    public record MonthlyStat(String month, long jobs, BigDecimal earnings) {
    }

    public record PerformanceResponse(double acceptanceRate, double completionRate, long totalCompleted,
                                      long totalCancelled, BigDecimal totalEarnings, BigDecimal pendingPayments,
                                      double averageRating, int reviewCount, List<MonthlyStat> monthly) {
    }
}
