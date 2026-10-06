package com.fixconnect.service;

import com.fixconnect.common.ApiException;
import com.fixconnect.domain.*;
import com.fixconnect.dto.AiDtos.DiagnosisResponse;
import com.fixconnect.dto.CommonDtos.PhotoResponse;
import com.fixconnect.dto.CommonDtos.ReviewResponse;
import com.fixconnect.dto.CommonDtos.TechnicianSummary;
import com.fixconnect.dto.RequestDtos.*;
import com.fixconnect.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Customer side of the request lifecycle: request, complaints, bookings, tracking, reviews, SOS. */
@Service
public class CustomerRequestService {

    public enum Scope { ALL, ACTIVE, PENDING, UPCOMING, ONGOING, COMPLETED, CANCELLED }

    private final ServiceRequestRepository requests;
    private final ProviderProfileRepository profiles;
    private final StatusEventRepository events;
    private final JobPhotoRepository photos;
    private final ReviewRepository reviews;
    private final InvoiceRepository invoices;
    private final Lookup lookup;
    private final DtoMapper mapper;
    private final CustomerService customers;
    private final NotificationService notifications;
    private final TimelineRecorder timeline;
    private final FileStorageService files;
    private final AiService ai;
    private final String hotline;
    private final int sosRadiusKm;

    public CustomerRequestService(ServiceRequestRepository requests, ProviderProfileRepository profiles,
                                  StatusEventRepository events, JobPhotoRepository photos, ReviewRepository reviews,
                                  InvoiceRepository invoices, Lookup lookup, DtoMapper mapper,
                                  CustomerService customers, NotificationService notifications,
                                  TimelineRecorder timeline, FileStorageService files, AiService ai,
                                  @Value("${fixconnect.emergency.hotline}") String hotline,
                                  @Value("${fixconnect.emergency.radius-km:5}") int sosRadiusKm) {
        this.requests = requests;
        this.profiles = profiles;
        this.events = events;
        this.photos = photos;
        this.reviews = reviews;
        this.invoices = invoices;
        this.lookup = lookup;
        this.mapper = mapper;
        this.customers = customers;
        this.notifications = notifications;
        this.timeline = timeline;
        this.files = files;
        this.ai = ai;
        this.hotline = hotline;
        this.sosRadiusKm = sosRadiusKm;
    }

    // ---------------------------------------------------------------- create

    @Transactional
    public ServiceRequestResponse create(Long customerId, CreateServiceRequest req) {
        User customer = lookup.user(customerId);
        ServiceCategory category = lookup.category(req.categoryCode());

        ServiceRequest r = new ServiceRequest();
        r.setCustomer(customer);
        r.setCategory(category);
        r.setTitle(blankToNull(req.title()));
        r.setDescription(req.description().trim());
        applyLocation(r, customerId, req.addressId(), req.address(), req.latitude(), req.longitude());
        r.setPreferredDate(req.preferredDate());
        r.setTimeSlot(req.timeSlot());
        r.setEmergency(req.emergency() || req.timeSlot() == TimeSlot.IMMEDIATE);

        DiagnosisResponse d = ai.diagnose(req.description(), category.getCode());
        r.setSeverity(r.isEmergency() ? Severity.CRITICAL : d.severity());
        if (r.getTitle() == null) {
            r.setTitle(category.getName());
        }

        if (req.technicianId() != null) {
            ProviderProfile p = lookup.technician(req.technicianId());
            r.setProvider(p.getUser()); // direct booking - waits for this technician to accept
        }
        requests.save(r);
        r.setTicketNo("CMP-" + (9000 + r.getId()));
        timeline.record(r, RequestStatus.PENDING, "Service request submitted");

        int notified = dispatch(r, false);
        notifications.notify(customer, "Request submitted",
                "Ticket #" + r.getTicketNo() + " for " + category.getName() + " was submitted. "
                        + (r.getProvider() != null ? "Waiting for " + r.getProvider().getFullName() + " to accept."
                        : notified + " nearby technician(s) notified."),
                "REQUEST", r.getId());
        return mapper.request(r, true);
    }

