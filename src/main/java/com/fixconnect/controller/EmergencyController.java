package com.fixconnect.controller;

import com.fixconnect.dto.RequestDtos.SosRequest;
import com.fixconnect.dto.RequestDtos.SosResponse;
import com.fixconnect.security.CurrentUser;
import com.fixconnect.service.CustomerRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customer/emergency")
@Tag(name = "07. Customer - Emergency SOS")
public class EmergencyController {

    private final CustomerRequestService service;
    private final CurrentUser currentUser;

    public EmergencyController(CustomerRequestService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @PostMapping("/sos")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Dispatch SOS technician",
            description = "Creates a CRITICAL priority request and broadcasts it to available technicians within the SOS radius.")
    public SosResponse sos(@Valid @RequestBody SosRequest req) {
        return service.sos(currentUser.id(), req);
    }
}
