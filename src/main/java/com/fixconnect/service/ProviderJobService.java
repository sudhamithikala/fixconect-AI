package com.fixconnect.service;

import com.fixconnect.common.ApiException;
import com.fixconnect.domain.*;
import com.fixconnect.dto.CommonDtos.PhotoResponse;
import com.fixconnect.dto.CommonDtos.StatusEventResponse;
import com.fixconnect.dto.CustomerDtos.InvoiceResponse;
import com.fixconnect.dto.CustomerDtos.WarrantyResponse;
import com.fixconnect.dto.ProviderDtos.*;
import com.fixconnect.dto.RequestDtos.ServiceRequestResponse;
import com.fixconnect.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Stream;

/** Provider side of the request lifecycle: incoming requests, accept/decline, job progress, photos, invoices. */
@Service
public class ProviderJobService {

    public enum JobScope { TODAY, UPCOMING, ONGOING, COMPLETED, CANCELLED, ALL }

    private static final Set<RequestStatus> OPEN_ASSIGNED =
            EnumSet.of(RequestStatus.ACCEPTED, RequestStatus.EN_ROUTE, RequestStatus.ARRIVED, RequestStatus.IN_PROGRESS);

    private final ServiceRequestRepository requests;
    private final StatusEventRepository events;
    private final JobPhotoRepository photos;
    private final InvoiceRepository invoices;
    private final WarrantyRepository warranties;
    private final Lookup lookup;
    private final DtoMapper mapper;
    private final NotificationService notifications;
    private final TimelineRecorder timeline;
    private final FileStorageService files;

    public ProviderJobService(ServiceRequestRepository requests, StatusEventRepository events, JobPhotoRepository photos,
                              InvoiceRepository invoices, WarrantyRepository warranties, Lookup lookup,
                              DtoMapper mapper, NotificationService notifications, TimelineRecorder timeline,
                              FileStorageService files) {
        this.requests = requests;
        this.events = events;
        this.photos = photos;
        this.invoices = invoices;
        this.warranties = warranties;
        this.lookup = lookup;
        this.mapper = mapper;
        this.notifications = notifications;
        this.timeline = timeline;
        this.files = files;
    }

    // ---------------------------------------------------------------- incoming requests

    @Transactional(readOnly = true)
    public List<ServiceRequestResponse> incoming(Long providerId) {
        return incomingEntities(providerId).stream().map(mapper::request).toList();
    }

    public List<ServiceRequest> incomingEntities(Long providerId) {
        ProviderProfile p = lookup.profileOf(providerId);
        if (!p.isApproved()) {
            return List.of(); // jobs are offered only after admin verification
        }
        List<ServiceRequest> direct = requests.findByStatusAndProvider_IdOrderByEmergencyDescCreatedAtAsc(RequestStatus.PENDING, providerId);
        List<ServiceRequest> pool = p.getCategory() == null ? List.of()
                : requests.findByStatusAndProviderIsNullAndCategory_IdOrderByEmergencyDescCreatedAtAsc(RequestStatus.PENDING, p.getCategory().getId())
                .stream()
                .filter(r -> !r.getDeclinedProviderIds().contains(providerId))
                .filter(r -> {
                    Double d = Geo.distanceKm(p.getLatitude(), p.getLongitude(), r.getLatitude(), r.getLongitude());
                    return d == null || d <= p.getServiceRadiusKm() || r.isEmergency();
                })
                .toList();
        return Stream.concat(direct.stream(), pool.stream())
                .sorted(Comparator.comparing(ServiceRequest::isEmergency).reversed()
                        .thenComparing(ServiceRequest::getCreatedAt))
                .toList();
    }