    @Transactional
    public PhotoResponse uploadProblemPhoto(Long customerId, Long requestId, MultipartFile file) {
        ServiceRequest r = lookup.customerRequest(requestId, customerId);
        JobPhoto p = new JobPhoto();
        p.setRequest(r);
        p.setType(PhotoType.PROBLEM);
        p.setFileName(files.store(file));
        p.setOriginalName(file.getOriginalFilename());
        p.setContentType(file.getContentType());
        p.setSizeBytes(file.getSize());
        p.setUploadedBy(r.getCustomer());
        photos.save(p);
        return mapper.photo(p);
    }

    // ---------------------------------------------------------------- read

    @Transactional(readOnly = true)
    public List<ServiceRequestResponse> list(Long customerId, Scope scope) {
        Set<RequestStatus> statuses = switch (scope == null ? Scope.ALL : scope) {
            case ALL -> EnumSet.allOf(RequestStatus.class);
            case ACTIVE -> RequestStatus.ACTIVE;
            case PENDING -> EnumSet.of(RequestStatus.PENDING);
            case UPCOMING -> EnumSet.of(RequestStatus.ACCEPTED);
            case ONGOING -> RequestStatus.ONGOING;
            case COMPLETED -> EnumSet.of(RequestStatus.COMPLETED);
            case CANCELLED -> EnumSet.of(RequestStatus.CANCELLED);
        };
        List<ServiceRequest> list = requests.findByCustomer_IdAndStatusInOrderByCreatedAtDesc(customerId, statuses);
        if (scope == Scope.UPCOMING) {
            list = list.stream().sorted(DtoMapper.bySchedule()).toList();
        }
        return list.stream().map(mapper::request).toList();
    }

    @Transactional(readOnly = true)
    public ServiceRequestResponse get(Long customerId, Long requestId) {
        return mapper.request(lookup.customerRequest(requestId, customerId), true);
    }

    @Transactional(readOnly = true)
    public TrackingResponse tracking(Long customerId, Long requestId) {
        ServiceRequest r = lookup.customerRequest(requestId, customerId);
        if (r.getProvider() == null || r.getStatus() == RequestStatus.PENDING) {
            throw ApiException.badRequest("No technician has accepted this request yet");
        }
        ProviderProfile p = lookup.profileOf(r.getProvider().getId());
        TechnicianSummary tech = mapper.technician(p, r.getLatitude(), r.getLongitude());
        Integer eta = r.getEtaMinutes();
        if (eta == null && r.getStatus() == RequestStatus.EN_ROUTE) {
            eta = Geo.etaMinutes(tech.distanceKm());
        }
        return new TrackingResponse(r.getId(), r.getBookingNo(), r.getStatus(), r.getStatus().getLabel(), eta, tech,
                p.getLatitude(), p.getLongitude(), p.getLocationUpdatedAt(), r.getAddress(), r.getLatitude(),
                r.getLongitude(), tech.distanceKm());
    }

    @Transactional(readOnly = true)
    public TimelineResponse timeline(Long customerId, Long requestId) {
        ServiceRequest r = lookup.customerRequest(requestId, customerId);
        return new TimelineResponse(r.getId(), r.getStatus(), r.getStatus().getLabel(), r.getEtaMinutes(),
                events.findByRequest_IdOrderByCreatedAtAscIdAsc(r.getId()).stream().map(mapper::event).toList());
    }

    @Transactional(readOnly = true)
    public List<PhotoResponse> photos(Long customerId, Long requestId) {
        lookup.customerRequest(requestId, customerId);
        return photos.findByRequest_IdOrderByUploadedAtAsc(requestId).stream().map(mapper::photo).toList();
    }

    @Transactional(readOnly = true)
    public List<PhotoResponse> allPhotos(Long customerId) {
        return photos.findByRequest_Customer_IdOrderByUploadedAtDesc(customerId).stream().map(mapper::photo).toList();
    }

