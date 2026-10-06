package com.fixconnect.service;

import com.fixconnect.common.ApiException;
import com.fixconnect.domain.Address;
import com.fixconnect.domain.RequestStatus;
import com.fixconnect.domain.ServiceRequest;
import com.fixconnect.domain.User;
import com.fixconnect.dto.CommonDtos.TechnicianSummary;
import com.fixconnect.dto.CustomerDtos.*;
import com.fixconnect.dto.RequestDtos.ServiceRequestResponse;
import com.fixconnect.repository.AddressRepository;
import com.fixconnect.repository.FavouriteRepository;
import com.fixconnect.repository.ServiceRequestRepository;
import com.fixconnect.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

@Service
public class CustomerService {

    private final Lookup lookup;
    private final UserRepository users;
    private final AddressRepository addresses;
    private final ServiceRequestRepository requests;
    private final FavouriteRepository favourites;
    private final DtoMapper mapper;
    private final NotificationService notifications;
    private final TechnicianService technicians;

    public CustomerService(Lookup lookup, UserRepository users, AddressRepository addresses,
                           ServiceRequestRepository requests, FavouriteRepository favourites, DtoMapper mapper,
                           NotificationService notifications, TechnicianService technicians) {
        this.lookup = lookup;
        this.users = users;
        this.addresses = addresses;
        this.requests = requests;
        this.favourites = favourites;
        this.mapper = mapper;
        this.notifications = notifications;
        this.technicians = technicians;
    }

    // ---------------------------------------------------------------- profile

    @Transactional(readOnly = true)
    public CustomerProfileResponse profile(Long userId) {
        User u = lookup.user(userId);
        String primary = addresses.findFirstByUser_IdAndPrimaryAddressTrue(userId).map(Address::fullText).orElse(null);
        long done = requests.countByCustomer_IdAndStatusIn(userId, EnumSet.of(RequestStatus.COMPLETED));
        long active = requests.countByCustomer_IdAndStatusIn(userId, RequestStatus.ACTIVE);
        return new CustomerProfileResponse(u.getId(), u.getFullName(), u.getEmail(), u.getPhone(), u.getAltPhone(),
                primary, u.getCreatedAt(), done, active, addresses.countByUser_Id(userId), u.getLoyaltyPoints(),
                Loyalty.tier(u.getLifetimePoints()));
    }

    @Transactional
    public CustomerProfileResponse updateProfile(Long userId, UpdateCustomerProfileRequest req) {
        User u = lookup.user(userId);
        String email = req.email().trim().toLowerCase();
        if (!email.equalsIgnoreCase(u.getEmail()) && users.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("Email is already used by another account");
        }
        u.setFullName(req.fullName().trim());
        u.setPhone(req.phone());
        u.setEmail(email);
        u.setAltPhone(req.altPhone() == null || req.altPhone().isBlank() ? null : req.altPhone());

        Address primary = addresses.findFirstByUser_IdAndPrimaryAddressTrue(userId).orElseGet(() -> {
            Address a = new Address();
            a.setUser(u);
            a.setLabel("Home");
            a.setPrimaryAddress(true);
            return a;
        });
        if (!req.primaryAddress().trim().equals(primary.fullText())) {
            primary.setLine1(req.primaryAddress().trim());
            primary.setCity(null);
            primary.setState(null);
            primary.setPincode(null);
        }
        addresses.save(primary);
        return profile(userId);
    }

    // ---------------------------------------------------------------- addresses

    @Transactional(readOnly = true)
    public List<AddressResponse> addresses(Long userId) {
        return addresses.findByUser_IdOrderByPrimaryAddressDescIdAsc(userId).stream().map(mapper::address).toList();
    }

    @Transactional
    public AddressResponse addAddress(Long userId, AddressRequest req) {
        Address a = new Address();
        a.setUser(lookup.user(userId));
        apply(a, req);
        boolean first = addresses.countByUser_Id(userId) == 0;
        addresses.save(a);
        if (first || Boolean.TRUE.equals(req.primary())) {
            makePrimary(userId, a);
        }
        return mapper.address(a);
    }

