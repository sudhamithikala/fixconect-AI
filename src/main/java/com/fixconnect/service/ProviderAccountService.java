package com.fixconnect.service;

import com.fixconnect.common.ApiException;
import com.fixconnect.domain.*;
import com.fixconnect.dto.CommonDtos.OfferingResponse;
import com.fixconnect.dto.ProviderDtos.*;
import com.fixconnect.repository.ProviderServiceOfferingRepository;
import com.fixconnect.repository.ServiceRequestRepository;
import com.fixconnect.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

/** Technician profile, verification, availability, location, payout settings and services & rates. */
@Service
public class ProviderAccountService {

    private final Lookup lookup;
    private final UserRepository users;
    private final ServiceRequestRepository requests;
    private final ProviderServiceOfferingRepository offerings;
    private final FileStorageService files;
    private final DtoMapper mapper;

    public ProviderAccountService(Lookup lookup, UserRepository users, ServiceRequestRepository requests,
                                  ProviderServiceOfferingRepository offerings, FileStorageService files,
                                  DtoMapper mapper) {
        this.lookup = lookup;
        this.users = users;
        this.requests = requests;
        this.offerings = offerings;
        this.files = files;
        this.mapper = mapper;
    }

    // ---------------------------------------------------------------- profile

    @Transactional(readOnly = true)
    public ProviderProfileResponse profile(Long providerId) {
        return toResponse(lookup.profileOf(providerId));
    }

    @Transactional
    public ProviderProfileResponse update(Long providerId, UpdateProviderProfileRequest req) {
        ProviderProfile p = lookup.profileOf(providerId);
        User u = p.getUser();
        String email = req.email().trim().toLowerCase();
        if (!email.equalsIgnoreCase(u.getEmail()) && users.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("Email is already used by another account");
        }
        u.setFullName(req.fullName().trim());
        u.setPhone(req.phone());
        u.setEmail(email);
        p.setCategory(lookup.category(req.categoryCode()));
        p.setHeadline(req.headline() == null || req.headline().isBlank() ? p.getCategory().getSpecialistTitle() : req.headline().trim());
        p.setSkills(req.skills().trim());
        p.setExperienceYears(req.experienceYears());
        p.setServiceArea(req.serviceArea().trim());
        return toResponse(p);
    }

    public ProviderProfileResponse toResponse(ProviderProfile p) {
        User u = p.getUser();
        long completed = requests.countByProvider_IdAndStatus(u.getId(), RequestStatus.COMPLETED);
        return new ProviderProfileResponse(u.getId(), u.getFullName(), u.getEmail(), u.getPhone(),
                mapper.categoryRef(p.getCategory()), p.getHeadline(), p.getSkills(), p.getExperienceYears(),
                p.getServiceArea(), p.getServiceRadiusKm(), p.getMaxDailyJobs(), p.isAvailable(), p.isProBadge(),
                DtoMapper.round1(p.getRating()), p.getReviewCount(), completed, completionRate(p, completed),
                acceptanceRate(p), u.getCreatedAt());
    }

    static double acceptanceRate(ProviderProfile p) {
        int total = p.getAcceptedCount() + p.getDeclinedCount();
        return total == 0 ? 100.0 : DtoMapper.round1(100.0 * p.getAcceptedCount() / total);
    }

    static double completionRate(ProviderProfile p, long completed) {
        return p.getAcceptedCount() == 0 ? 100.0
                : DtoMapper.round1(Math.min(100.0, 100.0 * completed / p.getAcceptedCount()));
    }

    // ---------------------------------------------------------------- verification

    @Transactional
    public VerificationResponse uploadIdProof(Long providerId, MultipartFile file) {
        ProviderProfile p = lookup.profileOf(providerId);
        p.setIdProofFile(files.store(file));
        // Verification is performed by the FixConnect back-office; a new upload resets it.
        p.setIdVerified(false);
        p.setIdVerifiedAt(null);
        return verification(p);
    }

    @Transactional(readOnly = true)
    public VerificationResponse verification(Long providerId) {
        return verification(lookup.profileOf(providerId));
    }

    private VerificationResponse verification(ProviderProfile p) {
        return new VerificationResponse(p.getIdProofFile() != null, FileStorageService.url(p.getIdProofFile()),
                p.isIdVerified(), p.getIdVerifiedAt(), p.isLicenseVerified(), p.isBackgroundCheckCleared(),
                p.isProBadge());
    }

    // ---------------------------------------------------------------- availability & location