    @Transactional(readOnly = true)
    public List<HistoryItem> history(Long customerId) {
        return requests.findByCustomer_IdAndStatusInOrderByCreatedAtDesc(customerId, EnumSet.of(RequestStatus.COMPLETED))
                .stream()
                .map(r -> new HistoryItem(r.getId(), r.getBookingNo(),
                        r.getCompletedAt() != null ? r.getCompletedAt().toLocalDate() : r.getPreferredDate(),
                        DtoMapper.titleOf(r), r.getProvider() != null ? r.getProvider().getFullName() : null,
                        invoices.findByRequest_Id(r.getId()).map(Invoice::getTotal).orElse(null),
                        reviews.findByRequest_Id(r.getId()).map(Review::getRating).orElse(null)))
                .toList();
    }

    // ---------------------------------------------------------------- change

    @Transactional
    public ServiceRequestResponse reschedule(Long customerId, Long requestId, RescheduleRequest req) {
        ServiceRequest r = lookup.customerRequest(requestId, customerId);
        if (r.getStatus() != RequestStatus.PENDING && r.getStatus() != RequestStatus.ACCEPTED) {
            throw ApiException.badRequest("Only pending or scheduled visits can be rescheduled");
        }
        r.setPreferredDate(req.preferredDate());
        r.setTimeSlot(req.timeSlot());
        timeline.record(r, r.getStatus(), "Visit rescheduled to " + req.preferredDate() + ", " + req.timeSlot().getLabel());
        if (r.getProvider() != null) {
            notifications.notify(r.getProvider(), "Booking rescheduled",
                    r.getCustomer().getFullName() + " moved " + ref(r) + " to " + req.preferredDate() + " ("
                            + req.timeSlot().getLabel() + ")", "BOOKING", r.getId());
        }
        return mapper.request(r);
    }

    @Transactional
    public ServiceRequestResponse cancel(Long customerId, Long requestId, CancelRequest req) {
        ServiceRequest r = lookup.customerRequest(requestId, customerId);
        if (!EnumSet.of(RequestStatus.PENDING, RequestStatus.ACCEPTED, RequestStatus.EN_ROUTE).contains(r.getStatus())) {
            throw ApiException.badRequest("This request can no longer be cancelled (status: " + r.getStatus().getLabel() + ")");
        }
        r.setStatus(RequestStatus.CANCELLED);
        r.setCancelledAt(LocalDateTime.now());
        r.setCancelReason(req == null ? null : req.reason());
        timeline.record(r, RequestStatus.CANCELLED, "Cancelled by customer" + (r.getCancelReason() != null ? ": " + r.getCancelReason() : ""));
        if (r.getProvider() != null) {
            notifications.notify(r.getProvider(), "Booking cancelled",
                    r.getCustomer().getFullName() + " cancelled " + ref(r), "BOOKING", r.getId());
        }
        return mapper.request(r);
    }

    @Transactional
    public ReviewResponse review(Long customerId, Long requestId, ReviewRequest req) {
        ServiceRequest r = lookup.customerRequest(requestId, customerId);
        if (r.getStatus() != RequestStatus.COMPLETED || r.getProvider() == null) {
            throw ApiException.badRequest("You can rate a technician only after the service is completed");
        }
        if (reviews.existsByRequest_Id(requestId)) {
            throw ApiException.conflict("You have already reviewed this service");
        }
        Review rv = new Review();
        rv.setRequest(r);
        rv.setCustomer(r.getCustomer());
        rv.setProvider(r.getProvider());
        rv.setRating(req.rating());
        rv.setComment(req.comment());
        reviews.save(rv);

        ProviderProfile p = lookup.profileOf(r.getProvider().getId());
        double total = p.getRating() * p.getReviewCount() + req.rating();
        p.setReviewCount(p.getReviewCount() + 1);
        p.setRating(total / p.getReviewCount());

        notifications.notify(r.getProvider(), "New " + req.rating() + "★ review",
                r.getCustomer().getFullName() + ": " + (req.comment() == null ? "(no comment)" : req.comment()),
                "REVIEW", r.getId());
        return mapper.review(rv);
    }

    // ---------------------------------------------------------------- SOS