    @Transactional
    public ServiceRequestResponse accept(Long providerId, Long requestId) {
        ProviderProfile p = lookup.profileOf(providerId);
        if (!p.isApproved()) {
            throw ApiException.forbidden("Your profile is awaiting admin verification.");
        }
        ServiceRequest r = lookup.request(requestId);
        if (r.getStatus() != RequestStatus.PENDING) {
            throw ApiException.conflict("Request is no longer open (status: " + r.getStatus().getLabel() + ")");
        }
        if (r.getProvider() != null && !r.getProvider().getId().equals(providerId)) {
            throw ApiException.conflict("Request was sent to another technician");
        }
        if (r.getProvider() == null && !r.isEmergency()
                && (p.getCategory() == null || !p.getCategory().getId().equals(r.getCategory().getId()))) {
            throw ApiException.forbidden("Request category does not match your specialization");
        }
        if (r.getDeclinedProviderIds().contains(providerId) && r.getProvider() == null) {
            throw ApiException.badRequest("You already declined this request");
        }
        long sameDay = requests.findByProvider_IdAndStatusInOrderByPreferredDateAscCreatedAtAsc(providerId, OPEN_ASSIGNED)
                .stream().filter(x -> Objects.equals(x.getPreferredDate(), r.getPreferredDate())).count();
        if (sameDay >= p.getMaxDailyJobs()) {
            throw ApiException.badRequest("Max daily jobs limit (" + p.getMaxDailyJobs() + ") reached for " + r.getPreferredDate());
        }

        r.setProvider(p.getUser());
        r.setStatus(RequestStatus.ACCEPTED);
        r.setAcceptedAt(LocalDateTime.now());
        r.setBookingNo("BK-" + (7700 + r.getId()));
        r.setEtaMinutes(Geo.etaMinutes(Geo.distanceKm(p.getLatitude(), p.getLongitude(), r.getLatitude(), r.getLongitude())));
        p.setAcceptedCount(p.getAcceptedCount() + 1);
        timeline.record(r, RequestStatus.ACCEPTED, "Request accepted by " + p.getUser().getFullName());

        notifications.notify(r.getCustomer(), "Technician Assigned",
                p.getUser().getFullName() + " accepted your " + DtoMapper.titleOf(r) + " request. Booking #" + r.getBookingNo(),
                "BOOKING", r.getId());
        return mapper.request(r, true);
    }

    @Transactional
    public ServiceRequestResponse decline(Long providerId, Long requestId, DeclineRequest req) {
        ProviderProfile p = lookup.profileOf(providerId);
        ServiceRequest r = lookup.request(requestId);
        if (r.getStatus() != RequestStatus.PENDING) {
            throw ApiException.conflict("Request is no longer open");
        }
        boolean direct = r.getProvider() != null && r.getProvider().getId().equals(providerId);
        if (r.getProvider() != null && !direct) {
            throw ApiException.notFound("Request");
        }
        r.getDeclinedProviderIds().add(providerId);
        p.setDeclinedCount(p.getDeclinedCount() + 1);
        if (direct) {
            r.setProvider(null); // release to the shared pool for other technicians
            timeline.record(r, RequestStatus.PENDING, "Selected technician unavailable - finding another technician");
            notifications.notify(r.getCustomer(), "Finding another technician",
                    p.getUser().getFullName() + " is unavailable" + (req != null && req.reason() != null ? " (" + req.reason() + ")" : "")
                            + ". We are matching you with another verified technician.", "REQUEST", r.getId());
        }
        return mapper.request(r);
    }

    // ---------------------------------------------------------------- jobs

    @Transactional(readOnly = true)
    public List<ServiceRequestResponse> jobs(Long providerId, JobScope scope) {
        return jobEntities(providerId, scope).stream().map(mapper::request).toList();
    }

    public List<ServiceRequest> jobEntities(Long providerId, JobScope scope) {
        LocalDate today = LocalDate.now();
        List<ServiceRequest> all = requests.findByProvider_IdAndStatusInOrderByPreferredDateAscCreatedAtAsc(providerId,
                EnumSet.of(RequestStatus.ACCEPTED, RequestStatus.EN_ROUTE, RequestStatus.ARRIVED,
                        RequestStatus.IN_PROGRESS, RequestStatus.COMPLETED, RequestStatus.CANCELLED));
        Stream<ServiceRequest> s = all.stream();
        s = switch (scope == null ? JobScope.ALL : scope) {
            case TODAY -> s.filter(r -> today.equals(r.getPreferredDate()) && r.getStatus() != RequestStatus.CANCELLED);
            case UPCOMING -> s.filter(r -> r.getStatus() == RequestStatus.ACCEPTED
                    && r.getPreferredDate() != null && r.getPreferredDate().isAfter(today));
            case ONGOING -> s.filter(r -> RequestStatus.ONGOING.contains(r.getStatus()));
            case COMPLETED -> s.filter(r -> r.getStatus() == RequestStatus.COMPLETED)
                    .sorted(Comparator.comparing(ServiceRequest::getCompletedAt, Comparator.nullsLast(Comparator.reverseOrder())));
            case CANCELLED -> s.filter(r -> r.getStatus() == RequestStatus.CANCELLED);
            case ALL -> s;
        };
        List<ServiceRequest> list = s.toList();
        if (scope == JobScope.TODAY || scope == JobScope.UPCOMING) {
            list = list.stream().sorted(DtoMapper.bySchedule()).toList();
        }
        return list;
    }

