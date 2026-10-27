package com.gymflow.repository;

import com.gymflow.domain.ActionHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ActionHistoryRepository extends JpaRepository<ActionHistory, Long> {
    long countByOrganizationId(Long organizationId);

    List<ActionHistory> findTop250ByOrganizationIdOrderByOccurredAtDesc(Long organizationId);

    @Query("select h.entityId as membershipId, max(h.occurredAt) as activityAt "
            + "from ActionHistory h where h.organizationId = :organizationId "
            + "and h.entityType = 'MEMBERSHIP' and h.entityId in :membershipIds group by h.entityId")
    List<MembershipActivityProjection> findLatestMembershipActivity(
            @Param("organizationId") Long organizationId, @Param("membershipIds") List<Long> membershipIds);
}
