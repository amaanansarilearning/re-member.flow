package com.gymflow.repository;

import com.gymflow.domain.RenewalReminder;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RenewalReminderRepository extends JpaRepository<RenewalReminder, Long> {
    @Query("select case when count(r) > 0 then true else false end from RenewalReminder r "
            + "where r.membership.id = :membershipId and r.daysBefore = :daysBefore "
            + "and lower(r.recipientEmail) = lower(:recipientEmail)")
    boolean existsForRecipient(@Param("membershipId") Long membershipId,
            @Param("daysBefore") int daysBefore, @Param("recipientEmail") String recipientEmail);

    List<RenewalReminder> findTop100ByStatusInAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
            List<String> statuses, Instant now);

    List<RenewalReminder> findTop500ByMembershipMemberOrganizationIdOrderByNextAttemptAtDesc(
            Long organizationId);
}
