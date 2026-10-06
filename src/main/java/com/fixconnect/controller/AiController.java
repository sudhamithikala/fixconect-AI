package com.fixconnect.controller;

import com.fixconnect.dto.AiDtos.*;
import com.fixconnect.service.AiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
@Tag(name = "04. AI")
public class AiController {

    private final AiService ai;

    public AiController(AiService ai) {
        this.ai = ai;
    }

    @PostMapping("/diagnose")
    @Operation(summary = "AI Problem Analysis - predicts root cause, category, severity, specialist and cost (public)")
    public DiagnosisResponse diagnose(@Valid @RequestBody DiagnoseRequest req) {
        return ai.diagnose(req.description(), req.categoryCode());
    }

    @PostMapping("/repair-guide")
    @Operation(summary = "AI Repair Guide Assistant for technicians - tools, steps and safety notes (PROVIDER)")
    public RepairGuideResponse repairGuide(@Valid @RequestBody RepairGuideRequest req) {
        return ai.repairGuide(req.description(), req.categoryCode());
    }
}