    @Transactional
    public AddressResponse updateAddress(Long userId, Long id, AddressRequest req) {
        Address a = address(userId, id);
        apply(a, req);
        if (Boolean.TRUE.equals(req.primary())) {
            makePrimary(userId, a);
        }
        return mapper.address(a);
    }

    @Transactional
    public AddressResponse setPrimary(Long userId, Long id) {
        Address a = address(userId, id);
        makePrimary(userId, a);
        return mapper.address(a);
    }

    @Transactional
    public void deleteAddress(Long userId, Long id) {
        Address a = address(userId, id);
        if (a.isPrimaryAddress() && addresses.countByUser_Id(userId) > 1) {
            throw ApiException.badRequest("Set another address as primary before deleting the primary address");
        }
        addresses.delete(a);
    }

    public Address address(Long userId, Long id) {
        return addresses.findByIdAndUser_Id(id, userId).orElseThrow(() -> ApiException.notFound("Address"));
    }

    public Optional<Address> primaryAddress(Long userId) {
        return addresses.findFirstByUser_IdAndPrimaryAddressTrue(userId);
    }

    private void makePrimary(Long userId, Address target) {
        addresses.findByUser_IdOrderByPrimaryAddressDescIdAsc(userId)
                .forEach(x -> x.setPrimaryAddress(x.getId().equals(target.getId())));
        target.setPrimaryAddress(true);
    }

    private static void apply(Address a, AddressRequest req) {
        a.setLabel(req.label() == null || req.label().isBlank() ? "Other" : req.label().trim());
        a.setLine1(req.line1().trim());
        a.setCity(req.city());
        a.setState(req.state());
        a.setPincode(req.pincode());
        a.setLatitude(req.latitude());
        a.setLongitude(req.longitude());
    }

    // ---------------------------------------------------------------- settings

    @Transactional(readOnly = true)
    public NotificationSettings settings(Long userId) {
        User u = lookup.user(userId);
        return new NotificationSettings(u.isSmsAlerts(), u.isWhatsappUpdates(), u.isEmailNotifications());
    }

    @Transactional
    public NotificationSettings updateSettings(Long userId, NotificationSettings req) {
        User u = lookup.user(userId);
        if (req.smsAlerts() != null) u.setSmsAlerts(req.smsAlerts());
        if (req.whatsappUpdates() != null) u.setWhatsappUpdates(req.whatsappUpdates());
        if (req.emailNotifications() != null) u.setEmailNotifications(req.emailNotifications());
        return new NotificationSettings(u.isSmsAlerts(), u.isWhatsappUpdates(), u.isEmailNotifications());
    }

    // ---------------------------------------------------------------- dashboard

    @Transactional(readOnly = true)
    public CustomerDashboardResponse dashboard(Long userId) {
        User u = lookup.user(userId);
        List<ServiceRequest> mine = requests.findByCustomer_IdOrderByCreatedAtDesc(userId);

        long active = mine.stream().filter(r -> r.getStatus() != RequestStatus.PENDING
                && RequestStatus.ACTIVE.contains(r.getStatus())).count();
        long pending = mine.stream().filter(r -> r.getStatus() == RequestStatus.PENDING).count();
        long done = mine.stream().filter(r -> r.getStatus() == RequestStatus.COMPLETED).count();

        ServiceRequestResponse ongoing = mine.stream()
                .filter(r -> RequestStatus.ONGOING.contains(r.getStatus()))
                .findFirst().map(mapper::request).orElse(null);

        List<ServiceRequestResponse> upcoming = mine.stream()
                .filter(r -> RequestStatus.ACTIVE.contains(r.getStatus()))
                .sorted(DtoMapper.bySchedule())
                .limit(5).map(mapper::request).toList();

        List<TechnicianSummary> top = technicians.top(3, userId);

        CustomerStats stats = new CustomerStats(active, pending, done, favourites.countByCustomer_Id(userId),
                u.getLoyaltyPoints(), notifications.unreadCount(userId));
        return new CustomerDashboardResponse(u.getFullName(), stats, ongoing, upcoming, top,
                notifications.recent(userId, 3));
    }
}
