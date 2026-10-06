package com.fixconnect.repository;

import com.fixconnect.domain.Warranty;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WarrantyRepository extends JpaRepository<Warranty, Long> {
    List<Warranty> findByCustomer_IdOrderByValidUntilDesc(Long customerId);

    List<Warranty> findByProvider_IdOrderByValidUntilDesc(Long providerId);

    Optional<Warranty> findByRequest_Id(Long requestId);

    Optional<Warranty> findByIdAndCustomer_Id(Long id, Long customerId);
}
