package com.fixconnect.dto;

import com.fixconnect.domain.Severity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public final class AiDtos {

    private AiDtos() {
    }

    public record DiagnoseRequest(
            @Schema(example = "AC unit is leaking water from the front vent and blowing lukewarm air.")
            @NotBlank @Size(min = 5, max = 2000) String description,
            @Schema(description = "Optional hint", example = "AC_REPAIR") String categoryCode) {
    }

    public record DiagnosisResponse(
            String detectedIssue,
            String summary,
            String categoryCode,
            String categoryName,
            Severity severity,
            String recommendedSpecialist,
            BigDecimal estimatedCostMin,
            BigDecimal estimatedCostMax,
            @Schema(example = "₹650 - ₹1,200") String estimatedCostLabel,
            @Schema(example = "96.4") double confidence,
            boolean emergencyRecommended,
            List<String> tips) {
    }

    public record RepairGuideRequest(
            @Schema(example = "AC compressor turns off after 5 minutes and shows Error E4.")
            @NotBlank @Size(min = 5, max = 2000) String description,
            String categoryCode) {
    }

    public record RepairGuideResponse(
            String probableCause,
            String categoryCode,
            List<String> requiredTools,
            List<String> diagnosticSteps,
            List<String> safetyNotes,
            int estimatedDurationMinutes,
            double confidence) {
    }
}
