package com.fixconnect.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;

/** Redeemable loyalty voucher, e.g. "Rs.200 OFF on your next electrical service" for 200 points. */
@Entity
@Table(name = "reward_vouchers")
public class RewardVoucher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(length = 300)
    private String description;

    private int pointsCost;

    private BigDecimal discountAmount;

    /** Restrict voucher to a category (null = any service). */
    @Column(length = 40)
    private String categoryCode;

    private boolean active = true;
    public RewardVoucher() {
    }


    // ------------------------------------------------------------- getters / setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getPointsCost() {
        return pointsCost;
    }

    public void setPointsCost(int pointsCost) {
        this.pointsCost = pointsCost;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public void setDiscountAmount(BigDecimal discountAmount) {
        this.discountAmount = discountAmount;
    }

    public String getCategoryCode() {
        return categoryCode;
    }

    public void setCategoryCode(String categoryCode) {
        this.categoryCode = categoryCode;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