    @Transactional(readOnly = true)
    public ServiceRequestResponse job(Long providerId, Long jobId) {
        return mapper.request(lookup.providerJob(jobId, providerId), true);
    }

    @Transactional(readOnly = true)
    public List<StatusEventResponse> jobTimeline(Long providerId, Long jobId) {
        lookup.providerJob(jobId, providerId);
        return events.findByRequest_IdOrderByCreatedAtAscIdAsc(jobId).stream().map(mapper::event).toList();
    }

    @Transactional
    public ServiceRequestResponse updateStatus(Long providerId, Long jobId, JobStatusUpdateRequest req) {
        ServiceRequest r = lookup.providerJob(jobId, providerId);
        RequestStatus next = req.status();
        if (!EnumSet.of(RequestStatus.EN_ROUTE, RequestStatus.ARRIVED, RequestStatus.IN_PROGRESS, RequestStatus.COMPLETED).contains(next)) {
            throw ApiException.badRequest("Status must be one of EN_ROUTE, ARRIVED, IN_PROGRESS, COMPLETED");
        }
        if (!OPEN_ASSIGNED.contains(r.getStatus())) {
            throw ApiException.badRequest("Job is " + r.getStatus().getLabel() + " and cannot be updated");
        }
        if (next.ordinal() <= r.getStatus().ordinal()) {
            throw ApiException.badRequest("Cannot move job from " + r.getStatus() + " back to " + next);
        }
        r.setStatus(next);
        if (req.etaMinutes() != null) {
            r.setEtaMinutes(req.etaMinutes());
        }
        String msg = req.note();
        switch (next) {
            case EN_ROUTE -> {
                if (msg == null) msg = "Technician is on the way";
            }
            case ARRIVED -> {
                r.setEtaMinutes(0);
                if (msg == null) msg = "Arrived at location!";
            }
            case IN_PROGRESS -> {
                r.setEtaMinutes(0);
                r.setStartedAt(LocalDateTime.now());
                if (msg == null) msg = "Started repair work.";
            }
            case COMPLETED -> {
                r.setEtaMinutes(null);
                r.setCompletedAt(LocalDateTime.now());
                if (msg == null) msg = "Job completed successfully";
                issueWarranty(r);
            }
            default -> {
            }
        }
        timeline.record(r, next, msg);
        String body = r.getProvider().getFullName() + ": " + msg
                + (next == RequestStatus.EN_ROUTE && r.getEtaMinutes() != null ? " (ETA " + r.getEtaMinutes() + " mins)" : "")
                + (next == RequestStatus.COMPLETED ? " Your 30-day warranty is now active." : "");
        notifications.notify(r.getCustomer(), next.getLabel(), body, "BOOKING", r.getId());
        return mapper.request(r);
    }

    @Transactional
    public ServiceRequestResponse updateEta(Long providerId, Long jobId, EtaUpdateRequest req) {
        ServiceRequest r = lookup.providerJob(jobId, providerId);
        if (!OPEN_ASSIGNED.contains(r.getStatus())) {
            throw ApiException.badRequest("ETA can only be updated for active jobs");
        }
        r.setEtaMinutes(req.etaMinutes());
        String msg = req.note() != null ? req.note() : "Updated ETA: " + req.etaMinutes() + " mins";
        timeline.record(r, r.getStatus(), msg);
        notifications.notify(r.getCustomer(), "ETA updated",
                r.getProvider().getFullName() + " - ETA " + req.etaMinutes() + " mins", "BOOKING", r.getId());
        return mapper.request(r);
    }

    private void issueWarranty(ServiceRequest r) {
        if (warranties.findByRequest_Id(r.getId()).isPresent()) {
            return;
        }
        Warranty w = new Warranty();
        w.setWarrantyNo("WR-" + (4000 + r.getId()));
        w.setRequest(r);
        w.setCustomer(r.getCustomer());
        w.setProvider(r.getProvider());
        w.setValidFrom(LocalDate.now());
        w.setValidUntil(LocalDate.now().plusDays(30));
        warranties.save(w);
    }

