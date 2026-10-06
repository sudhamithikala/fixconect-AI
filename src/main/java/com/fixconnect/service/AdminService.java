package com.fixconnect.service;

import com.fixconnect.common.ApiException;
import com.fixconnect.domain.*;
import com.fixconnect.dto.AdminDtos.*;
import com.fixconnect.repository.AddressRepository;
import com.fixconnect.repository.ProviderProfileRepository;
import com.fixconnect.repository.ServiceRequestRepository;
import com.fixconnect.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

/**
 * Admin console: view customers and service providers, verify providers, deactivate / reactivate accounts.
 * Admins do not manage customers' bookings - only their account status.
 */
@Service
public class AdminService {

    public enum StatusFilter { ALL, ACTIVE, DEACTIVATED }

    public enum VerificationFilter { ALL, PENDING, VERIFIED }

    private final UserRepository users;
    private final ProviderProfileRepository profiles;
    private final ServiceRequestRepository requests;
    private final AddressRepository addresses;
    private final Lookup lookup;
    private final DtoMapper mapper;
    private final NotificationService notifications;
    private final TimelineRecorder timeline;
    private final EmailService email;
    /** Public address of the site (fixconnect.app-base-url) - used for the "Log in" buttons in e-mails. */
    private final String appBaseUrl;

    public AdminService(UserRepository users, ProviderProfileRepository profiles, ServiceRequestRepository requests,
                        AddressRepository addresses, Lookup lookup, DtoMapper mapper,
                        NotificationService notifications, TimelineRecorder timeline, EmailService email,
                        @Value("${fixconnect.app-base-url:http://localhost:8080}") String appBaseUrl) {
        this.appBaseUrl = appBaseUrl.endsWith("/") ? appBaseUrl.substring(0, appBaseUrl.length() - 1) : appBaseUrl;
        this.users = users;
        this.profiles = profiles;
        this.requests = requests;
        this.addresses = addresses;
        this.lookup = lookup;
        this.mapper = mapper;
        this.notifications = notifications;
        this.timeline = timeline;
        this.email = email;
    }

    // ---------------------------------------------------------------- dashboard

    @Transactional(readOnly = true)
    public AdminDashboardResponse dashboard(Long adminId) {
        List<User> customers = users.findByRole(Role.CUSTOMER);
        List<ProviderProfile> providers = profiles.findAll();
        List<ServiceRequest> all = requests.findAll();

        long activeCustomers = customers.stream().filter(User::isActive).count();
        long verified = providers.stream().filter(ProviderProfile::isApproved).count();
        long deactivatedProviders = providers.stream().filter(p -> !p.getUser().isActive()).count();
        long pending = providers.stream().filter(p -> !p.isApproved() && p.getUser().isActive()).count();
        long online = providers.stream().filter(p -> p.isAvailable() && p.getUser().isActive()).count();
        long open = all.stream().filter(r -> RequestStatus.ACTIVE.contains(r.getStatus())).count();
        long completed = all.stream().filter(r -> r.getStatus() == RequestStatus.COMPLETED).count();

        AdminStats stats = new AdminStats(customers.size(), activeCustomers, customers.size() - activeCustomers,
                providers.size(), verified, pending, deactivatedProviders, online, open, completed);

        List<ProviderAdminView> pendingList = providers.stream()
                .filter(p -> !p.isApproved() && p.getUser().isActive())
                .sorted(Comparator.comparing((ProviderProfile p) -> p.getUser().getCreatedAt()).reversed())
                .limit(5).map(this::providerView).toList();
        List<CustomerAdminView> recent = customers.stream()
                .sorted(Comparator.comparing(User::getCreatedAt).reversed())
                .limit(5).map(this::customerView).toList();
        return new AdminDashboardResponse(lookup.user(adminId).getFullName(), stats, pendingList, recent);
    }

    // ---------------------------------------------------------------- lists

