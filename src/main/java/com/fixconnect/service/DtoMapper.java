package com.fixconnect.service;

import com.fixconnect.domain.*;
import com.fixconnect.dto.AuthDtos.UserSummary;
import com.fixconnect.dto.CommonDtos.*;
import com.fixconnect.dto.CustomerDtos.AddressResponse;
import com.fixconnect.dto.CustomerDtos.InvoiceResponse;
import com.fixconnect.dto.CustomerDtos.WarrantyResponse;
import com.fixconnect.dto.RequestDtos.ServiceRequestResponse;
import com.fixconnect.repository.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Entity -> DTO conversion. Must be called inside a transaction (lazy associations). */
@Component
public class DtoMapper {

    private final ProviderProfileRepository profiles;
    private final ProviderServiceOfferingRepository offerings;
    private final ServiceRequestRepository requests;
    private final InvoiceRepository invoices;
    private final WarrantyRepository warranties;
    private final ReviewRepository reviews;
    private final JobPhotoRepository photos;

    public DtoMapper(ProviderProfileRepository profiles, ProviderServiceOfferingRepository offerings,
                     ServiceRequestRepository requests, InvoiceRepository invoices, WarrantyRepository warranties,
                     ReviewRepository reviews, JobPhotoRepository photos) {
        this.profiles = profiles;
        this.offerings = offerings;
        this.requests = requests;
        this.invoices = invoices;
        this.warranties = warranties;
        this.reviews = reviews;
        this.photos = photos;
    }

    public UserSummary user(User u) {
        return new UserSummary(u.getId(), u.getFullName(), u.getEmail(), u.getPhone(), u.getRole(),
                u.getAuthProvider(), u.getCreatedAt(), FileStorageService.url(u.getPhotoFile()));
    }

    public CategoryResponse category(ServiceCategory c) {
        return new CategoryResponse(c.getId(), c.getCode(), c.getName(), c.getDescription(), c.getSpecialistTitle(),
                c.getIcon(), c.getBaseVisitFee(), c.getTypicalCostMin(), c.getTypicalCostMax(), c.isEmergencySupported());
    }

    public CategoryRef categoryRef(ServiceCategory c) {
        return c == null ? null : new CategoryRef(c.getCode(), c.getName());
    }

    public PersonRef person(User u) {
        return u == null ? null : new PersonRef(u.getId(), u.getFullName(), u.getPhone(), FileStorageService.url(u.getPhotoFile()));
    }

    public TechnicianSummary technician(User providerUser, Double fromLat, Double fromLng) {
        if (providerUser == null) {
            return null;
        }
        return profiles.findByUser_Id(providerUser.getId())
                .map(p -> technician(p, fromLat, fromLng))
                .orElse(null);
    }

    public TechnicianSummary technician(ProviderProfile p, Double fromLat, Double fromLng) {
        User u = p.getUser();
        boolean onJob = requests.countByProvider_IdAndStatusIn(u.getId(), RequestStatus.ONGOING) > 0;
        String label = !p.isAvailable() ? "Offline" : onJob ? "On Active Job" : "Available Now";
        BigDecimal startingRate = offerings.findByProvider_IdAndActiveTrueOrderByBaseRateAsc(u.getId()).stream()
                .findFirst().map(ProviderServiceOffering::getBaseRate)
                .orElse(p.getCategory() != null ? p.getCategory().getBaseVisitFee() : null);
        return new TechnicianSummary(u.getId(), u.getFullName(), u.getPhone(), categoryRef(p.getCategory()),
                p.getHeadline(), p.getExperienceYears(), round1(p.getRating()), p.getReviewCount(), p.isProBadge(),
                p.isAvailable(), label, p.getServiceArea(),
                Geo.distanceKm(fromLat, fromLng, p.getLatitude(), p.getLongitude()), startingRate,
                FileStorageService.url(u.getPhotoFile()));
    }

    public OfferingResponse offering(ProviderServiceOffering o) {
        return new OfferingResponse(o.getId(), o.getTitle(), o.getDescription(), o.getBaseRate(), o.isActive());
    }

    public ReviewResponse review(Review r) {
        return new ReviewResponse(r.getId(), r.getRequest().getId(), titleOf(r.getRequest()),
                r.getCustomer().getId(), r.getCustomer().getFullName(),
                r.getProvider().getId(), r.getProvider().getFullName(), r.getRating(), r.getComment(), r.getCreatedAt());
    }

