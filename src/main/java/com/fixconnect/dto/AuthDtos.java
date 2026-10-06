package com.fixconnect.dto;

import com.fixconnect.domain.AuthProvider;
import com.fixconnect.domain.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.time.LocalDateTime;

public final class AuthDtos {

    private AuthDtos() {
    }

    /** Indian mobile: 10 digits starting with 6-9, optional +91 / 0 prefix, spaces or dashes allowed (e.g. +91 98765 43210). */
    public static final String PHONE_REGEX = "^(?:\\+91[\\s-]?|0)?[6-9]\\d{4}[\\s-]?\\d{5}$";
    public static final String PHONE_MESSAGE = "Enter a valid 10-digit Indian mobile number starting with 6, 7, 8 or 9 (e.g. +91 98765 43210).";

    /** New passwords: 8+ characters, starts with a capital letter, and has a lowercase letter, a digit and a symbol. */
    public static final String PASSWORD_REGEX = "^[A-Z](?=.*[a-z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{7,}$";
    public static final String PASSWORD_MESSAGE = "Password must be at least 8 characters, start with a capital letter, "
            + "and contain a lowercase letter, a number and a symbol (e.g. Fixconnect@1).";

    /** Person names: letters, spaces, dots, apostrophes and hyphens; starts with a letter; 2-60 characters. */
    public static final String NAME_REGEX = "^[A-Za-z][A-Za-z .'-]{1,59}$";
    public static final String NAME_MESSAGE = "Name must be 2-60 characters, start with a letter and contain only letters, spaces, dots, apostrophes or hyphens.";

