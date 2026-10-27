package com.gymflow.repository;

import com.gymflow.domain.Membership;
import com.gymflow.domain.MembershipStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;

public interface MembershipRepository extends JpaRepository<Membership, Long> {
        @Query("select count(m) as memberships, "
                        + "count(distinct case when m.renewedFrom is null then m.member.id else null end) as newMembers, "
                        + "count(case when m.renewedFrom is not null then m.id else null end) as renewals, "
                        + "count(distinct case when m.renewedFrom is not null then m.member.id else null end) as renewingMembers, "
                        + "coalesce(sum(m.amountPaid), 0) as revenue, "
                        + "coalesce(sum(case when m.renewedFrom is null then m.amountPaid else 0 end), 0) as newRevenue, "
                        + "coalesce(sum(case when m.renewedFrom is not null then m.amountPaid else 0 end), 0) as renewalRevenue, "
                        + "coalesce(sum(m.listPrice), 0) as listPriceValue, "
                        + "coalesce(sum(m.listPrice * m.discountPercent / 100), 0) as discountsGiven, "
                        + "coalesce(sum(m.amountPaid - m.listPrice * (1 - m.discountPercent / 100)), 0) as recordedPriceVariance, "
                        + "coalesce(avg(m.amountPaid), 0) as averageSale, "
                        + "coalesce(avg(case when m.renewedFrom is not null then m.amountPaid else null end), 0) as averageRenewalSale "
                        + "from Membership m where m.member.organization.id = :organizationId "
                        + "and m.createdAt >= :from and m.createdAt < :to "
                        + "and (:branchId is null or m.branch.id = :branchId)")
        MembershipSalesAggregate salesAggregate(@Param("organizationId") Long organizationId,
                        @Param("branchId") Long branchId, @Param("from") java.time.Instant from,
                        @Param("to") java.time.Instant to);

        @Query("select m.plan.id as packageId, coalesce(m.planNameSnapshot, m.plan.name) as packageName, "
                        + "count(m.id) as memberships, "
                        + "count(distinct case when m.renewedFrom is null then m.member.id else null end) as newMembers, "
                        + "count(case when m.renewedFrom is not null then m.id else null end) as renewals, "
                        + "coalesce(sum(m.amountPaid), 0) as revenue, coalesce(sum(m.listPrice), 0) as listPriceValue, "
                        + "coalesce(sum(m.listPrice * m.discountPercent / 100), 0) as discountsGiven, "
                        + "coalesce(sum(m.amountPaid - m.listPrice * (1 - m.discountPercent / 100)), 0) as recordedPriceVariance, "
                        + "coalesce(avg(m.amountPaid), 0) as averageSale, "
                        + "coalesce(avg(m.discountPercent), 0) as averageDiscountPercent "
                        + "from Membership m where m.member.organization.id = :organizationId "
                        + "and m.createdAt >= :from and m.createdAt < :to "
                        + "and (:branchId is null or m.branch.id = :branchId) "
                        + "group by m.plan.id, coalesce(m.planNameSnapshot, m.plan.name)")
        List<MembershipPackageAggregate> packagePerformance(@Param("organizationId") Long organizationId,
                        @Param("branchId") Long branchId, @Param("from") java.time.Instant from,
                        @Param("to") java.time.Instant to);

        @Query("select m.member.id as memberId, m.member.fullName as memberName, "
                        + "m.member.email as memberEmail, count(m.id) as terms, "
                        + "count(case when m.renewedFrom is not null then m.id else null end) as renewals, "
                        + "count(distinct case when m.renewedFrom is not null "
                        + "then function('year', m.startDate) else null end) as renewalYears, "
                        + "min(case when m.renewedFrom is not null then function('year', m.startDate) else null end) as firstRenewalYear, "
                        + "max(case when m.renewedFrom is not null then function('year', m.startDate) else null end) as lastRenewalYear, "
                        + "coalesce(sum(m.amountPaid), 0) as lifetimeRevenue, "
                        + "max(case when m.renewedFrom is not null and m.createdAt >= :from and m.createdAt < :to "
                        + "then m.createdAt else null end) as latestRenewalInPeriod "
                        + "from Membership m where m.member.organization.id = :organizationId "
                        + "and (:branchId is null or m.branch.id = :branchId) "
                        + "group by m.member.id, m.member.fullName, m.member.email "
                        + "having count(case when m.renewedFrom is not null and m.createdAt >= :from "
                        + "and m.createdAt < :to then m.id else null end) > 0 "
                        + "order by latestRenewalInPeriod desc")
        List<MemberRenewalAggregate> renewalMembers(@Param("organizationId") Long organizationId,
                        @Param("branchId") Long branchId, @Param("from") java.time.Instant from,
                        @Param("to") java.time.Instant to, Pageable pageable);