    @Transactional(readOnly = true)
    public List<CustomerAdminView> customers(String q, StatusFilter status) {
        String query = norm(q);
        return users.findByRole(Role.CUSTOMER).stream()
                .filter(u -> matchesStatus(u, status))
                .filter(u -> query == null || contains(u.getFullName(), query) || contains(u.getEmail(), query)
                        || contains(u.getPhone(), query))
                .sorted(Comparator.comparing(User::getCreatedAt).reversed())
                .map(this::customerView)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProviderAdminView> providers(String q, StatusFilter status, VerificationFilter verification,
                                             String categoryCode) {
        String query = norm(q);
        return profiles.findAll().stream()
                .filter(p -> matchesStatus(p.getUser(), status))
                .filter(p -> verification == null || verification == VerificationFilter.ALL
                        || (verification == VerificationFilter.VERIFIED) == p.isApproved())
                .filter(p -> categoryCode == null || categoryCode.isBlank()
                        || (p.getCategory() != null && p.getCategory().getCode().equalsIgnoreCase(categoryCode)))
                .filter(p -> query == null || contains(p.getUser().getFullName(), query)
                        || contains(p.getUser().getEmail(), query) || contains(p.getUser().getPhone(), query)
                        || contains(p.getServiceArea(), query) || contains(p.getSkills(), query))
                .sorted(Comparator.comparing((ProviderProfile p) -> p.isApproved())
                        .thenComparing(p -> p.getUser().getCreatedAt(), Comparator.reverseOrder()))
                .map(this::providerView)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProviderAdminView provider(Long userId) {
        return providerView(lookup.profileOf(requireRole(userId, Role.PROVIDER).getId()));
    }

    @Transactional(readOnly = true)
    public CustomerAdminView customer(Long userId) {
        return customerView(requireRole(userId, Role.CUSTOMER));
    }

    // ---------------------------------------------------------------- provider verification

    @Transactional
    public ProviderAdminView updateVerification(Long providerId, VerificationUpdateRequest req) {
        User u = requireRole(providerId, Role.PROVIDER);
        ProviderProfile p = lookup.profileOf(u.getId());
        boolean wasApproved = p.isApproved();

        p.setIdVerified(req.idVerified());
        p.setIdVerifiedAt(req.idVerified() ? (p.getIdVerifiedAt() != null ? p.getIdVerifiedAt() : LocalDateTime.now()) : null);
        p.setLicenseVerified(req.licenseVerified());
        p.setBackgroundCheckCleared(req.backgroundCheckCleared());
        p.setVerificationNote(blankToNull(req.note()));
        p.setVerificationUpdatedAt(LocalDateTime.now());

        if (p.isApproved() && !wasApproved) {
            notifications.notify(u, "Profile verified ✅",
                    "Your FixConnect Pro profile is approved. Go online from your dashboard to start receiving jobs.",
                    "SYSTEM", null);
            email.sendAccountNotice(u.getEmail(), u.getFullName(), "Your FixConnect profile is verified",
                    "Good news! Your technician profile has been verified by the FixConnect team. "
                            + "Log in and switch to <strong>Available for Work</strong> to start receiving jobs.",
                    appBaseUrl + "/login.html", "Log in");
        } else if (!p.isApproved() && wasApproved) {
            p.setAvailable(false);
            notifications.notify(u, "Verification withdrawn",
                    "Your verification was updated by FixConnect" + (p.getVerificationNote() != null ? ": " + p.getVerificationNote() : ".")
                            + " You are offline until it is approved again.", "SYSTEM", null);
        } else if (!p.isApproved()) {
            notifications.notify(u, "Verification update",
                    "Your verification is in progress" + (p.getVerificationNote() != null ? ": " + p.getVerificationNote() : "."),
                    "SYSTEM", null);
        }
        return providerView(p);
    }

    // ---------------------------------------------------------------- deactivate / reactivate

    @Transactional
    public AccountStatusResponse setStatus(Long adminId, Long userId, AccountStatusRequest req) {
        User u = lookup.user(userId);
        if (u.getRole() == Role.ADMIN) {
            throw ApiException.forbidden("Admin accounts cannot be deactivated from the console");
        }
        if (u.getId().equals(adminId)) {
            throw ApiException.forbidden("You cannot change your own account status");
        }
        boolean activate = req.active();
        if (u.isActive() == activate) {
            throw ApiException.conflict("Account is already " + (activate ? "active" : "deactivated"));
        }

        int affected = 0;
        if (activate) {
            u.setActive(true);
            u.setDeactivatedAt(null);
            u.setDeactivationReason(null);
            notifications.notify(u, "Account reactivated", "Welcome back! Your FixConnect account has been reactivated.",
                    "SYSTEM", null);
            email.sendAccountNotice(u.getEmail(), u.getFullName(), "Your FixConnect account is active again",
                    "Your FixConnect AI account has been reactivated. You can log in again.",
                    appBaseUrl + "/login.html", "Log in");
        } else {
            u.setActive(false);
            u.setDeactivatedAt(LocalDateTime.now());
            u.setDeactivationReason(blankToNull(req.reason()));
            affected = u.getRole() == Role.PROVIDER ? onProviderDeactivated(u) : onCustomerDeactivated(u);
            email.sendAccountNotice(u.getEmail(), u.getFullName(), "Your FixConnect account has been deactivated",
                    "Your FixConnect AI account has been deactivated"
                            + (u.getDeactivationReason() != null ? " for the following reason: <em>" + escape(u.getDeactivationReason()) + "</em>" : "")
                            + ". If you think this is a mistake, contact " + EmailService.SUPPORT_EMAIL + ".");
        }
        String who = u.getRole() == Role.PROVIDER ? "Service provider" : "Customer";
        String msg = who + " " + u.getFullName() + (activate ? " reactivated" : " deactivated")
                + (affected > 0 ? " (" + affected + " open request(s) updated)" : "");
        return new AccountStatusResponse(true, msg, u.getId(), u.getRole(), u.isActive(), affected);
    }

    /** Provider goes offline; bookings that were sent only to them go back to the shared pool. */
    private int onProviderDeactivated(User u) {
        profiles.findByUser_Id(u.getId()).ifPresent(p -> p.setAvailable(false));
        int n = 0;
        for (ServiceRequest r : requests.findByStatusAndProvider_IdOrderByEmergencyDescCreatedAtAsc(RequestStatus.PENDING, u.getId())) {
            r.setProvider(null);
            r.getDeclinedProviderIds().add(u.getId());
            timeline.record(r, RequestStatus.PENDING, "Selected technician unavailable - finding another technician");
            notifications.notify(r.getCustomer(), "Finding another technician",
                    "Your selected technician is no longer available. We are matching you with another verified technician.",
                    "REQUEST", r.getId());
            n++;
        }
        return n;
    }

    /** Pending requests of a deactivated customer are cancelled so technicians don't see them. */
    private int onCustomerDeactivated(User u) {
        int n = 0;
        for (ServiceRequest r : requests.findByCustomer_IdAndStatusInOrderByCreatedAtDesc(u.getId(), EnumSet.of(RequestStatus.PENDING))) {
            r.setStatus(RequestStatus.CANCELLED);
            r.setCancelledAt(LocalDateTime.now());
            r.setCancelReason("Customer account deactivated");
            timeline.record(r, RequestStatus.CANCELLED, "Cancelled - customer account deactivated");
            n++;
        }
        return n;
    }

    // ---------------------------------------------------------------- mapping

    private CustomerAdminView customerView(User u) {
        String primary = addresses.findFirstByUser_IdAndPrimaryAddressTrue(u.getId()).map(Address::fullText).orElse(null);
        long total = requests.countByCustomer_IdAndStatusIn(u.getId(), EnumSet.allOf(RequestStatus.class));
        long done = requests.countByCustomer_IdAndStatusIn(u.getId(), EnumSet.of(RequestStatus.COMPLETED));
        return new CustomerAdminView(u.getId(), u.getFullName(), u.getEmail(), u.getPhone(), primary, u.isActive(),
                u.getDeactivatedAt(), u.getDeactivationReason(), total, done, u.getLoyaltyPoints(), u.getCreatedAt());
    }

    private ProviderAdminView providerView(ProviderProfile p) {
        User u = p.getUser();
        long completed = requests.countByProvider_IdAndStatus(u.getId(), RequestStatus.COMPLETED);
        return new ProviderAdminView(u.getId(), u.getFullName(), u.getEmail(), u.getPhone(),
                mapper.categoryRef(p.getCategory()), p.getHeadline(), p.getSkills(), p.getExperienceYears(),
                p.getServiceArea(), u.isActive(), u.getDeactivatedAt(), u.getDeactivationReason(), p.isApproved(),
                p.isIdVerified(), p.isLicenseVerified(), p.isBackgroundCheckCleared(), p.getIdProofFile() != null,
                FileStorageService.url(p.getIdProofFile()), p.getVerificationNote(), p.getVerificationUpdatedAt(),
                p.isAvailable(), DtoMapper.round1(p.getRating()), p.getReviewCount(), completed, u.getCreatedAt());
    }

    private User requireRole(Long userId, Role role) {
        User u = lookup.user(userId);
        if (u.getRole() != role) {
            throw ApiException.notFound(role == Role.PROVIDER ? "Service provider" : "Customer");
        }
        return u;
    }

    private static boolean matchesStatus(User u, StatusFilter status) {
        if (status == null || status == StatusFilter.ALL) return true;
        return (status == StatusFilter.ACTIVE) == u.isActive();
    }

    private static String norm(String q) {
        return q == null || q.isBlank() ? null : q.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean contains(String s, String q) {
        return s != null && s.toLowerCase(Locale.ROOT).contains(q);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String escape(String s) {
        return org.springframework.web.util.HtmlUtils.htmlEscape(s);
    }
}
