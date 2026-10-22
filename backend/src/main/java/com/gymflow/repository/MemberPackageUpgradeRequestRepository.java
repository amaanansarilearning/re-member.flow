package com.gymflow.repository;

import com.gymflow.domain.MemberPackageUpgradeRequest;
import com.gymflow.domain.MemberUpgradeRequestStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MemberPackageUpgradeRequestRepository extends JpaRepository<MemberPackageUpgradeRequest, Long> {

    @Query("SELECT r FROM MemberPackageUpgradeRequest r LEFT JOIN FETCH r.member LEFT JOIN FETCH r.newPlan LEFT JOIN FETCH r.branch LEFT JOIN FETCH r.membership WHERE r.organization.id = :organizationId ORDER BY r.createdAt DESC")
    List<MemberPackageUpgradeRequest> findByOrganizationIdOrderByCreatedAtDesc(@Param("organizationId") Long organizationId);

    @Query("SELECT r FROM MemberPackageUpgradeRequest r LEFT JOIN FETCH r.member LEFT JOIN FETCH r.newPlan LEFT JOIN FETCH r.branch LEFT JOIN FETCH r.membership WHERE r.organization.id = :organizationId AND (r.branch.id = :branchId OR r.branch IS NULL) ORDER BY r.createdAt DESC")
    List<MemberPackageUpgradeRequest> findByBranchOrUnassigned(@Param("organizationId") Long organizationId, @Param("branchId") Long branchId);

    @Query("SELECT r FROM MemberPackageUpgradeRequest r LEFT JOIN FETCH r.member LEFT JOIN FETCH r.newPlan LEFT JOIN FETCH r.branch LEFT JOIN FETCH r.membership WHERE r.branch.id = :branchId ORDER BY r.createdAt DESC")
    List<MemberPackageUpgradeRequest> findByBranchIdOrderByCreatedAtDesc(@Param("branchId") Long branchId);

    @Query("SELECT r FROM MemberPackageUpgradeRequest r LEFT JOIN FETCH r.member LEFT JOIN FETCH r.newPlan LEFT JOIN FETCH r.branch LEFT JOIN FETCH r.membership WHERE r.member.id = :memberId ORDER BY r.createdAt DESC")
    List<MemberPackageUpgradeRequest> findByMemberIdOrderByCreatedAtDesc(@Param("memberId") Long memberId);

    boolean existsByMembershipIdAndStatus(Long membershipId, MemberUpgradeRequestStatus status);

    Optional<MemberPackageUpgradeRequest> findByIdAndOrganizationId(Long id, Long organizationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM MemberPackageUpgradeRequest r WHERE r.id = :id AND r.organization.id = :organizationId")
    Optional<MemberPackageUpgradeRequest> findByIdAndOrganizationIdForUpdate(@Param("id") Long id, @Param("organizationId") Long organizationId);
}

