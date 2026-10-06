package com.fixconnect.repository;

import com.fixconnect.domain.VoucherRedemption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VoucherRedemptionRepository extends JpaRepository<VoucherRedemption, Long> {
    List<VoucherRedemption> findByUser_IdOrderByRedeemedAtDesc(Long userId);

    Optional<VoucherRedemption> findByUser_IdAndCouponCodeIgnoreCaseAndUsedFalse(Long userId, String couponCode);
}
