package com.fixconnect.domain;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 30-day service warranty issued when a job is completed. */
@Entity
@Table(name = "warranties")
public class Warranty {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String warrantyNo;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false, unique = true)
    private ServiceRequest request;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false)
    private User provider;

    @Column(nullable = false)
    private LocalDate validFrom;

    @Column(nullable = false)
    private LocalDate validUntil;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private WarrantyStatus status = WarrantyStatus.ACTIVE;

    private LocalDateTime claimedAt;

    @Column(length = 500)
    private String claimNote;

    /** Follow-up service request created by a claim. */
    private Long claimRequestId;

    /** Status taking expiry into account. */
    public WarrantyStatus effectiveStatus() {
        if (status == WarrantyStatus.ACTIVE && validUntil.isBefore(LocalDate.now())) {
            return WarrantyStatus.EXPIRED;
        }
        return status;
    }
    public Warranty() {
    }


    // ------------------------------------------------------------- getters / setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getWarrantyNo() {
        return warrantyNo;
    }

    public void setWarrantyNo(String warrantyNo) {
        this.warrantyNo = warrantyNo;
    }

    public ServiceRequest getRequest() {
        return request;
    }

    public void setRequest(ServiceRequest request) {
        this.request = request;
    }

    public User getCustomer() {
        return customer;
    }

    public void setCustomer(User customer) {
        this.customer = customer;
    }

    public User getProvider() {
        return provider;
    }

    public void setProvider(User provider) {
        this.provider = provider;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(LocalDate validFrom) {
        this.validFrom = validFrom;
    }

    public LocalDate getValidUntil() {
        return validUntil;
    }

    public void setValidUntil(LocalDate validUntil) {
        this.validUntil = validUntil;
    }

    public WarrantyStatus getStatus() {
        return status;
    }

    public void setStatus(WarrantyStatus status) {
        this.status = status;
    }

    public LocalDateTime getClaimedAt() {
        return claimedAt;
    }

    public void setClaimedAt(LocalDateTime claimedAt) {
        this.claimedAt = claimedAt;
    }

    public String getClaimNote() {
        return claimNote;
    }

    public void setClaimNote(String claimNote) {
        this.claimNote = claimNote;
    }

    public Long getClaimRequestId() {
        return claimRequestId;
    }

    public void setClaimRequestId(Long claimRequestId) {
        this.claimRequestId = claimRequestId;
    }
}
