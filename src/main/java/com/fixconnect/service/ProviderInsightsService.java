package com.fixconnect.service;

import com.fixconnect.domain.*;
import com.fixconnect.dto.CommonDtos.ReviewResponse;
import com.fixconnect.dto.ProviderDtos.*;
import com.fixconnect.dto.RequestDtos.ServiceRequestResponse;
import com.fixconnect.repository.InvoiceRepository;
import com.fixconnect.repository.ReviewRepository;
import com.fixconnect.repository.ServiceRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

/** Provider dashboard, customer directory, ratings and performance analytics. */
@Service
public class ProviderInsightsService {

    private final Lookup lookup;
    private final ServiceRequestRepository requests;
    private final ReviewRepository reviews;
    private final InvoiceRepository invoices;
    private final ProviderJobService jobs;
    private final NotificationService notifications;
    private final DtoMapper mapper;

    public ProviderInsightsService(Lookup lookup, ServiceRequestRepository requests, ReviewRepository reviews,
                                   InvoiceRepository invoices, ProviderJobService jobs,
                                   NotificationService notifications, DtoMapper mapper) {
        this.lookup = lookup;
        this.requests = requests;
        this.reviews = reviews;
        this.invoices = invoices;
        this.jobs = jobs;
        this.notifications = notifications;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public ProviderDashboardResponse dashboard(Long providerId) {
        ProviderProfile p = lookup.profileOf(providerId);
        List<ServiceRequest> incoming = jobs.incomingEntities(providerId);
        List<ServiceRequest> today = jobs.jobEntities(providerId, ProviderJobService.JobScope.TODAY);
        long ongoing = requests.countByProvider_IdAndStatusIn(providerId, RequestStatus.ONGOING);
        long completed = requests.countByProvider_IdAndStatus(providerId, RequestStatus.COMPLETED);
        long highPriority = incoming.stream()
                .filter(r -> r.isEmergency() || r.getSeverity() == Severity.HIGH || r.getSeverity() == Severity.CRITICAL)
                .count();

        ProviderStats stats = new ProviderStats(incoming.size(), highPriority, today.size(), ongoing, completed,
                ProviderAccountService.completionRate(p, completed), ProviderAccountService.acceptanceRate(p),
                DtoMapper.round1(p.getRating()), p.getReviewCount(), notifications.unreadCount(providerId));

        List<ServiceRequestResponse> incomingDto = incoming.stream().limit(3).map(mapper::request).toList();
        List<ServiceRequestResponse> todayDto = today.stream().map(mapper::request).toList();
        List<ReviewResponse> recent = reviews.findByProvider_IdOrderByCreatedAtDesc(providerId).stream()
                .limit(3).map(mapper::review).toList();
        return new ProviderDashboardResponse(p.getUser().getFullName(), p.getHeadline(), p.isAvailable(),
                p.isProBadge(), p.getServiceArea(), p.getServiceRadiusKm(), stats, incomingDto, todayDto, recent,
                notifications.recent(providerId, 3));
    }

    @Transactional(readOnly = true)
    public List<CustomerDirectoryItem> customers(Long providerId) {
        Map<Long, List<ServiceRequest>> byCustomer = requests.findByProvider_IdOrderByCreatedAtDesc(providerId).stream()
                .filter(r -> RequestStatus.ASSIGNED.contains(r.getStatus()))
                .collect(Collectors.groupingBy(r -> r.getCustomer().getId(), LinkedHashMap::new, Collectors.toList()));
        return byCustomer.values().stream().map(list -> {
            ServiceRequest latest = list.get(0);
            User c = latest.getCustomer();
            LocalDate last = list.stream()
                    .map(r -> r.getCompletedAt() != null ? r.getCompletedAt().toLocalDate() : r.getPreferredDate())
                    .filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
            return new CustomerDirectoryItem(c.getId(), c.getFullName(), c.getPhone(), latest.getAddress(),
                    list.size(), last);
        }).toList();
    }

    @Transactional(readOnly = true)
    public RatingsSummary ratings(Long providerId) {
        ProviderProfile p = lookup.profileOf(providerId);
        List<Review> list = reviews.findByProvider_IdOrderByCreatedAtDesc(providerId);
        Map<Integer, Long> dist = new TreeMap<>(Comparator.reverseOrder());
        for (int i = 1; i <= 5; i++) {
            dist.put(i, 0L);
        }
        list.forEach(r -> dist.merge(r.getRating(), 1L, Long::sum));
        return new RatingsSummary(DtoMapper.round1(p.getRating()), p.getReviewCount(), dist,
                list.stream().map(mapper::review).toList());
    }

    @Transactional(readOnly = true)
    public PerformanceResponse performance(Long providerId, int months) {
        ProviderProfile p = lookup.profileOf(providerId);
        long completed = requests.countByProvider_IdAndStatus(providerId, RequestStatus.COMPLETED);
        long cancelled = requests.countByProvider_IdAndStatus(providerId, RequestStatus.CANCELLED);
        List<Invoice> all = invoices.findByProvider_IdOrderByIssuedAtDesc(providerId);
        BigDecimal earned = sum(all.stream().filter(i -> i.getStatus() == InvoiceStatus.PAID).toList());
        BigDecimal pending = sum(all.stream().filter(i -> i.getStatus() == InvoiceStatus.UNPAID).toList());

        int span = Math.max(1, Math.min(months, 24));
        YearMonth now = YearMonth.now();
        Map<YearMonth, List<ServiceRequest>> completedByMonth = requests.findByProvider_IdOrderByCreatedAtDesc(providerId).stream()
                .filter(r -> r.getStatus() == RequestStatus.COMPLETED && r.getCompletedAt() != null)
                .collect(Collectors.groupingBy(r -> YearMonth.from(r.getCompletedAt())));
        Map<YearMonth, BigDecimal> earningsByMonth = all.stream()
                .filter(i -> i.getStatus() == InvoiceStatus.PAID && i.getPaidAt() != null)
                .collect(Collectors.groupingBy(i -> YearMonth.from(i.getPaidAt()),
                        Collectors.reducing(BigDecimal.ZERO, Invoice::getTotal, BigDecimal::add)));
        List<MonthlyStat> monthly = new ArrayList<>();
        for (int i = span - 1; i >= 0; i--) {
            YearMonth m = now.minusMonths(i);
            monthly.add(new MonthlyStat(m.toString(), completedByMonth.getOrDefault(m, List.of()).size(),
                    earningsByMonth.getOrDefault(m, BigDecimal.ZERO)));
        }
        return new PerformanceResponse(ProviderAccountService.acceptanceRate(p),
                ProviderAccountService.completionRate(p, completed), completed, cancelled, earned, pending,
                DtoMapper.round1(p.getRating()), p.getReviewCount(), monthly);
    }

    private static BigDecimal sum(List<Invoice> list) {
        return list.stream().map(Invoice::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
