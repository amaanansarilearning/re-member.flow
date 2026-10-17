package com.gymflow.repository;

import com.gymflow.domain.PasswordResetCode;
import java.time.Instant;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordResetCodeRepository extends JpaRepository<PasswordResetCode, Long> {
    Optional<PasswordResetCode> findFirstByEmailIgnoreCaseAndConsumedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
            String email, Instant now);

    boolean existsByEmailIgnoreCaseAndCreatedAtAfter(String email, Instant createdAt);

    List<PasswordResetCode> findAllByEmailIgnoreCaseAndConsumedAtIsNull(String email);
}