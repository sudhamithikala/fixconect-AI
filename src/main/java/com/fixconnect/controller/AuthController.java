package com.fixconnect.controller;

import com.fixconnect.common.MessageResponse;
import com.fixconnect.dto.AuthDtos.*;
import com.fixconnect.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "01. Auth")
@SecurityRequirements
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/login")
    @Operation(summary = "Login as customer or provider",
            description = "Role must match the account. Demo: customer@gmail.com / 12345 (CUSTOMER), provider@gmail.com / 12345 (PROVIDER).")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        return auth.login(req);
    }

    @PostMapping("/register/customer")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create customer account (customer-register.html / signup.html - Customer)")
    public AuthResponse registerCustomer(@Valid @RequestBody CustomerRegisterRequest req) {
        return auth.registerCustomer(req);
    }

    @PostMapping("/register/provider")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register as service provider (provider-register.html / signup.html - Provider)",
            description = "Upload the ID proof afterwards with POST /api/provider/profile/id-proof.")
    public AuthResponse registerProvider(@Valid @RequestBody ProviderRegisterRequest req) {
        return auth.registerProvider(req);
    }

    @PostMapping("/google")
    @Operation(summary = "Continue with Google (Firebase ID token)",
            description = "Verifies the Firebase ID token from signInWithPopup and logs in or creates the account.")
    public AuthResponse google(@Valid @RequestBody GoogleLoginRequest req) {
        return auth.googleLogin(req);
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "E-mail a password-reset link (forgot-password.html)",
            description = "Always returns the same message (does not reveal whether the e-mail is registered). "
                    + "The link is valid for 30 minutes, single use; requesting a new one invalidates older links.")
    public ForgotPasswordResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        return auth.forgotPassword(req);
    }

    @GetMapping("/reset-password/validate")
    @Operation(summary = "Check a reset link token before showing the new-password form")
    public ResetTokenStatus validateResetToken(@RequestParam String token) {
        return auth.validateResetToken(token);
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Set a new password using the token from the e-mailed link (sends a confirmation e-mail)")
    public MessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        return auth.resetPassword(req);
    }
}
