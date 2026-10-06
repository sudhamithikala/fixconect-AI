package com.fixconnect.repository;

import com.fixconnect.domain.ProviderServiceOffering;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProviderServiceOfferingRepository extends JpaRepository<ProviderServiceOffering, Long> {
    List<ProviderServiceOffering> findByProvider_IdOrderByIdAsc(Long providerId);

    List<ProviderServiceOffering> findByProvider_IdAndActiveTrueOrderByBaseRateAsc(Long providerId);

    Optional<ProviderServiceOffering> findByIdAndProvider_Id(Long id, Long providerId);
}
