package com.fixconnect.repository;

import com.fixconnect.domain.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    /** Lookup by SHA-256 hash of the token. */
    Optional<PasswordResetToken> findByToken(String tokenHash);

    List<PasswordResetToken> findByUser_IdAndUsedFalse(Long userId);

    Optional<PasswordResetToken> findFirstByUser_IdOrderByCreatedAtDesc(Long userId);
}
