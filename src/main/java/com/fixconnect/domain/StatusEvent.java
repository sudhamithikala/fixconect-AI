package com.fixconnect.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Milestone in the ETA / Service Status timeline of a request. */
@Entity
@Table(name = "status_events")
public class StatusEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false)
    private ServiceRequest request;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RequestStatus status;

    @Column(length = 300)
    private String message;

    private Integer etaMinutes;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public StatusEvent(ServiceRequest request, RequestStatus status, String message, Integer etaMinutes) {
        this.request = request;
        this.status = status;
        this.message = message;
        this.etaMinutes = etaMinutes;
    }
    public StatusEvent() {
    }


    // ------------------------------------------------------------- getters / setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ServiceRequest getRequest() {
        return request;
    }

    public void setRequest(ServiceRequest request) {
        this.request = request;
    }

    public RequestStatus getStatus() {
        return status;
    }

    public void setStatus(RequestStatus status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Integer getEtaMinutes() {
        return etaMinutes;
    }

    public void setEtaMinutes(Integer etaMinutes) {
        this.etaMinutes = etaMinutes;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