    @Transactional
    public AvailabilityResponse setAvailability(Long providerId, boolean available) {
        ProviderProfile p = lookup.profileOf(providerId);
        if (available && !p.isApproved()) {
            throw ApiException.forbidden("Your profile is awaiting admin verification. You can go online once it is approved.");
        }
        p.setAvailable(available);
        return available
                ? new AvailabilityResponse(true, "Available for Work", "Status set to ONLINE. Nearby customers can now discover your services.")
                : new AvailabilityResponse(false, "Offline / Busy", "Status set to OFFLINE. New service dispatches paused.");
    }

    @Transactional
    public void updateLocation(Long providerId, LocationUpdateRequest req) {
        ProviderProfile p = lookup.profileOf(providerId);
        p.setLatitude(req.latitude());
        p.setLongitude(req.longitude());
        p.setLocationUpdatedAt(LocalDateTime.now());
        // keep ETA of the job currently en route roughly in sync with the new position
        requests.findByProvider_IdAndStatusInOrderByPreferredDateAscCreatedAtAsc(providerId, java.util.EnumSet.of(RequestStatus.EN_ROUTE))
                .forEach(r -> {
                    Integer eta = Geo.etaMinutes(Geo.distanceKm(req.latitude(), req.longitude(), r.getLatitude(), r.getLongitude()));
                    if (eta != null) {
                        r.setEtaMinutes(eta);
                    }
                });
    }

    // ---------------------------------------------------------------- settings

    @Transactional(readOnly = true)
    public ProviderSettingsResponse settings(Long providerId) {
        return settings(lookup.profileOf(providerId));
    }

    @Transactional
    public ProviderSettingsResponse updateSettings(Long providerId, ProviderSettingsRequest req) {
        ProviderProfile p = lookup.profileOf(providerId);
        User u = p.getUser();
        if (req.serviceRadiusKm() != null) p.setServiceRadiusKm(req.serviceRadiusKm());
        if (req.maxDailyJobs() != null) p.setMaxDailyJobs(req.maxDailyJobs());
        if (req.bankAccountHolder() != null) p.setBankAccountHolder(req.bankAccountHolder());
        if (req.bankAccountNumber() != null) p.setBankAccountNumber(req.bankAccountNumber());
        if (req.bankIfsc() != null) p.setBankIfsc(req.bankIfsc());
        if (req.upiId() != null) p.setUpiId(req.upiId());
        if (req.smsAlerts() != null) u.setSmsAlerts(req.smsAlerts());
        if (req.whatsappUpdates() != null) u.setWhatsappUpdates(req.whatsappUpdates());
        if (req.emailNotifications() != null) u.setEmailNotifications(req.emailNotifications());
        return settings(p);
    }

    private ProviderSettingsResponse settings(ProviderProfile p) {
        String acc = p.getBankAccountNumber();
        String masked = acc == null || acc.length() < 4 ? acc : "XXXXXX" + acc.substring(acc.length() - 4);
        User u = p.getUser();
        return new ProviderSettingsResponse(p.getServiceRadiusKm(), p.getMaxDailyJobs(), p.getBankAccountHolder(),
                masked, p.getBankIfsc(), p.getUpiId(), u.isSmsAlerts(), u.isWhatsappUpdates(), u.isEmailNotifications());
    }

    // ---------------------------------------------------------------- services & rates

    @Transactional(readOnly = true)
    public List<OfferingResponse> offerings(Long providerId) {
        return offerings.findByProvider_IdOrderByIdAsc(providerId).stream().map(mapper::offering).toList();
    }

    @Transactional
    public OfferingResponse addOffering(Long providerId, OfferingRequest req) {
        ProviderServiceOffering o = new ProviderServiceOffering();
        o.setProvider(lookup.user(providerId));
        apply(o, req);
        offerings.save(o);
        return mapper.offering(o);
    }

    @Transactional
    public OfferingResponse updateOffering(Long providerId, Long id, OfferingRequest req) {
        ProviderServiceOffering o = offerings.findByIdAndProvider_Id(id, providerId)
                .orElseThrow(() -> ApiException.notFound("Service offering"));
        apply(o, req);
        return mapper.offering(o);
    }

    @Transactional
    public void deleteOffering(Long providerId, Long id) {
        ProviderServiceOffering o = offerings.findByIdAndProvider_Id(id, providerId)
                .orElseThrow(() -> ApiException.notFound("Service offering"));
        offerings.delete(o);
    }

    private static void apply(ProviderServiceOffering o, OfferingRequest req) {
        o.setTitle(req.title().trim());
        o.setDescription(req.description());
        o.setBaseRate(req.baseRate());
        if (req.active() != null) {
            o.setActive(req.active());
        }
    }
}
