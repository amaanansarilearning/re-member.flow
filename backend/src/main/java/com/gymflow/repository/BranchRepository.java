package com.gymflow.repository;

import com.gymflow.domain.GymBranch;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BranchRepository extends JpaRepository<GymBranch, Long> {
    List<GymBranch> findByOrganizationIdOrderByNameAsc(Long organizationId);

    List<GymBranch> findByOrganizationIdAndActiveTrueOrderByNameAsc(Long organizationId);

    Optional<GymBranch> findByOrganizationIdAndCodeIgnoreCase(Long organizationId, String code);

    Optional<GymBranch> findByIdAndOrganizationId(Long id, Long organizationId);

    Optional<GymBranch> findByIdAndOrganizationIdAndActiveTrue(Long id, Long organizationId);

    boolean existsByOrganizationIdAndCodeIgnoreCase(Long organizationId, String code);

    boolean existsByOrganizationIdAndCodeIgnoreCaseAndIdNot(Long organizationId, String code, Long id);

    long countByOrganizationId(Long organizationId);
}