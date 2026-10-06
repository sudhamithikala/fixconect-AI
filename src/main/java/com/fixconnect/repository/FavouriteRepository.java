package com.fixconnect.repository;

import com.fixconnect.domain.Favourite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FavouriteRepository extends JpaRepository<Favourite, Long> {
    List<Favourite> findByCustomer_IdOrderByCreatedAtDesc(Long customerId);

    Optional<Favourite> findByCustomer_IdAndProvider_Id(Long customerId, Long providerId);

    boolean existsByCustomer_IdAndProvider_Id(Long customerId, Long providerId);

    long countByCustomer_Id(Long customerId);
}
