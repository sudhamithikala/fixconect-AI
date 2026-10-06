package com.fixconnect.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;

/** Plumbing, Electrical, AC Repair, Appliance Repair, Cleaning, Carpentry, Painting ... */
@Entity
@Table(name = "service_categories", uniqueConstraints = @UniqueConstraint(columnNames = "code"))
public class ServiceCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(length = 500)
    private String description;

    /** Technician title used in the UI, e.g. "AC Technician". */
    @Column(length = 80)
    private String specialistTitle;

    @Column(length = 60)
    private String icon;

    private BigDecimal baseVisitFee;
    private BigDecimal typicalCostMin;
    private BigDecimal typicalCostMax;

    private boolean emergencySupported = true;

    public ServiceCategory(String code, String name, String description, String specialistTitle, String icon,
                           int baseVisitFee, int typicalCostMin, int typicalCostMax) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.specialistTitle = specialistTitle;
        this.icon = icon;
        this.baseVisitFee = BigDecimal.valueOf(baseVisitFee);
        this.typicalCostMin = BigDecimal.valueOf(typicalCostMin);
        this.typicalCostMax = BigDecimal.valueOf(typicalCostMax);
    }
    public ServiceCategory() {
    }


    // ------------------------------------------------------------- getters / setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSpecialistTitle() {
        return specialistTitle;
    }

    public void setSpecialistTitle(String specialistTitle) {
        this.specialistTitle = specialistTitle;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public BigDecimal getBaseVisitFee() {
        return baseVisitFee;
    }

    public void setBaseVisitFee(BigDecimal baseVisitFee) {
        this.baseVisitFee = baseVisitFee;
    }

    public BigDecimal getTypicalCostMin() {
        return typicalCostMin;
    }

    public void setTypicalCostMin(BigDecimal typicalCostMin) {
        this.typicalCostMin = typicalCostMin;
    }

    public BigDecimal getTypicalCostMax() {
        return typicalCostMax;
    }

    public void setTypicalCostMax(BigDecimal typicalCostMax) {
        this.typicalCostMax = typicalCostMax;
    }

    public boolean isEmergencySupported() {
        return emergencySupported;
    }

    public void setEmergencySupported(boolean emergencySupported) {
        this.emergencySupported = emergencySupported;
    }
}