    @Transactional
    public SosResponse sos(Long customerId, SosRequest req) {
        User customer = lookup.user(customerId);
        ServiceCategory category = lookup.category(req.type().getCategoryCode());

        ServiceRequest r = new ServiceRequest();
        r.setCustomer(customer);
        r.setCategory(category);
        r.setTitle("SOS: " + req.type().getLabel());
        r.setDescription(req.description() == null || req.description().isBlank()
                ? req.type().getLabel() + " - urgent assistance needed" : req.description().trim());
        applyLocation(r, customerId, req.addressId(), req.location(), req.latitude(), req.longitude());
        r.setPreferredDate(LocalDate.now());
        r.setTimeSlot(TimeSlot.IMMEDIATE);
        r.setEmergency(true);
        r.setSeverity(Severity.CRITICAL);
        requests.save(r);
        r.setTicketNo("CMP-" + (9000 + r.getId()));
        timeline.record(r, RequestStatus.PENDING, "Emergency SOS broadcast");

        int notified = dispatch(r, true);
        notifications.notify(customer, "🚨 Emergency SOS broadcast sent",
                notified + " technician(s) within " + sosRadiusKm + " km notified. Hotline team will call you within 60 seconds.",
                "SOS", r.getId());
        return new SosResponse(true, "EMERGENCY SOS BROADCAST SENT! Technicians within " + sosRadiusKm + "km notified.",
                notified, hotline, sosRadiusKm, mapper.request(r));
    }

    // ---------------------------------------------------------------- helpers

    /** Notifies matching technicians. Returns how many were notified. */
    private int dispatch(ServiceRequest r, boolean sos) {
        if (r.getProvider() != null) {
            notifications.notify(r.getProvider(), "New direct booking request",
                    r.getCustomer().getFullName() + " requested " + DtoMapper.titleOf(r) + " at " + r.getAddress(),
                    "REQUEST", r.getId());
            return 1;
        }
        List<ProviderProfile> candidates = profiles.findByCategory_IdAndAvailableTrue(r.getCategory().getId());
        if (candidates.isEmpty() && sos) {
            candidates = profiles.findByAvailableTrue(); // any available technician for an emergency
        }
        candidates = candidates.stream().filter(p -> p.getUser().isActive() && p.isApproved()).toList();
        int count = 0;
        for (ProviderProfile p : candidates) {
            Double d = Geo.distanceKm(r.getLatitude(), r.getLongitude(), p.getLatitude(), p.getLongitude());
            int radius = sos ? sosRadiusKm : p.getServiceRadiusKm();
            if (d != null && d > radius) {
                continue;
            }
            notifications.notify(p.getUser(),
                    sos ? "🚨 URGENT SOS request nearby" : (r.isEmergency() ? "Urgent service request" : "New service request"),
                    DtoMapper.titleOf(r) + " - " + r.getAddress() + (d != null ? " (" + d + " km away)" : ""),
                    sos ? "SOS" : "REQUEST", r.getId());
            count++;
        }
        return count;
    }

    private void applyLocation(ServiceRequest r, Long customerId, Long addressId, String text, Double lat, Double lng) {
        if (addressId != null) {
            Address a = customers.address(customerId, addressId);
            r.setAddress(a.fullText());
            r.setLatitude(a.getLatitude());
            r.setLongitude(a.getLongitude());
        } else if (text != null && !text.isBlank()) {
            r.setAddress(text.trim());
            r.setLatitude(lat);
            r.setLongitude(lng);
        } else {
            Address a = customers.primaryAddress(customerId)
                    .orElseThrow(() -> ApiException.badRequest("Provide addressId or address text"));
            r.setAddress(a.fullText());
            r.setLatitude(a.getLatitude());
            r.setLongitude(a.getLongitude());
        }
        if (lat != null && lng != null) {
            r.setLatitude(lat);
            r.setLongitude(lng);
        }
    }

    private static String ref(ServiceRequest r) {
        return "#" + (r.getBookingNo() != null ? r.getBookingNo() : r.getTicketNo());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
