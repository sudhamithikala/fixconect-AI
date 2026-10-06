package com.fixconnect.service;

import com.fixconnect.common.ApiException;
import com.fixconnect.common.MessageResponse;
import com.fixconnect.domain.*;
import com.fixconnect.dto.AuthDtos.*;
import com.fixconnect.repository.AddressRepository;
import com.fixconnect.repository.PasswordResetTokenRepository;
import com.fixconnect.repository.ProviderProfileRepository;
import com.fixconnect.repository.UserRepository;
import com.fixconnect.security.FirebaseTokenVerifier;
import com.fixconnect.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository users;
    private final AddressRepository addresses;
    private final ProviderProfileRepository profiles;
    private final PasswordResetTokenRepository resetTokens;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final FirebaseTokenVerifier firebase;
    private final Lookup lookup;
    private final DtoMapper mapper;
    private final NotificationService notifications;
    private final boolean exposeResetToken;
    private final EmailService email;
    private final String appBaseUrl;
    private final String adminAlertEmail;
    private final int resetTokenMinutes;
    private final long resendCooldownSeconds;

    private static final SecureRandom RANDOM = new SecureRandom();

    public static final String DEACTIVATED_MESSAGE =
            "Your account has been deactivated. Please contact " + EmailService.SUPPORT_EMAIL + ".";

    public AuthService(UserRepository users, AddressRepository addresses, ProviderProfileRepository profiles,
                       PasswordResetTokenRepository resetTokens, PasswordEncoder encoder, JwtService jwt,
                       FirebaseTokenVerifier firebase, Lookup lookup, DtoMapper mapper,
                       NotificationService notifications,
                       @Value("${fixconnect.auth.expose-reset-token:false}") boolean exposeResetToken,
                       EmailService email,
                       @Value("${fixconnect.app-base-url:http://localhost:8080}") String appBaseUrl,
                       @Value("${fixconnect.admin.alert-email:" + EmailService.SUPPORT_EMAIL + "}") String adminAlertEmail,
                       @Value("${fixconnect.auth.reset-token-minutes:30}") int resetTokenMinutes,
                       @Value("${fixconnect.auth.reset-resend-cooldown-seconds:60}") long resendCooldownSeconds) {
        this.users = users;
        this.addresses = addresses;
        this.profiles = profiles;
        this.resetTokens = resetTokens;
        this.encoder = encoder;
        this.jwt = jwt;
        this.firebase = firebase;
        this.lookup = lookup;
        this.mapper = mapper;
        this.notifications = notifications;
        this.exposeResetToken = exposeResetToken;
        this.email = email;
        this.appBaseUrl = appBaseUrl.endsWith("/") ? appBaseUrl.substring(0, appBaseUrl.length() - 1) : appBaseUrl;
        this.adminAlertEmail = adminAlertEmail;
        this.resetTokenMinutes = resetTokenMinutes;
        this.resendCooldownSeconds = resendCooldownSeconds;
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        User user = users.findByEmailIgnoreCase(req.email().trim())
                .filter(u -> u.getPasswordHash() != null && encoder.matches(req.password(), u.getPasswordHash()))
                .filter(u -> u.getRole() == req.role())
                .orElseThrow(() -> ApiException.unauthorized("Invalid Email, Password or Role"));
        if (!user.isActive()) {
            throw ApiException.forbidden(DEACTIVATED_MESSAGE);
        }
        String who = switch (user.getRole()) {
            case CUSTOMER -> "Customer";
            case PROVIDER -> "Provider";
            case ADMIN -> "Admin";
        };
        return authResponse(user, who + " Login Successful");
    }

    @Transactional
    public AuthResponse registerCustomer(CustomerRegisterRequest req) {
        checkPasswords(req.password(), req.confirmPassword());
        ensureEmailFree(req.email());
        User u = newUser(req.fullName(), req.email(), req.phone(), Role.CUSTOMER);
        u.setPasswordHash(encoder.encode(req.password()));
        users.save(u);

        Address a = new Address();
        a.setUser(u);
        a.setLabel("Home");
        a.setLine1(req.address().trim());
        a.setCity(req.city());
        a.setPincode(req.pincode());
        a.setPrimaryAddress(true);
        addresses.save(a);

        notifications.notify(u, "Welcome to FixConnect AI", "Your customer account is ready. Request your first home service anytime.", "SYSTEM", null);
        return authResponse(u, "Customer account created successfully");
    }

    @Transactional
    public AuthResponse registerProvider(ProviderRegisterRequest req) {
        checkPasswords(req.password(), req.confirmPassword());
        ensureEmailFree(req.email());
        ServiceCategory category = lookup.category(req.categoryCode());

        User u = newUser(req.fullName(), req.email(), req.phone(), Role.PROVIDER);
        u.setPasswordHash(encoder.encode(req.password()));
        users.save(u);

        ProviderProfile p = new ProviderProfile();
        p.setUser(u);
        p.setCategory(category);
        p.setHeadline(category.getSpecialistTitle());
        p.setSkills(category.getName());
        p.setExperienceYears(req.experienceYears());
        p.setServiceArea(req.serviceArea().trim());
        p.setAvailable(false); // can go online once an admin verifies the profile
        profiles.save(p);

        notifications.notify(u, "Registration received",
                "Upload your Government Photo ID (Aadhaar / PAN) from your profile. A FixConnect admin will verify your "
                        + "profile; once approved you can go online and start receiving jobs.",
                "SYSTEM", null);
        alertAdminNewProvider(u, category.getName(), p.getServiceArea(), "Registration form");
        return authResponse(u, "Service provider registered successfully");
    }

    @Transactional
    public AuthResponse googleLogin(GoogleLoginRequest req) {
        if (req.role() == Role.ADMIN) {
            throw ApiException.forbidden("Admins must sign in with e-mail and password on the admin login page");
        }
        Jwt token = firebase.verify(req.idToken());
        String email = token.getClaimAsString("email");
        if (email == null) {
            throw ApiException.unauthorized("Google account has no e-mail address");
        }
        Optional<User> existing = users.findByEmailIgnoreCase(email);
        if (existing.isPresent()) {
            User u = existing.get();
            if (!u.isActive()) {
                throw ApiException.forbidden(DEACTIVATED_MESSAGE);
            }
            if (u.getRole() != req.role()) {
                throw new ApiException(HttpStatus.CONFLICT, "This Google account is registered as " + u.getRole()
                        + ". Switch the role selector and try again.");
            }
            return authResponse(u, "Google login successful");
        }
        String name = Optional.ofNullable(token.getClaimAsString("name")).orElse(email.substring(0, email.indexOf('@')));
        User u = newUser(name, email, req.phone(), req.role());
        u.setAuthProvider(AuthProvider.GOOGLE);
        users.save(u);
        if (req.role() == Role.PROVIDER) {
            ProviderProfile p = new ProviderProfile();
            p.setUser(u);
            p.setCategory(lookup.category("GENERAL"));
            p.setAvailable(false);
            profiles.save(p);
            alertAdminNewProvider(u, p.getCategory() != null ? p.getCategory().getName() : null, null, "Google");
        }
        return authResponse(u, "Google account registered - please complete your profile");
    }

    /** E-mails the admin a link straight to the verification queue in the admin console. */
    private void alertAdminNewProvider(User u, String category, String serviceArea, String method) {
        email.sendNewProviderAlert(adminAlertEmail, u.getFullName(), u.getEmail(), u.getPhone(), category, serviceArea,
                method, appBaseUrl + "/admin-dashboard.html#section-verification");
    }

    @Transactional
    public ForgotPasswordResponse forgotPassword(ForgotPasswordRequest req) {
        // Same answer whether or not the account exists, so the endpoint cannot be used to discover e-mails.
        String generic = "If an account exists for " + req.email().trim()
                + ", we've sent a password reset link. Please check your inbox (and spam folder).";
        Optional<User> found = users.findByEmailIgnoreCase(req.email().trim()).filter(User::isActive);
        if (found.isEmpty()) {
            return new ForgotPasswordResponse(true, generic, null);
        }
        User user = found.get();

        // Throttle: one e-mail per cooldown window per account
        Optional<PasswordResetToken> last = resetTokens.findFirstByUser_IdOrderByCreatedAtDesc(user.getId());
        if (last.isPresent() && last.get().getCreatedAt() != null
                && last.get().getCreatedAt().isAfter(LocalDateTime.now().minusSeconds(resendCooldownSeconds))) {
            return new ForgotPasswordResponse(true, generic, null);
        }

        // Only the newest link works
        resetTokens.findByUser_IdAndUsedFalse(user.getId()).forEach(old -> old.setUsed(true));

        String rawToken = newToken();
        PasswordResetToken t = new PasswordResetToken();
        t.setUser(user);
        t.setToken(sha256(rawToken));
        t.setExpiresAt(LocalDateTime.now().plusMinutes(resetTokenMinutes));
        resetTokens.save(t);

        String link = appBaseUrl + "/forgot-password.html?token=" + rawToken;
        email.sendPasswordResetLink(user.getEmail(), user.getFullName(), link, resetTokenMinutes);
        return new ForgotPasswordResponse(true, generic, exposeResetToken ? rawToken : null);
    }

    /** Checks a reset link before showing the "new password" form. */
    @Transactional(readOnly = true)
    public ResetTokenStatus validateResetToken(String rawToken) {
        return findUsableToken(rawToken)
                .map(t -> new ResetTokenStatus(true, maskEmail(t.getUser().getEmail()), "Choose a new password.",
                        t.getExpiresAt()))
                .orElse(new ResetTokenStatus(false, null,
                        "This reset link is invalid, has expired or was already used. Please request a new one.", null));
    }

    @Transactional
    public MessageResponse resetPassword(ResetPasswordRequest req) {
        checkPasswords(req.newPassword(), req.confirmPassword());
        PasswordResetToken t = findUsableToken(req.token())
                .orElseThrow(() -> ApiException.badRequest(
                        "This reset link is invalid, has expired or was already used. Please request a new one."));
        t.setUsed(true);
        t.setUsedAt(LocalDateTime.now());
        User u = t.getUser();
        u.setPasswordHash(encoder.encode(req.newPassword()));
        email.sendPasswordChanged(u.getEmail(), u.getFullName(), appBaseUrl + "/login.html");
        return MessageResponse.ok("Password has been reset. You can now log in with your new password.");
    }

    private Optional<PasswordResetToken> findUsableToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        return resetTokens.findByToken(sha256(rawToken.trim()))
                .filter(x -> !x.isUsed() && x.getExpiresAt().isAfter(LocalDateTime.now()));
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256(String value) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return email;
        }
        return email.charAt(0) + "*".repeat(Math.max(1, at - 2)) + email.charAt(at - 1) + email.substring(at);
    }

    @Transactional
    public MessageResponse changePassword(Long userId, ChangePasswordRequest req) {
        User u = lookup.user(userId);
        if (u.getPasswordHash() != null && !encoder.matches(req.currentPassword(), u.getPasswordHash())) {
            throw ApiException.badRequest("Current password is incorrect");
        }
        u.setPasswordHash(encoder.encode(req.newPassword()));
        return MessageResponse.ok("Password updated successfully!");
    }

    @Transactional(readOnly = true)
    public UserSummary me(Long userId) {
        return mapper.user(lookup.user(userId));
    }

    // ---------------------------------------------------------------------------------------------

    private AuthResponse authResponse(User u, String message) {
        String redirect = switch (u.getRole()) {
            case CUSTOMER -> "customer-profile.html";
            case PROVIDER -> "provider-profile.html";
            case ADMIN -> "admin-dashboard.html";
        };
        return new AuthResponse(true, message, u.getRole(), jwt.issueToken(u), "Bearer", jwt.expiresInSeconds(),
                redirect, mapper.user(u));
    }

    private User newUser(String name, String email, String phone, Role role) {
        User u = new User();
        u.setFullName(name.trim());
        u.setEmail(email.trim().toLowerCase());
        u.setPhone(phone);
        u.setRole(role);
        return u;
    }

    private void ensureEmailFree(String email) {
        if (users.existsByEmailIgnoreCase(email.trim())) {
            throw ApiException.conflict("An account with this email already exists");
        }
    }

    private static void checkPasswords(String password, String confirm) {
        if (!password.equals(confirm)) {
            throw ApiException.badRequest("Passwords do not match.");
        }
    }
}
