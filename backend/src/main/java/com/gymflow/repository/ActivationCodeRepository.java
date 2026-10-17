package com.gymflow.repository;

import com.gymflow.domain.ActivationCode;
import com.gymflow.domain.ActivationCodeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ActivationCodeRepository extends JpaRepository<ActivationCode, Long> {
    Optional<ActivationCode> findByCodeIgnoreCase(String code);

    List<ActivationCode> findAllByOrderByCreatedAtDesc();

    long countByStatus(ActivationCodeStatus status);

    boolean existsByCodeIgnoreCase(String code);
}

