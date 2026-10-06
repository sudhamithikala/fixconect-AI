package com.fixconnect.repository;

import com.fixconnect.domain.ProviderProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProviderProfileRepository extends JpaRepository<ProviderProfile, Long> {
    Optional<ProviderProfile> findByUser_Id(Long userId);

    List<ProviderProfile> findByCategory_IdAndAvailableTrue(Long categoryId);

    List<ProviderProfile> findByAvailableTrue();
}
