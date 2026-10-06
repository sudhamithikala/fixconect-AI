package com.fixconnect.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "voucher_redemptions")
public class VoucherRedemption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "voucher_id", nullable = false)
    private RewardVoucher voucher;

    @Column(nullable = false, unique = true, length = 30)
    private String couponCode;

    private boolean used;

    @Column(nullable = false)
    private LocalDateTime redeemedAt = LocalDateTime.now();

    private LocalDateTime usedAt;
    public VoucherRedemption() {
    }


    // ------------------------------------------------------------- getters / setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public RewardVoucher getVoucher() {
        return voucher;
    }

    public void setVoucher(RewardVoucher voucher) {
        this.voucher = voucher;
    }

    public String getCouponCode() {
        return couponCode;
    }

    public void setCouponCode(String couponCode) {
        this.couponCode = couponCode;
    }

    public boolean isUsed() {
        return used;
    }

    public void setUsed(boolean used) {
        this.used = used;
    }

    public LocalDateTime getRedeemedAt() {
        return redeemedAt;
    }

    public void setRedeemedAt(LocalDateTime redeemedAt) {
        this.redeemedAt = redeemedAt;
    }

    public LocalDateTime getUsedAt() {
        return usedAt;
    }

    public void setUsedAt(LocalDateTime usedAt) {
        this.usedAt = usedAt;
    }
}
