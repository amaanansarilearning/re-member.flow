package com.gymflow.repository;

import com.gymflow.domain.PtPayout;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PtPayoutRepository extends JpaRepository<PtPayout, Long> {
    List<PtPayout> findByPersonalTrainerIdOrderByCreatedAtDesc(Long ptId);
    List<PtPayout> findByPersonalTrainerOrganizationIdOrderByCreatedAtDesc(Long orgId);
    Optional<PtPayout> findByIdAndPersonalTrainerId(Long id, Long ptId);
    Optional<PtPayout> findByIdAndPersonalTrainerOrganizationId(Long id, Long orgId);
}