    // ---------------------------------------------------------------- photos

    @Transactional
    public PhotoResponse uploadPhoto(Long providerId, Long jobId, PhotoType type, MultipartFile file) {
        if (type == PhotoType.PROBLEM) {
            throw ApiException.badRequest("Technicians upload BEFORE or AFTER photos");
        }
        ServiceRequest r = lookup.providerJob(jobId, providerId);
        if (r.getStatus() == RequestStatus.CANCELLED || r.getStatus() == RequestStatus.PENDING) {
            throw ApiException.badRequest("Photos can be added only to accepted jobs");
        }
        JobPhoto p = new JobPhoto();
        p.setRequest(r);
        p.setType(type);
        p.setFileName(files.store(file));
        p.setOriginalName(file.getOriginalFilename());
        p.setContentType(file.getContentType());
        p.setSizeBytes(file.getSize());
        p.setUploadedBy(r.getProvider());
        photos.save(p);
        if (type == PhotoType.AFTER) {
            notifications.notify(r.getCustomer(), "After-service photo uploaded",
                    "Review the proof-of-work photos for " + DtoMapper.titleOf(r), "BOOKING", r.getId());
        }
        return mapper.photo(p);
    }

    @Transactional(readOnly = true)
    public List<PhotoResponse> photos(Long providerId, Long jobId) {
        lookup.providerJob(jobId, providerId);
        return photos.findByRequest_IdOrderByUploadedAtAsc(jobId).stream().map(mapper::photo).toList();
    }

    @Transactional(readOnly = true)
    public List<PhotoResponse> allPhotos(Long providerId) {
        return photos.findByRequest_Provider_IdOrderByUploadedAtDesc(providerId).stream().map(mapper::photo).toList();
    }

    // ---------------------------------------------------------------- invoices & warranties

    @Transactional
    public InvoiceResponse createInvoice(Long providerId, Long jobId, CreateInvoiceRequest req) {
        ServiceRequest r = lookup.providerJob(jobId, providerId);
        if (r.getStatus() != RequestStatus.IN_PROGRESS && r.getStatus() != RequestStatus.COMPLETED) {
            throw ApiException.badRequest("Invoice can be generated once work has started");
        }
        if (invoices.existsByRequest_Id(jobId)) {
            throw ApiException.conflict("Invoice already generated for this job");
        }
        BigDecimal labour = r.isWarrantyClaim() ? BigDecimal.ZERO : req.laborCost();
        BigDecimal parts = req.partsCost() != null ? req.partsCost() : BigDecimal.ZERO;
        if (req.discount() != null && req.discount().compareTo(labour.add(parts)) > 0) {
            throw ApiException.badRequest("Discount cannot be more than labour + parts (₹" + labour.add(parts) + ")");
        }
        Invoice inv = new Invoice();
        inv.setInvoiceNo("INV-" + LocalDate.now().getYear() + "-" + String.format("%04d", r.getId()));
        inv.setRequest(r);
        inv.setCustomer(r.getCustomer());
        inv.setProvider(r.getProvider());
        inv.setLaborCost(r.isWarrantyClaim() ? BigDecimal.ZERO : req.laborCost());
        inv.setPartsCost(req.partsCost() != null ? req.partsCost() : BigDecimal.ZERO);
        inv.setPartsDescription(req.partsDescription());
        inv.setDiscount(req.discount() != null ? req.discount() : BigDecimal.ZERO);
        inv.setNotes(r.isWarrantyClaim() ? "Warranty repair - labour free of charge. " + (req.notes() == null ? "" : req.notes()) : req.notes());
        inv.recalculate();
        invoices.save(inv);
        notifications.notify(r.getCustomer(), "Invoice generated",
                "Invoice #" + inv.getInvoiceNo() + " for " + DtoMapper.titleOf(r) + ": ₹" + inv.getTotal(), "INVOICE", inv.getId());
        return mapper.invoice(inv);
    }

    @Transactional(readOnly = true)
    public List<InvoiceResponse> invoices(Long providerId) {
        return invoices.findByProvider_IdOrderByIssuedAtDesc(providerId).stream().map(mapper::invoice).toList();
    }

    @Transactional(readOnly = true)
    public List<WarrantyResponse> warranties(Long providerId) {
        return warranties.findByProvider_IdOrderByValidUntilDesc(providerId).stream().map(mapper::warranty).toList();
    }
}
