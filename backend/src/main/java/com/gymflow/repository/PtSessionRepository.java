package com.gymflow.repository;

import com.gymflow.domain.PtSession;
import com.gymflow.domain.PtSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PtSessionRepository extends JpaRepository<PtSession, Long> {
    List<PtSession> findByPersonalTrainerIdAndScheduledDateBetweenOrderByScheduledDateAscStartTimeAsc(
        Long ptId, LocalDate from, LocalDate to);

    List<PtSession> findByMembershipIdOrderByScheduledDateAscStartTimeAsc(Long membershipId);

    List<PtSession> findByPersonalTrainerIdOrderByScheduledDateDescStartTimeDesc(Long ptId);

    @Query("select case when count(s) > 0 then true else false end from PtSession s " +
           "where s.personalTrainer.id = :ptId and s.scheduledDate = :date " +
           "and s.startTime < :endTime and s.endTime > :startTime " +
           "and s.status not in ('CANCELLED', 'MISSED') " +
           "and (:excludeId is null or s.id <> :excludeId)")
    boolean hasConflict(@Param("ptId") Long ptId, @Param("date") LocalDate date,
        @Param("startTime") java.time.LocalTime startTime,
        @Param("endTime") java.time.LocalTime endTime,
        @Param("excludeId") Long excludeId);

    long countByPersonalTrainerIdAndStatus(Long ptId, PtSessionStatus status);

    @Query("select count(s) from PtSession s where s.personalTrainer.id = :ptId " +
           "and s.scheduledDate between :from and :to and s.status = :status")
    long countByPtAndDateRangeAndStatus(@Param("ptId") Long ptId,
        @Param("from") LocalDate from, @Param("to") LocalDate to,
        @Param("status") PtSessionStatus status);

    List<PtSession> findByPersonalTrainerIdAndScheduledDateOrderByStartTimeAsc(Long ptId, LocalDate date);

    Optional<PtSession> findByIdAndPersonalTrainerId(Long id, Long ptId);
    Optional<PtSession> findByIdAndMembershipMemberId(Long id, Long memberId);

    @Query("select count(s) from PtSession s " +
           "where s.personalTrainer.organization.id = :orgId " +
           "and s.status = :status " +
           "and s.scheduledDate between :from and :to")
    long countByPersonalTrainerOrganizationIdAndStatusAndPeriod(
        @Param("orgId") Long orgId, @Param("status") PtSessionStatus status,
        @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select count(s) from PtSession s where s.personalTrainer.organization.id = :orgId")
    long countByOrganizationId(@Param("orgId") Long orgId);

    long countByPersonalTrainerId(Long personalTrainerId);
}
