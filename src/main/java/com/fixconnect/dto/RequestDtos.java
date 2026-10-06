package com.fixconnect.dto;

import com.fixconnect.domain.EmergencyType;
import com.fixconnect.domain.RequestStatus;
import com.fixconnect.domain.Severity;
import com.fixconnect.domain.TimeSlot;
import com.fixconnect.dto.CommonDtos.CategoryRef;
import com.fixconnect.dto.CommonDtos.PersonRef;
import com.fixconnect.dto.CommonDtos.PhotoResponse;
import com.fixconnect.dto.CommonDtos.StatusEventResponse;
import com.fixconnect.dto.CommonDtos.TechnicianSummary;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class RequestDtos {

    private RequestDtos() {
    }

    @Schema(description = "Request a Home Service form. Provide either addressId (saved address) or address text. "
            + "Set technicianId to book a specific technician (\"Book Service\" button).")
    public record CreateServiceRequest(
            @Schema(example = "AC_REPAIR") @NotBlank String categoryCode,
            @Schema(example = "AC cooling not working") @Size(max = 150) String title,
            @Schema(example = "Indoor split AC blowing warm air, outdoor unit fan stopped.") @NotBlank @Size(min = 10, max = 2000, message = "Describe the problem in 10-2000 characters") @Pattern(regexp = AuthDtos.TEXT_REGEX, message = AuthDtos.TEXT_MESSAGE) String description,
            Long addressId,
            @Schema(example = "Plot 42, Jubilee Hills, Road No. 10, Hyderabad") @Pattern(regexp = "^$|" + AuthDtos.LONG_ADDRESS_REGEX, message = AuthDtos.ADDRESS_MESSAGE) String address,
            Double latitude,
            Double longitude,
            @NotNull @FutureOrPresent LocalDate preferredDate,
            @NotNull TimeSlot timeSlot,
            @Schema(description = "Mark as High Emergency Priority (dispatch under 30 mins)") boolean emergency,
            Long technicianId) {
    }

    public record RescheduleRequest(@NotNull @FutureOrPresent LocalDate preferredDate, @NotNull TimeSlot timeSlot) {
    }

    public record CancelRequest(@Size(max = 300) String reason) {
    }

    public record ReviewRequest(
            @NotNull @Min(1) @Max(5) Integer rating,
            @Schema(example = "Rahul fixed our AC unit in 30 minutes. Excellent work!") @Size(max = 1000) String comment) {
    }

    public record ServiceRequestResponse(
            Long id,
            @Schema(example = "CMP-9042") String ticketNo,
            @Schema(example = "BK-7721") String bookingNo,
            CategoryRef category,
            String title,
            String description,
            String address,
            Double latitude,
            Double longitude,
            LocalDate preferredDate,
            TimeSlot timeSlot,
            String timeSlotLabel,
            boolean emergency,
            Severity severity,
            RequestStatus status,
            String statusLabel,
            Integer etaMinutes,
            boolean warrantyClaim,
            boolean directBooking,
            String cancelReason,
            LocalDateTime createdAt,
            LocalDateTime acceptedAt,
            LocalDateTime completedAt,
            PersonRef customer,
            TechnicianSummary technician,
            Double distanceKm,
            Long invoiceId,
            BigDecimal invoiceTotal,
            Long warrantyId,
            Integer rating,
            List<PhotoResponse> photos) {
    }

    public record TrackingResponse(
            Long requestId,
            String bookingNo,
            RequestStatus status,
            String statusLabel,
            Integer etaMinutes,
            TechnicianSummary technician,
            Double technicianLatitude,
            Double technicianLongitude,
            LocalDateTime locationUpdatedAt,
            String destinationAddress,
            Double destinationLatitude,
            Double destinationLongitude,
            Double distanceKm) {
    }

    public record TimelineResponse(Long requestId, RequestStatus currentStatus, String currentLabel,
                                   Integer etaMinutes, List<StatusEventResponse> events) {
    }

    public record HistoryItem(Long requestId, String bookingNo, LocalDate date, String service, String technician,
                              BigDecimal cost, Integer rating) {
    }

    public record SosRequest(
            @NotNull EmergencyType type,
            Long addressId,
            @Schema(example = "Plot 42, Jubilee Hills, Hyderabad") @Size(max = 400) String location,
            Double latitude,
            Double longitude,
            @Schema(example = "Main water pipe burst in kitchen cabinet, water leaking fast...") @Size(max = 2000) String description) {
    }

    public record SosResponse(boolean success, String message, int techniciansNotified, String hotline,
                              int dispatchRadiusKm, ServiceRequestResponse request) {
    }
}
