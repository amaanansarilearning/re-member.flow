package com.gymflow.repository;

import com.gymflow.domain.MembershipPlan;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlanRepository extends JpaRepository<MembershipPlan, Long> {
    long countByOrganizationId(Long organizationId);

    List<MembershipPlan> findByActiveTrueOrderByPriceAsc();

    Optional<MembershipPlan> findByIdAndOrganizationId(Long id, Long organizationId);

    List<MembershipPlan> findByOrganizationIdOrderByPriceAsc(Long organizationId);

    @Query("select p from MembershipPlan p where p.organization.id = :organizationId and p.active = true "
            + "and (p.branch is null or :branchId is null or p.branch.id = :branchId) order by p.price asc")
    List<MembershipPlan> findAvailableForOrganizationAndBranch(@Param("organizationId") Long organizationId,
            @Param("branchId") Long branchId);
}