    public PhotoResponse photo(JobPhoto p) {
        return new PhotoResponse(p.getId(), p.getRequest().getId(), p.getType(), FileStorageService.url(p.getFileName()),
                p.getOriginalName(), p.getUploadedAt());
    }

    public NotificationResponse notification(Notification n) {
        return new NotificationResponse(n.getId(), n.getTitle(), n.getMessage(), n.getType(), n.getReferenceId(),
                n.isRead(), n.getCreatedAt());
    }

    public StatusEventResponse event(StatusEvent e) {
        return new StatusEventResponse(e.getStatus(), e.getStatus().getLabel(), e.getMessage(), e.getEtaMinutes(),
                e.getCreatedAt());
    }

    public AddressResponse address(Address a) {
        return new AddressResponse(a.getId(), a.getLabel(), a.getLine1(), a.getCity(), a.getState(), a.getPincode(),
                a.getLatitude(), a.getLongitude(), a.isPrimaryAddress(), a.fullText());
    }

    public ServiceRequestResponse request(ServiceRequest r) {
        return request(r, false);
    }

    public ServiceRequestResponse request(ServiceRequest r, boolean withPhotos) {
        Optional<Invoice> invoice = invoices.findByRequest_Id(r.getId());
        Optional<Warranty> warranty = warranties.findByRequest_Id(r.getId());
        Optional<Review> review = reviews.findByRequest_Id(r.getId());
        List<PhotoResponse> photoList = withPhotos
                ? photos.findByRequest_IdOrderByUploadedAtAsc(r.getId()).stream().map(this::photo).toList()
                : null;
        TechnicianSummary tech = technician(r.getProvider(), r.getLatitude(), r.getLongitude());
        boolean direct = r.getStatus() == RequestStatus.PENDING && r.getProvider() != null;
        return new ServiceRequestResponse(
                r.getId(), r.getTicketNo(), r.getBookingNo(), categoryRef(r.getCategory()), titleOf(r),
                r.getDescription(), r.getAddress(), r.getLatitude(), r.getLongitude(), r.getPreferredDate(),
                r.getTimeSlot(), r.getTimeSlot() != null ? r.getTimeSlot().getLabel() : null,
                r.isEmergency(), r.getSeverity(), r.getStatus(), r.getStatus().getLabel(), r.getEtaMinutes(),
                r.isWarrantyClaim(), direct, r.getCancelReason(), r.getCreatedAt(), r.getAcceptedAt(), r.getCompletedAt(),
                person(r.getCustomer()), tech, tech != null ? tech.distanceKm() : null,
                invoice.map(Invoice::getId).orElse(null), invoice.map(Invoice::getTotal).orElse(null),
                warranty.map(Warranty::getId).orElse(null), review.map(Review::getRating).orElse(null),
                photoList);
    }

    public InvoiceResponse invoice(Invoice i) {
        ServiceRequest r = i.getRequest();
        return new InvoiceResponse(i.getId(), i.getInvoiceNo(), r.getId(), r.getBookingNo(), titleOf(r),
                i.getProvider().getId(), i.getProvider().getFullName(), i.getCustomer().getId(),
                i.getCustomer().getFullName(), i.getLaborCost(), i.getPartsCost(), i.getPartsDescription(),
                i.getDiscount(), i.getTotal(), i.getStatus(), i.getPaymentMethod(), i.getAppliedCoupon(), i.getNotes(),
                i.getIssuedAt(), i.getPaidAt(), "/api/invoices/" + i.getId() + "/pdf");
    }

    public WarrantyResponse warranty(Warranty w) {
        WarrantyStatus status = w.effectiveStatus();
        long daysLeft = status == WarrantyStatus.ACTIVE
                ? Math.max(0, ChronoUnit.DAYS.between(LocalDate.now(), w.getValidUntil())) : 0;
        return new WarrantyResponse(w.getId(), w.getWarrantyNo(), w.getRequest().getId(), titleOf(w.getRequest()),
                w.getProvider().getFullName(), w.getCustomer().getFullName(), w.getValidFrom(), w.getValidUntil(),
                daysLeft, status, w.getClaimedAt(), w.getClaimNote(), w.getClaimRequestId());
    }

    public static String titleOf(ServiceRequest r) {
        if (r.getTitle() != null && !r.getTitle().isBlank()) {
            return r.getTitle();
        }
        return r.getCategory() != null ? r.getCategory().getName() : "Home Service";
    }

    public static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    public static Comparator<ServiceRequest> bySchedule() {
        return Comparator.comparing(ServiceRequest::getPreferredDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(r -> r.getTimeSlot() == null ? 99 : r.getTimeSlot().ordinal());
    }
}