    public static final String EMAIL_REGEX = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}$";
    public static final String EMAIL_MESSAGE = "Enter a valid e-mail address (e.g. name@example.com).";

    /** Street addresses: 10-300 characters, must contain letters; letters, digits, spaces and , . / # ( ) & : ; ' + - only. */
    public static final String ADDRESS_REGEX = "^(?=.*[A-Za-z])[A-Za-z0-9\\s,./#()&:;'+-]{10,300}$";
    public static final String LONG_ADDRESS_REGEX = "^(?=.*[A-Za-z])[A-Za-z0-9\\s,./#()&:;'+-]{10,400}$";
    public static final String ADDRESS_MESSAGE = "Enter the full address (house/flat no., street, area): at least 10 characters with letters, "
            + "using only letters, numbers, spaces and , . / # - ( ) & : ; ' +";

    public static final String CITY_REGEX = "^$|^[A-Za-z][A-Za-z .-]{1,59}$";
    public static final String CITY_MESSAGE = "City and state must be 2-60 letters (spaces, dots and hyphens allowed).";

    public static final String PINCODE_REGEX = "^$|^[1-9][0-9]{5}$";
    public static final String PINCODE_MESSAGE = "Pincode must be 6 digits and cannot start with 0.";

    public static final String AREA_REGEX = "^(?=.*[A-Za-z])[A-Za-z0-9\\s,./&()'-]{3,200}$";
    public static final String AREA_MESSAGE = "Service area must be 3-200 characters with letters (e.g. Jubilee Hills, Madhapur).";

    /** Free text (descriptions, skills, titles): empty or must contain at least one letter. */
    public static final String TEXT_REGEX = "(?s)^$|^(?=.*[A-Za-z]).*$";
    public static final String TEXT_MESSAGE = "Please write it in words - it must contain letters.";

    @Schema(description = "Login payload - same contract as the original Express /api/login")
    public record LoginRequest(
            @Schema(example = "customer@gmail.com") @NotBlank @Email String email,
            @Schema(example = "12345") @NotBlank String password,
            @Schema(example = "CUSTOMER") @NotNull Role role) {
    }

    public record CustomerRegisterRequest(
            @Schema(example = "Naga Sudha") @NotBlank @Pattern(regexp = NAME_REGEX, message = NAME_MESSAGE) String fullName,
            @Schema(example = "+91 98765 43210") @NotBlank @Pattern(regexp = PHONE_REGEX, message = PHONE_MESSAGE) String phone,
            @Schema(example = "nagasudha@example.com") @NotBlank @Email @Size(max = 120) @Pattern(regexp = EMAIL_REGEX, message = EMAIL_MESSAGE) String email,
            @Schema(example = "Plot 42, Jubilee Hills, Road No. 10") @NotBlank @Pattern(regexp = ADDRESS_REGEX, message = ADDRESS_MESSAGE) String address,
            @Schema(example = "Hyderabad") @Pattern(regexp = CITY_REGEX, message = CITY_MESSAGE) String city,
            @Schema(example = "500033") @Pattern(regexp = PINCODE_REGEX, message = PINCODE_MESSAGE) String pincode,
            @Schema(example = "Fixconnect@1") @NotBlank @Pattern(regexp = PASSWORD_REGEX, message = PASSWORD_MESSAGE) String password,
            @Schema(example = "Fixconnect@1") @NotBlank String confirmPassword) {
    }

    public record ProviderRegisterRequest(
            @Schema(example = "Rahul Kumar") @NotBlank @Pattern(regexp = NAME_REGEX, message = NAME_MESSAGE) String fullName,
            @Schema(example = "+91 90000 11111") @NotBlank @Pattern(regexp = PHONE_REGEX, message = PHONE_MESSAGE) String phone,
            @Schema(example = "rahul.tech@example.com") @NotBlank @Email @Size(max = 120) @Pattern(regexp = EMAIL_REGEX, message = EMAIL_MESSAGE) String email,
            @Schema(description = "Service category code (see GET /api/services). UI values map as: Electrician=ELECTRICAL, Plumber=PLUMBING, "
                    + "AC Technician=AC_REPAIR, Carpenter=CARPENTRY, Appliance Repair=APPLIANCE_REPAIR, Painter=PAINTING, Other=GENERAL",
                    example = "AC_REPAIR") @NotBlank String categoryCode,
            @Schema(example = "7") @NotNull @Min(0) @Max(60) Integer experienceYears,
            @Schema(example = "Jubilee Hills, Hyderabad") @NotBlank @Pattern(regexp = AREA_REGEX, message = AREA_MESSAGE) String serviceArea,
            @Schema(example = "Flat 101, Banjara Hills") @Pattern(regexp = "^$|" + ADDRESS_REGEX, message = ADDRESS_MESSAGE) String address,
            @Schema(example = "Fixconnect@1") @NotBlank @Pattern(regexp = PASSWORD_REGEX, message = PASSWORD_MESSAGE) String password,
            @Schema(example = "Fixconnect@1") @NotBlank String confirmPassword) {
    }

    @Schema(description = "Firebase ID token obtained on the client via signInWithPopup(auth, googleProvider) then user.getIdToken()")
    public record GoogleLoginRequest(
            @NotBlank String idToken,
            @NotNull Role role,
            String phone) {
    }

    public record ForgotPasswordRequest(@Schema(example = "customer@gmail.com") @NotBlank @Email String email) {
    }

    public record ResetPasswordRequest(
            @NotBlank String token,
            @NotBlank @Pattern(regexp = PASSWORD_REGEX, message = PASSWORD_MESSAGE) String newPassword,
            @NotBlank String confirmPassword) {
    }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Pattern(regexp = PASSWORD_REGEX, message = PASSWORD_MESSAGE) String newPassword) {
    }

    public record UserSummary(Long id, String fullName, String email, String phone, Role role,
                              AuthProvider authProvider, LocalDateTime createdAt,
                              @Schema(description = "Profile picture URL, null when the user has not uploaded one") String photoUrl) {
    }

    public record AuthResponse(
            boolean success,
            String message,
            Role role,
            String token,
            String tokenType,
            long expiresIn,
            @Schema(description = "Page the front-end should open next") String redirectTo,
            UserSummary user) {
    }

    public record ResetTokenStatus(
            boolean valid,
            @Schema(description = "Masked e-mail of the account, e.g. c******r@gmail.com") String email,
            String message,
            LocalDateTime expiresAt) {
    }

    public record ForgotPasswordResponse(
            boolean success,
            String message,
            @Schema(description = "Only returned when fixconnect.auth.expose-reset-token=true (development)") String resetToken) {
    }
}
