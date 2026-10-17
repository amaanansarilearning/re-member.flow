package com.gymflow.repository;

import com.gymflow.domain.PtEarning;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface PtEarningRepository extends JpaRepository<PtEarning, Long> {
    List<PtEarning> findByPersonalTrainerIdAndPayoutIsNullOrderByCreatedAtDesc(Long ptId);
    List<PtEarning> findByPayoutIdOrderByCreatedAtAsc(Long payoutId);
    List<PtEarning> findByPersonalTrainerIdOrderByCreatedAtDesc(Long ptId);

    @Query("select coalesce(sum(e.ptCommission), 0) from PtEarning e " +
           "where e.personalTrainer.id = :ptId and e.payout is null")
    BigDecimal sumPendingEarnings(@Param("ptId") Long ptId);

    @Query("select coalesce(sum(e.ptCommission), 0) from PtEarning e " +
           "where e.personalTrainer.organization.id = :orgId " +
           "and e.ptSession.scheduledDate between :from and :to")
    BigDecimal sumEarningsByOrgAndPeriod(@Param("orgId") Long orgId,
        @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select coalesce(sum(e.gymRetained), 0) from PtEarning e " +
           "where e.personalTrainer.organization.id = :orgId " +
           "and e.ptSession.scheduledDate between :from and :to")
    BigDecimal sumGymRetainedByOrgAndPeriod(@Param("orgId") Long orgId,
        @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select coalesce(sum(e.grossRevenue), 0) from PtEarning e " +
           "where e.personalTrainer.organization.id = :orgId " +
           "and e.ptSession.scheduledDate between :from and :to")
    BigDecimal sumGrossRevenueByOrgAndPeriod(@Param("orgId") Long orgId,
        @Param("from") LocalDate from, @Param("to") LocalDate to);

    boolean existsByPtSessionId(Long ptSessionId);
}