        @Query("select count(distinct m.member.id) from Membership m where m.member.organization.id = :organizationId "
                        + "and (:branchId is null or m.branch.id = :branchId) and m.startDate <= :asOf "
                        + "and m.createdAt < :asOfInstant and m.endDate >= :asOf "
                        + "and (m.status = :active or m.cancelledAt > :asOfInstant)")
        long countActiveMembersAsOf(@Param("organizationId") Long organizationId,
                        @Param("branchId") Long branchId, @Param("asOf") LocalDate asOf,
                        @Param("asOfInstant") java.time.Instant asOfInstant,
                        @Param("active") MembershipStatus active);

        @Query("select count(m.id) from Membership m where m.member.organization.id = :organizationId "
                        + "and (:branchId is null or m.branch.id = :branchId) and m.startDate <= :asOf "
                        + "and m.createdAt < :asOfInstant and m.endDate >= :asOf "
                        + "and (m.status = :active or m.cancelledAt > :asOfInstant)")
        long countActiveMembershipsAsOf(@Param("organizationId") Long organizationId,
                        @Param("branchId") Long branchId, @Param("asOf") LocalDate asOf,
                        @Param("asOfInstant") java.time.Instant asOfInstant,
                        @Param("active") MembershipStatus active);

        @Query("select count(m.id) from Membership m where m.member.organization.id = :organizationId "
                        + "and (:branchId is null or m.branch.id = :branchId) "
                        + "and ((m.cancelledAt >= :fromInstant and m.cancelledAt < :toInstant) "
                        + "or (m.endDate >= :from and m.endDate <= :to "
                        + "and (m.cancelledAt is null or m.cancelledAt >= :toInstant)))")
        long countEndedTerms(@Param("organizationId") Long organizationId, @Param("branchId") Long branchId,
                        @Param("fromInstant") java.time.Instant fromInstant,
                        @Param("toInstant") java.time.Instant toInstant, @Param("from") LocalDate from,
                        @Param("to") LocalDate to);

        @Query("select count(m.id) from Membership m where m.member.organization.id = :organizationId "
                        + "and (:branchId is null or m.branch.id = :branchId) "
                        + "and ((m.cancelledAt >= :fromInstant and m.cancelledAt < :toInstant) "
                        + "or (m.endDate >= :from and m.endDate <= :to "
                        + "and (m.cancelledAt is null or m.cancelledAt >= :toInstant))) "
                        + "and not exists (select later.id from Membership later where later.member.id = m.member.id "
                        + "and (later.createdAt > m.createdAt or (later.createdAt = m.createdAt and later.id > m.id)) "
                        + "and later.createdAt < :toInstant) "
                        + "and exists (select opening.id from Membership opening where opening.member.id = m.member.id "
                        + "and (:branchId is null or opening.branch.id = :branchId) "
                        + "and opening.createdAt < :fromInstant "
                        + "and opening.startDate <= :openingDate and opening.endDate >= :openingDate "
                        + "and (opening.status = :active or opening.cancelledAt > :fromInstant)) "
                        + "and not exists (select ongoing.id from Membership ongoing where ongoing.member.id = m.member.id "
                        + "and (:branchId is null or ongoing.branch.id = :branchId) "
                        + "and ongoing.startDate <= :to and ongoing.endDate >= :to "
                        + "and ongoing.createdAt < :toInstant "
                        + "and (ongoing.status = :active or ongoing.cancelledAt > :toInstant)) "
                        + "and not exists (select scheduled.id from Membership scheduled "
                        + "where scheduled.member.id = m.member.id and (:branchId is null or scheduled.branch.id = :branchId) "
                        + "and scheduled.createdAt < :toInstant and scheduled.startDate > :to "
                        + "and (scheduled.status = :active or scheduled.cancelledAt > :toInstant))")
        long countMembersLeft(@Param("organizationId") Long organizationId, @Param("branchId") Long branchId,
                        @Param("fromInstant") java.time.Instant fromInstant,
                        @Param("toInstant") java.time.Instant toInstant, @Param("from") LocalDate from,
                        @Param("to") LocalDate to, @Param("openingDate") LocalDate openingDate,
                        @Param("active") MembershipStatus active);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("select m from Membership m where m.id = :id")
        java.util.Optional<Membership> findByIdForUpdate(@Param("id") Long id);

