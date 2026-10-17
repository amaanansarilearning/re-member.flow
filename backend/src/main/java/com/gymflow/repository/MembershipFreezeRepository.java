package com.gymflow.repository;

import com.gymflow.domain.MembershipFreeze;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MembershipFreezeRepository extends JpaRepository<MembershipFreeze, Long> {
    @Query("select case when count(f) > 0 then true else false end from MembershipFreeze f "
            + "where f.membership.id = :membershipId and f.cancelledAt is null "
            + "and (f.endedEarlyAt is null or f.endedEarlyAt > :startDate) "
            + "and f.startDate <= :endDate and f.endDate >= :startDate")
    boolean existsOverlapping(@Param("membershipId") Long membershipId,
            @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    List<MembershipFreeze> findByMembershipIdOrderByStartDateAsc(Long membershipId);

    List<MembershipFreeze> findByMembershipIdInOrderByMembershipIdAscStartDateAsc(Collection<Long> membershipIds);

    @Query("select case when count(f) > 0 then true else false end from MembershipFreeze f "
            + "where f.membership.id = :membershipId and f.cancelledAt is null "
            + "and (f.endedEarlyAt is null or f.endedEarlyAt > :date) "
            + "and f.startDate <= :date and f.endDate >= :date")
    boolean existsActiveOnDate(@Param("membershipId") Long membershipId, @Param("date") LocalDate date);
}
