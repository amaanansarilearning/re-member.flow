package com.gymflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "action_history", indexes = {
        @Index(name = "idx_action_history_org_time", columnList = "organization_id, occurred_at"),
        @Index(name = "idx_action_history_membership_time",
                columnList = "organization_id, entity_type, entity_id, occurred_at")
})
public class ActionHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "actor_id", nullable = false)
    private Long actorId;

    @Column(name = "actor_name", nullable = false, length = 100)
    private String actorName;

    @Column(name = "actor_email", nullable = false, length = 254)
    private String actorEmail;

    @Column(nullable = false, length = 40)
    private String action;

    @Column(name = "entity_type", nullable = false, length = 40)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(length = 500)
    private String reason;

    @Lob
    @Column(name = "before_data", columnDefinition = "LONGTEXT")
    private String beforeData;

    @Lob
    @Column(name = "after_data", columnDefinition = "LONGTEXT")
    private String afterData;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt = Instant.now();

    protected ActionHistory() {
    }

    public ActionHistory(Long organizationId, Long actorId, String actorName, String actorEmail, String action,
            String entityType, Long entityId, String reason, String beforeData, String afterData) {
        this.organizationId = organizationId;
        this.actorId = actorId;
        this.actorName = actorName;
        this.actorEmail = actorEmail;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.reason = reason;
        this.beforeData = beforeData;
        this.afterData = afterData;
    }

    public Long getId() { return id; }
    public Long getOrganizationId() { return organizationId; }
    public Long getActorId() { return actorId; }
    public String getActorName() { return actorName; }
    public String getActorEmail() { return actorEmail; }
    public String getAction() { return action; }
    public String getEntityType() { return entityType; }
    public Long getEntityId() { return entityId; }
    public String getReason() { return reason; }
    public String getBeforeData() { return beforeData; }
    public String getAfterData() { return afterData; }
    public Instant getOccurredAt() { return occurredAt; }
}
