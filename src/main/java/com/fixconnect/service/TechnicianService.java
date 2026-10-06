package com.fixconnect.service;

import com.fixconnect.domain.Address;
import com.fixconnect.domain.ProviderProfile;
import com.fixconnect.domain.RequestStatus;
import com.fixconnect.dto.CommonDtos.*;
import com.fixconnect.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Public technician directory ("Find & Book Technicians", "Top Rated Technicians"). */
@Service
public class TechnicianService {

    public enum SortBy { RATING, DISTANCE, EXPERIENCE, REVIEWS }

    private final ProviderProfileRepository profiles;
    private final AddressRepository addresses;
    private final ProviderServiceOfferingRepository offerings;
    private final ReviewRepository reviews;
    private final ServiceRequestRepository requests;
    private final FavouriteRepository favourites;
    private final Lookup lookup;
    private final DtoMapper mapper;

    public TechnicianService(ProviderProfileRepository profiles, AddressRepository addresses,
                             ProviderServiceOfferingRepository offerings, ReviewRepository reviews,
                             ServiceRequestRepository requests, FavouriteRepository favourites, Lookup lookup,
                             DtoMapper mapper) {
        this.profiles = profiles;
        this.addresses = addresses;
        this.offerings = offerings;
        this.reviews = reviews;
        this.requests = requests;
        this.favourites = favourites;
        this.lookup = lookup;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<TechnicianSummary> search(String categoryCode, Boolean availableOnly, Double minRating,
                                          Boolean verifiedOnly, String q, SortBy sort, Double lat, Double lng,
                                          Long viewerId) {
        double[] origin = origin(lat, lng, viewerId);
        Long categoryId = categoryCode == null || categoryCode.isBlank() ? null : lookup.category(categoryCode).getId();
        String query = q == null ? null : q.trim().toLowerCase();

        List<TechnicianSummary> list = profiles.findAll().stream()
                .filter(p -> p.getUser().isActive() && p.isApproved())
                .filter(p -> categoryId == null || (p.getCategory() != null && p.getCategory().getId().equals(categoryId)))
                .filter(p -> !Boolean.TRUE.equals(availableOnly) || p.isAvailable())
                .filter(p -> !Boolean.TRUE.equals(verifiedOnly) || p.isProBadge())
                .filter(p -> minRating == null || p.getRating() >= minRating)
                .filter(p -> query == null || query.isEmpty() || matches(p, query))
                .map(p -> mapper.technician(p, origin[0] == 999 ? null : origin[0], origin[1] == 999 ? null : origin[1]))
                .toList();

        Comparator<TechnicianSummary> cmp = switch (sort == null ? SortBy.RATING : sort) {
            case DISTANCE -> Comparator.comparing(TechnicianSummary::distanceKm, Comparator.nullsLast(Comparator.naturalOrder()));
            case EXPERIENCE -> Comparator.comparingInt(TechnicianSummary::experienceYears).reversed();
            case REVIEWS -> Comparator.comparingInt(TechnicianSummary::reviewCount).reversed();
            case RATING -> Comparator.comparingDouble(TechnicianSummary::rating).reversed()
                    .thenComparing(Comparator.comparingInt(TechnicianSummary::reviewCount).reversed());
        };
        return list.stream().sorted(cmp).toList();
    }

    @Transactional(readOnly = true)
    public List<TechnicianSummary> top(int limit, Long viewerId) {
        return search(null, null, null, true, null, SortBy.RATING, null, null, viewerId)
                .stream().limit(limit).toList();
    }

    @Transactional(readOnly = true)
    public TechnicianDetail detail(Long technicianId, Long viewerId) {
        ProviderProfile p = lookup.technician(technicianId);
        double[] origin = origin(null, null, viewerId);
        List<OfferingResponse> services = offerings.findByProvider_IdAndActiveTrueOrderByBaseRateAsc(technicianId)
                .stream().map(mapper::offering).toList();
        List<ReviewResponse> recent = reviews.findByProvider_IdOrderByCreatedAtDesc(technicianId).stream()
                .limit(5).map(mapper::review).toList();
        boolean fav = viewerId != null && favourites.existsByCustomer_IdAndProvider_Id(viewerId, technicianId);
        long completed = requests.countByProvider_IdAndStatus(technicianId, RequestStatus.COMPLETED);
        return new TechnicianDetail(
                mapper.technician(p, origin[0] == 999 ? null : origin[0], origin[1] == 999 ? null : origin[1]),
                p.getSkills(), p.getServiceRadiusKm(), completed, fav, services, recent);
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> reviews(Long technicianId) {
        lookup.technician(technicianId);
        return reviews.findByProvider_IdOrderByCreatedAtDesc(technicianId).stream().map(mapper::review).toList();
    }

    /** Explicit coordinates win; otherwise the viewer's primary address. 999 = unknown. */
    private double[] origin(Double lat, Double lng, Long viewerId) {
        if (lat != null && lng != null) {
            return new double[]{lat, lng};
        }
        if (viewerId != null) {
            Optional<Address> a = addresses.findFirstByUser_IdAndPrimaryAddressTrue(viewerId);
            if (a.isPresent() && a.get().getLatitude() != null && a.get().getLongitude() != null) {
                return new double[]{a.get().getLatitude(), a.get().getLongitude()};
            }
        }
        return new double[]{999, 999};
    }

    private static boolean matches(ProviderProfile p, String q) {
        return contains(p.getUser().getFullName(), q) || contains(p.getSkills(), q) || contains(p.getHeadline(), q)
                || contains(p.getServiceArea(), q) || (p.getCategory() != null && contains(p.getCategory().getName(), q));
    }

    private static boolean contains(String s, String q) {
        return s != null && s.toLowerCase().contains(q);
    }
}