        List<Membership> findByMemberOrganizationIdOrderByCreatedAtDesc(Long organizationId);

        List<Membership> findByBranchIdAndMemberOrganizationIdOrderByCreatedAtDesc(Long branchId, Long organizationId);

        @Query(value = "select m from Membership m join fetch m.member left join fetch m.branch "
                        + "where m.member.organization.id = :organizationId "
                        + "and (:branchId is null or m.branch.id = :branchId) "
                        + "order by m.createdAt desc, m.id desc",
                        countQuery = "select count(m) from Membership m "
                                        + "where m.member.organization.id = :organizationId "
                                        + "and (:branchId is null or m.branch.id = :branchId)")
        Page<Membership> findRecentForOrganization(@Param("organizationId") Long organizationId,
                        @Param("branchId") Long branchId, Pageable pageable);

        Optional<Membership> findByRenewedFromIdAndStartDateAndStatus(
                        Long renewedFromId, LocalDate startDate, MembershipStatus status);

        @Query("select m from Membership m join fetch m.member left join fetch m.branch "
                        + "where m.member.id in :memberIds "
                        + "and (:branchId is null or m.branch.id = :branchId) "
                        + "order by m.member.id asc, m.createdAt desc, m.id desc")
        List<Membership> findByMemberIdsForGroups(@Param("memberIds") List<Long> memberIds,
                        @Param("branchId") Long branchId);

        List<Membership> findByEndDateAndStatusAndRenewalStartDateIsNull(
                        LocalDate endDate, MembershipStatus status);

        Optional<Membership> findByIdAndMemberOrganizationId(Long id, Long organizationId);

        @Query("select m from Membership m where m.member.organization.id = :organizationId "
                        + "and m.createdAt >= :from and m.createdAt < :to "
                        + "and (:branchId is null or m.branch.id = :branchId) order by m.createdAt desc")
        List<Membership> findCreatedForReport(@Param("organizationId") Long organizationId,
                        @Param("from") java.time.Instant from, @Param("to") java.time.Instant to,
                        @Param("branchId") Long branchId);

        @Query("select m from Membership m where m.member.organization.id = :organizationId "
                        + "and m.cancelledAt >= :from and m.cancelledAt < :to "
                        + "and (:branchId is null or m.branch.id = :branchId) order by m.cancelledAt desc")
        List<Membership> findCancelledForReport(@Param("organizationId") Long organizationId,
                        @Param("from") java.time.Instant from, @Param("to") java.time.Instant to,
                        @Param("branchId") Long branchId);

        long countByBranchId(Long branchId);

        long countByPlanId(Long planId);

        @Query("select m.plan.id as itemId, count(m) as usageCount from Membership m "
                        + "where m.plan.id in :planIds group by m.plan.id")
        List<UsageCountProjection> countUsageByPlanIds(@Param("planIds") List<Long> planIds);

        @Query("select m.branch.id as itemId, count(m) as usageCount from Membership m "
                        + "where m.branch.id in :branchIds group by m.branch.id")
        List<UsageCountProjection> countUsageByBranchIds(@Param("branchIds") List<Long> branchIds);

