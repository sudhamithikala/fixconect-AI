package com.fixconnect.repository;

import com.fixconnect.domain.RewardVoucher;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RewardVoucherRepository extends JpaRepository<RewardVoucher, Long> {
    List<RewardVoucher> findByActiveTrueOrderByPointsCostAsc();
}
