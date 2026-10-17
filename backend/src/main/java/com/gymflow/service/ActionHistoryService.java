package com.gymflow.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymflow.domain.ActionHistory;
import com.gymflow.domain.GymOrganization;
import com.gymflow.domain.UserAccount;
import com.gymflow.repository.ActionHistoryRepository;
import com.gymflow.repository.MembershipActivityProjection;
import com.gymflow.web.ApiDtos.ActionHistoryDto;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ActionHistoryService {
    private final ActionHistoryRepository history;
    private final ObjectMapper objectMapper;

    public ActionHistoryService(ActionHistoryRepository history, ObjectMapper objectMapper) {
        this.history = history;
        this.objectMapper = objectMapper;
    }

    public void record(UserAccount actor, String action, String entityType, Long entityId, String reason,
            Object before, Object after) {
        GymOrganization organization = actor.getOrganization();
        if (organization == null) {
            throw new IllegalStateException("Cannot audit an action without an organization");
        }
        history.save(new ActionHistory(organization.getId(), actor.getId(), actor.getFullName(), actor.getEmail(),
                action, entityType, entityId, reason, serialize(before), serialize(after)));
    }

    public List<ActionHistoryDto> list(Long organizationId) {
        return history.findTop250ByOrganizationIdOrderByOccurredAtDesc(organizationId).stream()
                .map(item -> new ActionHistoryDto(item.getId(), item.getActorId(), item.getActorName(),
                        item.getActorEmail(), item.getAction(), item.getEntityType(), item.getEntityId(),
                        item.getReason(), item.getBeforeData(), item.getAfterData(), item.getOccurredAt()))
                .toList();
    }

    public Map<Long, java.time.Instant> latestMembershipActivity(Long organizationId, List<Long> membershipIds) {
        if (membershipIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, java.time.Instant> activityByMembership = new LinkedHashMap<>();
        history.findLatestMembershipActivity(organizationId, membershipIds)
                .forEach(activity -> activityByMembership.put(activity.getMembershipId(), activity.getActivityAt()));
        return activityByMembership;
    }

    private String serialize(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize action history details", exception);
        }
    }
}
