package com.fixconnect.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Technician-specific details for users with role PROVIDER. */
@Entity
@Table(name = "provider_profiles")
public class ProviderProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private ServiceCategory category;

    /** Headline shown on profile, e.g. "AC & Home Appliance Specialist". */
    @Column(length = 120)
    private String headline;

    @Column(length = 500)
    private String skills;

    private int experienceYears;

    @Column(length = 200)
    private String serviceArea;

    private int serviceRadiusKm = 10;
    private int maxDailyJobs = 6;

    /** Online / offline availability toggle. */
    private boolean available = true;

    // Location (updated live while travelling to a job)
    private Double latitude;
    private Double longitude;
    private LocalDateTime locationUpdatedAt;

    // Verification & credentials
    @Column(length = 200)
    private String idProofFile;
    private boolean idVerified;
    private LocalDateTime idVerifiedAt;
    private boolean licenseVerified;
    private boolean backgroundCheckCleared;

    /** Admin who last changed the verification, and when. */
    private LocalDateTime verificationUpdatedAt;

    @Column(length = 300)
    private String verificationNote;

    // Ratings (denormalised for fast listing)
    private double rating;
    private int reviewCount;

    // Acceptance statistics
    private int acceptedCount;
    private int declinedCount;

    // Bank payout details
    @Column(length = 120)
    private String bankAccountHolder;
    @Column(length = 34)
    private String bankAccountNumber;
    @Column(length = 15)
    private String bankIfsc;
    @Column(length = 80)
    private String upiId;

    public LocalDateTime getVerificationUpdatedAt() {
        return verificationUpdatedAt;
    }

    public void setVerificationUpdatedAt(LocalDateTime verificationUpdatedAt) {
        this.verificationUpdatedAt = verificationUpdatedAt;
    }

    public String getVerificationNote() {
        return verificationNote;
    }

    public void setVerificationNote(String verificationNote) {
        this.verificationNote = verificationNote;
    }

    /** Approved by an admin: may go online, appear in search and receive jobs. */
    public boolean isApproved() {
        return isProBadge();
    }

    public boolean isProBadge() {
        return idVerified && licenseVerified && backgroundCheckCleared;
    }
    public ProviderProfile() {
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

    public ServiceCategory getCategory() {
        return category;
    }

    public void setCategory(ServiceCategory category) {
        this.category = category;
    }

    public String getHeadline() {
        return headline;
    }

    public void setHeadline(String headline) {
        this.headline = headline;
    }

    public String getSkills() {
        return skills;
    }

    public void setSkills(String skills) {
        this.skills = skills;
    }

    public int getExperienceYears() {
        return experienceYears;
    }

    public void setExperienceYears(int experienceYears) {
        this.experienceYears = experienceYears;
    }

    public String getServiceArea() {
        return serviceArea;
    }

    public void setServiceArea(String serviceArea) {
        this.serviceArea = serviceArea;
    }

    public int getServiceRadiusKm() {
        return serviceRadiusKm;
    }

    public void setServiceRadiusKm(int serviceRadiusKm) {
        this.serviceRadiusKm = serviceRadiusKm;
    }

    public int getMaxDailyJobs() {
        return maxDailyJobs;
    }

    public void setMaxDailyJobs(int maxDailyJobs) {
        this.maxDailyJobs = maxDailyJobs;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public LocalDateTime getLocationUpdatedAt() {
        return locationUpdatedAt;
    }

    public void setLocationUpdatedAt(LocalDateTime locationUpdatedAt) {
        this.locationUpdatedAt = locationUpdatedAt;
    }

    public String getIdProofFile() {
        return idProofFile;
    }

    public void setIdProofFile(String idProofFile) {
        this.idProofFile = idProofFile;
    }

    public boolean isIdVerified() {
        return idVerified;
    }

    public void setIdVerified(boolean idVerified) {
        this.idVerified = idVerified;
    }

    public LocalDateTime getIdVerifiedAt() {
        return idVerifiedAt;
    }

    public void setIdVerifiedAt(LocalDateTime idVerifiedAt) {
        this.idVerifiedAt = idVerifiedAt;
    }

    public boolean isLicenseVerified() {
        return licenseVerified;
    }

    public void setLicenseVerified(boolean licenseVerified) {
        this.licenseVerified = licenseVerified;
    }

    public boolean isBackgroundCheckCleared() {
        return backgroundCheckCleared;
    }

    public void setBackgroundCheckCleared(boolean backgroundCheckCleared) {
        this.backgroundCheckCleared = backgroundCheckCleared;
    }

    public double getRating() {
        return rating;
    }

    public void setRating(double rating) {
        this.rating = rating;
    }

    public int getReviewCount() {
        return reviewCount;
    }

    public void setReviewCount(int reviewCount) {
        this.reviewCount = reviewCount;
    }

    public int getAcceptedCount() {
        return acceptedCount;
    }

    public void setAcceptedCount(int acceptedCount) {
        this.acceptedCount = acceptedCount;
    }

    public int getDeclinedCount() {
        return declinedCount;
    }

    public void setDeclinedCount(int declinedCount) {
        this.declinedCount = declinedCount;
    }

    public String getBankAccountHolder() {
        return bankAccountHolder;
    }

    public void setBankAccountHolder(String bankAccountHolder) {
        this.bankAccountHolder = bankAccountHolder;
    }

    public String getBankAccountNumber() {
        return bankAccountNumber;
    }

    public void setBankAccountNumber(String bankAccountNumber) {
        this.bankAccountNumber = bankAccountNumber;
    }

    public String getBankIfsc() {
        return bankIfsc;
    }

    public void setBankIfsc(String bankIfsc) {
        this.bankIfsc = bankIfsc;
    }

    public String getUpiId() {
        return upiId;
    }

    public void setUpiId(String upiId) {
        this.upiId = upiId;
    }
}
