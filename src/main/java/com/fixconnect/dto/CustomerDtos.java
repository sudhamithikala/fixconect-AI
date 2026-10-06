package com.fixconnect.dto;

import com.fixconnect.domain.InvoiceStatus;
import com.fixconnect.domain.PaymentMethod;
import com.fixconnect.domain.TimeSlot;
import com.fixconnect.domain.WarrantyStatus;
import com.fixconnect.dto.AuthDtos;
import com.fixconnect.dto.CommonDtos.NotificationResponse;
import com.fixconnect.dto.CommonDtos.TechnicianSummary;
import com.fixconnect.dto.RequestDtos.ServiceRequestResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class CustomerDtos {

    private CustomerDtos() {
    }

    public record CustomerProfileResponse(
            Long id, String fullName, String email, String phone, String altPhone,
            String primaryAddress, LocalDateTime memberSince,
            long servicesCompleted, long activeRequests, long savedAddresses,
            int loyaltyPoints, String tier) {
    }

    public record UpdateCustomerProfileRequest(
            @NotBlank @Pattern(regexp = AuthDtos.NAME_REGEX, message = AuthDtos.NAME_MESSAGE) String fullName,
            @NotBlank @Pattern(regexp = AuthDtos.PHONE_REGEX, message = AuthDtos.PHONE_MESSAGE) String phone,
            @NotBlank @Email @Size(max = 120) @Pattern(regexp = AuthDtos.EMAIL_REGEX, message = AuthDtos.EMAIL_MESSAGE) String email,
            @Pattern(regexp = "^$|" + AuthDtos.PHONE_REGEX, message = AuthDtos.PHONE_MESSAGE) String altPhone,
            @Schema(description = "Updates the primary saved address") @NotBlank @Pattern(regexp = AuthDtos.ADDRESS_REGEX, message = AuthDtos.ADDRESS_MESSAGE) String primaryAddress) {
    }

    public record AddressRequest(
            @Schema(example = "Home") @Pattern(regexp = "^$|^[A-Za-z][A-Za-z0-9 .'-]{0,39}$", message = "Label must start with a letter and be at most 40 characters (e.g. Home, Office).") String label,
            @Schema(example = "Flat 302, Green View Apartments, Madhapur") @NotBlank @Pattern(regexp = AuthDtos.ADDRESS_REGEX, message = AuthDtos.ADDRESS_MESSAGE) String line1,
            @Schema(example = "Hyderabad") @Pattern(regexp = AuthDtos.CITY_REGEX, message = AuthDtos.CITY_MESSAGE) String city,
            @Schema(example = "Telangana") @Pattern(regexp = AuthDtos.CITY_REGEX, message = AuthDtos.CITY_MESSAGE) String state,
            @Schema(example = "500081") @Pattern(regexp = AuthDtos.PINCODE_REGEX, message = AuthDtos.PINCODE_MESSAGE) String pincode,
            Double latitude,
            Double longitude,
            Boolean primary) {
    }

    public record AddressResponse(Long id, String label, String line1, String city, String state, String pincode,
                                  Double latitude, Double longitude, boolean primary, String fullAddress) {
    }

    public record NotificationSettings(Boolean smsAlerts, Boolean whatsappUpdates, Boolean emailNotifications) {
    }

    public record CustomerStats(long activeBookings, long pendingRequests, long servicesDone, long favouriteTechs,
                                int loyaltyPoints, long unreadNotifications) {
    }

    public record CustomerDashboardResponse(
            String fullName,
            CustomerStats stats,
            ServiceRequestResponse ongoingService,
            List<ServiceRequestResponse> upcomingServices,
            List<TechnicianSummary> topTechnicians,
            List<NotificationResponse> recentNotifications) {
    }

    public record FavouriteResponse(Long id, TechnicianSummary technician, String note, LocalDateTime savedAt) {
    }

    public record InvoiceResponse(
            Long id, String invoiceNo, Long requestId, String bookingNo, String service,
            Long technicianId, String technicianName, Long customerId, String customerName,
            BigDecimal laborCost, BigDecimal partsCost, String partsDescription, BigDecimal discount, BigDecimal total,
            InvoiceStatus status, PaymentMethod paymentMethod, String appliedCoupon, String notes,
            LocalDateTime issuedAt, LocalDateTime paidAt, String pdfUrl) {
    }

    public record PayInvoiceRequest(
            @NotNull PaymentMethod method,
            @Schema(description = "Coupon code from a redeemed loyalty voucher", example = "FC-REWARD200") String couponCode) {
    }

    public record PaymentResponse(boolean success, String message, InvoiceResponse invoice, int pointsEarned,
                                  int totalPoints) {
    }

    public record WarrantyResponse(
            Long id, String warrantyNo, Long requestId, String service, String technicianName, String customerName,
            LocalDate validFrom, LocalDate validUntil, long daysLeft, WarrantyStatus status,
            LocalDateTime claimedAt, String claimNote, Long claimRequestId) {
    }

    public record WarrantyClaimRequest(
            @Schema(example = "Pipe joint is leaking again") @NotBlank @Size(min = 10, max = 500, message = "Describe the problem in 10-500 characters") @Pattern(regexp = AuthDtos.TEXT_REGEX, message = AuthDtos.TEXT_MESSAGE) String issue,
            @FutureOrPresent LocalDate preferredDate,
            TimeSlot timeSlot) {
    }

    public record WarrantyClaimResponse(boolean success, String message, WarrantyResponse warranty,
                                        ServiceRequestResponse followUpRequest) {
    }

    public record VoucherResponse(Long id, String title, String description, int pointsCost,
                                  BigDecimal discountAmount, String categoryCode, boolean affordable) {
    }

    public record RedemptionResponse(Long id, String couponCode, String voucherTitle, BigDecimal discountAmount,
                                     boolean used, LocalDateTime redeemedAt) {
    }

    public record RewardsResponse(int points, int lifetimePoints, String tier, String earningRule, String nextTier,
                                  int pointsToNextTier, List<VoucherResponse> vouchers,
                                  List<RedemptionResponse> redemptions) {
    }

    public record RedeemRequest(@NotNull Long voucherId) {
    }

    public record RedeemResponse(boolean success, String message, RedemptionResponse redemption, int remainingPoints) {
    }
}
