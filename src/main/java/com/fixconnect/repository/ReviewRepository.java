package com.fixconnect.repository;

import com.fixconnect.domain.Review;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    List<Review> findByProvider_IdOrderByCreatedAtDesc(Long providerId);

    Optional<Review> findByRequest_Id(Long requestId);

    boolean existsByRequest_Id(Long requestId);
}
