package com.fixconnect.repository;

import com.fixconnect.domain.RequestStatus;
import com.fixconnect.domain.ServiceRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ServiceRequestRepository extends JpaRepository<ServiceRequest, Long> {

    List<ServiceRequest> findByCustomer_IdOrderByCreatedAtDesc(Long customerId);

    List<ServiceRequest> findByCustomer_IdAndStatusInOrderByCreatedAtDesc(Long customerId, Collection<RequestStatus> statuses);

    List<ServiceRequest> findByProvider_IdOrderByCreatedAtDesc(Long providerId);

    List<ServiceRequest> findByProvider_IdAndStatusInOrderByPreferredDateAscCreatedAtAsc(Long providerId, Collection<RequestStatus> statuses);

    /** Open requests in the shared pool for a category (no technician chosen yet). */
    List<ServiceRequest> findByStatusAndProviderIsNullAndCategory_IdOrderByEmergencyDescCreatedAtAsc(RequestStatus status, Long categoryId);

    /** Requests sent directly to one technician that still await acceptance. */
    List<ServiceRequest> findByStatusAndProvider_IdOrderByEmergencyDescCreatedAtAsc(RequestStatus status, Long providerId);

    Optional<ServiceRequest> findByIdAndCustomer_Id(Long id, Long customerId);

    long countByCustomer_IdAndStatusIn(Long customerId, Collection<RequestStatus> statuses);

    long countByProvider_IdAndStatus(Long providerId, RequestStatus status);

    long countByProvider_IdAndStatusIn(Long providerId, Collection<RequestStatus> statuses);
}
