package com.fixconnect.service;

import com.fixconnect.common.ApiException;
import com.fixconnect.domain.*;
import com.fixconnect.dto.CustomerDtos.*;
import com.fixconnect.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Favourites, invoices & payments, warranty claims and loyalty rewards for customers. */
@Service
public class CustomerBenefitsService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final FavouriteRepository favourites;
    private final InvoiceRepository invoices;
    private final WarrantyRepository warranties;
    private final RewardVoucherRepository vouchers;
    private final VoucherRedemptionRepository redemptions;
    private final ServiceRequestRepository requests;
    private final Lookup lookup;
    private final DtoMapper mapper;
    private final NotificationService notifications;
    private final TimelineRecorder timeline;

    public CustomerBenefitsService(FavouriteRepository favourites, InvoiceRepository invoices,
                                   WarrantyRepository warranties, RewardVoucherRepository vouchers,
                                   VoucherRedemptionRepository redemptions, ServiceRequestRepository requests,
                                   Lookup lookup, DtoMapper mapper, NotificationService notifications,
                                   TimelineRecorder timeline) {
        this.favourites = favourites;
        this.invoices = invoices;
        this.warranties = warranties;
        this.vouchers = vouchers;
        this.redemptions = redemptions;
        this.requests = requests;
        this.lookup = lookup;
        this.mapper = mapper;
        this.notifications = notifications;
        this.timeline = timeline;
    }

    // ---------------------------------------------------------------- favourites

    @Transactional(readOnly = true)
    public List<FavouriteResponse> favourites(Long customerId) {
        return favourites.findByCustomer_IdOrderByCreatedAtDesc(customerId).stream()
                .map(f -> new FavouriteResponse(f.getId(), mapper.technician(f.getProvider(), null, null),
                        f.getNote(), f.getCreatedAt()))
                .toList();
    }

    @Transactional
    public FavouriteResponse addFavourite(Long customerId, Long technicianId, String note) {
        ProviderProfile p = lookup.technician(technicianId);
        Favourite f = favourites.findByCustomer_IdAndProvider_Id(customerId, technicianId).orElseGet(() -> {
            Favourite x = new Favourite();
            x.setCustomer(lookup.user(customerId));
            x.setProvider(p.getUser());
            return x;
        });
        if (note != null) {
            f.setNote(note);
        }
        favourites.save(f);
        return new FavouriteResponse(f.getId(), mapper.technician(p, null, null), f.getNote(), f.getCreatedAt());
    }

    @Transactional
    public void removeFavourite(Long customerId, Long technicianId) {
        Favourite f = favourites.findByCustomer_IdAndProvider_Id(customerId, technicianId)
                .orElseThrow(() -> ApiException.notFound("Favourite"));
        favourites.delete(f);
    }

    // ---------------------------------------------------------------- invoices

    @Transactional(readOnly = true)
    public List<InvoiceResponse> invoices(Long customerId) {
        return invoices.findByCustomer_IdOrderByIssuedAtDesc(customerId).stream().map(mapper::invoice).toList();
    }

    @Transactional
    public PaymentResponse pay(Long customerId, Long invoiceId, PayInvoiceRequest req) {
        Invoice inv = invoices.findById(invoiceId)
                .filter(i -> i.getCustomer().getId().equals(customerId))
                .orElseThrow(() -> ApiException.notFound("Invoice"));
        if (inv.getStatus() == InvoiceStatus.PAID) {
            throw ApiException.conflict("Invoice is already paid");
        }
        User customer = inv.getCustomer();

        if (req.couponCode() != null && !req.couponCode().isBlank()) {
            VoucherRedemption red = redemptions
                    .findByUser_IdAndCouponCodeIgnoreCaseAndUsedFalse(customerId, req.couponCode().trim())
                    .orElseThrow(() -> ApiException.badRequest("Coupon code is invalid or already used"));
            RewardVoucher v = red.getVoucher();
            if (v.getCategoryCode() != null && !v.getCategoryCode().equalsIgnoreCase(inv.getRequest().getCategory().getCode())) {
                throw ApiException.badRequest("This voucher is valid only for " + v.getCategoryCode() + " services");
            }
            inv.setDiscount(inv.getDiscount().add(v.getDiscountAmount()));
            inv.setAppliedCoupon(red.getCouponCode());
            inv.recalculate();
            red.setUsed(true);
            red.setUsedAt(LocalDateTime.now());
        }

        inv.setStatus(InvoiceStatus.PAID);
        inv.setPaymentMethod(req.method());
        inv.setPaidAt(LocalDateTime.now());

        int earned = Loyalty.pointsFor(inv.getTotal());
        customer.setLoyaltyPoints(customer.getLoyaltyPoints() + earned);
        customer.setLifetimePoints(customer.getLifetimePoints() + earned);

        notifications.notify(inv.getProvider(), "Payment received",
                "₹" + inv.getTotal() + " received for invoice #" + inv.getInvoiceNo(), "INVOICE", inv.getId());
        notifications.notify(customer, "Payment successful",
                "Invoice #" + inv.getInvoiceNo() + " paid. You earned " + earned + " loyalty points.", "INVOICE", inv.getId());
        return new PaymentResponse(true, "Payment successful", mapper.invoice(inv), earned, customer.getLoyaltyPoints());
    }

    // ---------------------------------------------------------------- warranty

    @Transactional(readOnly = true)
    public List<WarrantyResponse> warranties(Long customerId) {
        return warranties.findByCustomer_IdOrderByValidUntilDesc(customerId).stream().map(mapper::warranty).toList();
    }

    @Transactional
    public WarrantyClaimResponse claim(Long customerId, Long warrantyId, WarrantyClaimRequest req) {
        Warranty w = warranties.findByIdAndCustomer_Id(warrantyId, customerId)
                .orElseThrow(() -> ApiException.notFound("Warranty"));
        WarrantyStatus status = w.effectiveStatus();
        if (status != WarrantyStatus.ACTIVE) {
            throw ApiException.badRequest("Warranty is " + status + " and cannot be claimed");
        }
        ServiceRequest original = w.getRequest();

        ServiceRequest follow = new ServiceRequest();
        follow.setCustomer(original.getCustomer());
        follow.setProvider(original.getProvider()); // same technician fixes it free of charge
        follow.setCategory(original.getCategory());
        follow.setTitle("Warranty claim: " + DtoMapper.titleOf(original));
        follow.setDescription(req.issue().trim());
        follow.setAddress(original.getAddress());
        follow.setLatitude(original.getLatitude());
        follow.setLongitude(original.getLongitude());
        follow.setPreferredDate(req.preferredDate() != null ? req.preferredDate() : LocalDate.now().plusDays(1));
        follow.setTimeSlot(req.timeSlot() != null ? req.timeSlot() : TimeSlot.MORNING);
        follow.setWarrantyClaim(true);
        follow.setSeverity(Severity.MODERATE);
        requests.save(follow);
        follow.setTicketNo("CMP-" + (9000 + follow.getId()));
        timeline.record(follow, RequestStatus.PENDING, "Warranty claim raised for #" + w.getWarrantyNo());

        w.setStatus(WarrantyStatus.CLAIMED);
        w.setClaimedAt(LocalDateTime.now());
        w.setClaimNote(req.issue());
        w.setClaimRequestId(follow.getId());

        notifications.notify(w.getProvider(), "Warranty claim received",
                original.getCustomer().getFullName() + " raised a free repair claim on #" + w.getWarrantyNo() + ": " + req.issue(),
                "WARRANTY", follow.getId());
        return new WarrantyClaimResponse(true, "Warranty claim registered. Free repair visit requested.",
                mapper.warranty(w), mapper.request(follow));
    }

    // ---------------------------------------------------------------- rewards

    @Transactional(readOnly = true)
    public RewardsResponse rewards(Long customerId) {
        User u = lookup.user(customerId);
        List<VoucherResponse> list = vouchers.findByActiveTrueOrderByPointsCostAsc().stream()
                .map(v -> new VoucherResponse(v.getId(), v.getTitle(), v.getDescription(), v.getPointsCost(),
                        v.getDiscountAmount(), v.getCategoryCode(), u.getLoyaltyPoints() >= v.getPointsCost()))
                .toList();
        List<RedemptionResponse> reds = redemptions.findByUser_IdOrderByRedeemedAtDesc(customerId).stream()
                .map(this::redemption).toList();
        return new RewardsResponse(u.getLoyaltyPoints(), u.getLifetimePoints(), Loyalty.tier(u.getLifetimePoints()),
                Loyalty.EARNING_RULE, Loyalty.nextTier(u.getLifetimePoints()), Loyalty.pointsToNext(u.getLifetimePoints()),
                list, reds);
    }

    @Transactional
    public RedeemResponse redeem(Long customerId, RedeemRequest req) {
        User u = lookup.user(customerId);
        RewardVoucher v = vouchers.findById(req.voucherId()).filter(RewardVoucher::isActive)
                .orElseThrow(() -> ApiException.notFound("Voucher"));
        if (u.getLoyaltyPoints() < v.getPointsCost()) {
            throw ApiException.badRequest("Not enough points: you have " + u.getLoyaltyPoints() + ", need " + v.getPointsCost());
        }
        u.setLoyaltyPoints(u.getLoyaltyPoints() - v.getPointsCost());
        VoucherRedemption r = new VoucherRedemption();
        r.setUser(u);
        r.setVoucher(v);
        r.setCouponCode(couponCode(v.getDiscountAmount()));
        redemptions.save(r);
        notifications.notify(u, "Voucher redeemed", "Voucher Code " + r.getCouponCode() + " redeemed! " + v.getTitle(),
                "REWARD", r.getId());
        return new RedeemResponse(true, "Voucher Code " + r.getCouponCode() + " redeemed!", redemption(r),
                u.getLoyaltyPoints());
    }

    private RedemptionResponse redemption(VoucherRedemption r) {
        return new RedemptionResponse(r.getId(), r.getCouponCode(), r.getVoucher().getTitle(),
                r.getVoucher().getDiscountAmount(), r.isUsed(), r.getRedeemedAt());
    }

    private static String couponCode(BigDecimal amount) {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder("FC-R").append(amount.intValue()).append('-');
        for (int i = 0; i < 5; i++) {
            sb.append(chars.charAt(RANDOM.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