        @Query("select count(distinct m.member.id) from Membership m "
                        + "where m.member.organization.id = :organizationId and m.branch.id = :branchId")
        long countDistinctMembersByBranch(@Param("organizationId") Long organizationId,
                        @Param("branchId") Long branchId);

        @Query("select count(distinct m.member.id) from Membership m where m.branch.id = :branchId "
                        + "and m.status = :status and m.startDate <= :today and m.endDate >= :today")
        long countActiveMembersByBranch(@Param("branchId") Long branchId,
                        @Param("status") MembershipStatus status, @Param("today") LocalDate today);

        @Query("select count(distinct m.member.id) from Membership m "
                        + "where m.member.organization.id = :organizationId and m.status = :status "
                        + "and m.startDate <= :today and m.endDate >= :today "
                        + "and (:branchId is null or m.branch.id = :branchId)")
        long countActiveMembersByOrganization(@Param("organizationId") Long organizationId,
                        @Param("branchId") Long branchId, @Param("status") MembershipStatus status,
                        @Param("today") LocalDate today);

        @Query("select m from Membership m where m.member.organization.id = :organizationId "
                        + "and (:branchId is null or m.branch.id = :branchId) "
                        + "and m.endDate between :start and :end and m.startDate <= :today "
                        + "and m.status = :status and m.renewalStartDate is null order by m.endDate asc")
        List<Membership> findExpiringWithoutRenewal(@Param("start") LocalDate start,
                        @Param("end") LocalDate end, @Param("today") LocalDate today,
                        @Param("status") MembershipStatus status,
                        @Param("organizationId") Long organizationId,
                        @Param("branchId") Long branchId);

        List<Membership> findByMemberEmailIgnoreCaseOrderByCreatedAtDesc(String email);

        boolean existsByMemberIdAndStatusAndEndDateGreaterThanEqualAndRenewalStartDateIsNull(
                        Long memberId, MembershipStatus status, LocalDate today);

        boolean existsByMemberId(Long memberId);

        @Query("select case when count(m) > 0 then true else false end from Membership m "
                        + "where m.member.id = :memberId and m.id <> :membershipId and m.status = :status "
                        + "and m.endDate >= :today and m.renewalStartDate is null")
        boolean hasOtherActiveOrScheduledMembership(@Param("memberId") Long memberId,
                        @Param("membershipId") Long membershipId,
                        @Param("status") MembershipStatus status,
                        @Param("today") LocalDate today);

        @Query("select case when count(m) > 0 then true else false end from Membership m "
                        + "where m.member.id = :memberId and m.id <> :membershipId and m.status = :status "
                        + "and m.startDate <= :endDate and m.endDate >= :startDate")
        boolean hasOverlappingActiveMembership(@Param("memberId") Long memberId,
                        @Param("membershipId") Long membershipId, @Param("status") MembershipStatus status,
                        @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

        long countByStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        MembershipStatus status, LocalDate startDate, LocalDate endDate);

        long countByStatus(MembershipStatus status);

        @Query("select coalesce(sum(m.amountPaid), 0) from Membership m where m.createdAt >= :from and m.createdAt < :to")
        BigDecimal revenueBetween(@Param("from") java.time.Instant from, @Param("to") java.time.Instant to);

        @Query("select coalesce(sum(m.amountPaid), 0) from Membership m "
                        + "where m.member.organization.id = :organizationId and m.createdAt >= :from and m.createdAt < :to "
                        + "and (:branchId is null or m.branch.id = :branchId)")
        BigDecimal revenueBetweenForOrganization(@Param("organizationId") Long organizationId,
                        @Param("branchId") Long branchId, @Param("from") java.time.Instant from,
                        @Param("to") java.time.Instant to);

