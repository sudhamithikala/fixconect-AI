package com.fixconnect.controller;

import com.fixconnect.dto.CommonDtos.HealthResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@Tag(name = "00. Health")
@SecurityRequirements
public class HealthController {

    @GetMapping("/")
    @Operation(summary = "Opens the website home page (index.html)")
    public ResponseEntity<Void> root() {
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, "/index.html").build();
    }

    @GetMapping("/api/health")
    @Operation(summary = "Health status")
    public HealthResponse health() {
        return new HealthResponse("UP", "fixconnect-backend", LocalDateTime.now());
    }
}
