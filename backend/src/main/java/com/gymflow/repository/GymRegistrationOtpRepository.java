package com.gymflow.repository;

import com.gymflow.domain.GymRegistrationOtp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GymRegistrationOtpRepository extends JpaRepository<GymRegistrationOtp, Long> {
    Optional<GymRegistrationOtp> findFirstByEmailIgnoreCaseAndConsumedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
            String email, Instant now);

    boolean existsByEmailIgnoreCaseAndCreatedAtAfter(String email, Instant createdAt);

    List<GymRegistrationOtp> findAllByEmailIgnoreCaseAndConsumedAtIsNull(String email);
}

