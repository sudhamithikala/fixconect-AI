package com.fixconnect.repository;

import com.fixconnect.domain.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    List<Invoice> findByCustomer_IdOrderByIssuedAtDesc(Long customerId);

    List<Invoice> findByProvider_IdOrderByIssuedAtDesc(Long providerId);

    Optional<Invoice> findByRequest_Id(Long requestId);

    boolean existsByRequest_Id(Long requestId);
}