        @Query("select m.branch.id as branchId, "
                        + "coalesce(sum(case when m.createdAt >= :currentFrom and m.createdAt < :currentTo "
                        + "then m.amountPaid else 0 end), 0) as revenueThisMonth, "
                        + "coalesce(sum(case when m.createdAt >= :previousFrom and m.createdAt < :previousTo "
                        + "then m.amountPaid else 0 end), 0) as revenueLastMonth, "
                        + "count(distinct case when m.status = :status and m.startDate <= :today "
                        + "and m.endDate >= :today then m.member.id else null end) as activeMembers, "
                        + "count(distinct m.member.id) as totalMembers "
                        + "from Membership m where m.member.organization.id = :organizationId "
                        + "and m.branch.id in :branchIds group by m.branch.id")
        List<BranchMembershipMetricsProjection> findBranchMetrics(
                        @Param("organizationId") Long organizationId, @Param("branchIds") List<Long> branchIds,
                        @Param("currentFrom") java.time.Instant currentFrom,
                        @Param("currentTo") java.time.Instant currentTo,
                        @Param("previousFrom") java.time.Instant previousFrom,
                        @Param("previousTo") java.time.Instant previousTo,
                        @Param("status") MembershipStatus status, @Param("today") LocalDate today);

        @Query("select count(distinct m.member.id) from Membership m where m.personalTrainer is not null and m.member.organization.id = :orgId and m.status = com.gymflow.domain.MembershipStatus.ACTIVE")
        long countActivePtTrainees(@Param("orgId") Long orgId);

        @Query("select coalesce(sum(case when m.plan.ptFeeIncluded is not null and m.plan.ptFeeIncluded > 0 then m.plan.ptFeeIncluded else m.amountPaid end), 0) from Membership m where m.personalTrainer is not null and m.member.organization.id = :orgId and m.status = com.gymflow.domain.MembershipStatus.ACTIVE")
        BigDecimal sumContractedPtRevenue(@Param("orgId") Long orgId);

        @Query("select coalesce(sum(m.ptSessionsIncluded), 0) from Membership m where m.personalTrainer is not null and m.member.organization.id = :orgId and m.status = com.gymflow.domain.MembershipStatus.ACTIVE")
        long sumAllocatedPtSessions(@Param("orgId") Long orgId);

        @Query("select coalesce(sum(m.ptSessionsUsed), 0) from Membership m where m.personalTrainer is not null and m.member.organization.id = :orgId and m.status = com.gymflow.domain.MembershipStatus.ACTIVE")
        long sumUsedPtSessions(@Param("orgId") Long orgId);

        List<Membership> findByPersonalTrainerIdOrderByCreatedAtDesc(Long personalTrainerId);

        List<Membership> findByPersonalTrainerIdAndStatusOrderByCreatedAtDesc(Long personalTrainerId, MembershipStatus status);

        @Query("select coalesce(sum(m.amountPaid), 0) from Membership m where m.member.organization.id = :orgId")
        BigDecimal sumAmountPaidByOrganizationId(@Param("orgId") Long orgId);

        @Query("select coalesce(sum(m.amountPaid), 0) from Membership m where m.member.organization.id = :orgId and m.createdAt >= :since")
        BigDecimal sumAmountPaidByOrganizationIdSince(@Param("orgId") Long orgId, @Param("since") java.time.Instant since);

        @Query("select coalesce(sum(m.listPrice * m.discountPercent / 100), 0) from Membership m where m.member.organization.id = :orgId")
        BigDecimal sumDiscountsByOrganizationId(@Param("orgId") Long orgId);

        @Query("select count(m) from Membership m where m.member.organization.id = :orgId and m.status = :status")
        long countByOrganizationIdAndStatus(@Param("orgId") Long orgId, @Param("status") MembershipStatus status);

        @Query("select coalesce(sum(m.amountPaid), 0) from Membership m")
        BigDecimal sumAllPlatformRevenue();

        @Query("select coalesce(sum(m.amountPaid), 0) from Membership m where m.branch.id = :branchId")
        BigDecimal sumAmountPaidByBranchId(@Param("branchId") Long branchId);

        @Query("select count(m) from Membership m where m.plan.id = :planId and m.status = com.gymflow.domain.MembershipStatus.ACTIVE")
        long countActiveByPlanId(@Param("planId") Long planId);
}