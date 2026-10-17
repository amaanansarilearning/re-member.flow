package com.gymflow.repository;

import com.gymflow.domain.UpgradeRequest;
import com.gymflow.domain.UpgradeRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface UpgradeRequestRepository extends JpaRepository<UpgradeRequest, Long> {
    List<UpgradeRequest> findByStatusOrderByCreatedAtDesc(UpgradeRequestStatus status);

    List<UpgradeRequest> findAllByOrderByCreatedAtDesc();

    List<UpgradeRequest> findByOrganizationIdOrderByCreatedAtDesc(Long organizationId);

    Optional<UpgradeRequest> findTopByOrganizationIdAndStatusOrderByCreatedAtDesc(Long organizationId, UpgradeRequestStatus status);

    long countByStatus(UpgradeRequestStatus status);
}

