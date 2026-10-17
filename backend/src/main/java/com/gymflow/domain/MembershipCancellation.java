package com.gymflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "membership_cancellations", indexes = {
        @Index(name = "idx_membership_cancellations_membership", columnList = "membership_id"),
        @Index(name = "idx_membership_cancellations_org_time", columnList = "organization_id, performed_at")
})
public class MembershipCancellation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "membership_id", nullable = false)
    private Long membershipId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "member_name", nullable = false, length = 100)
    private String memberName;

    @Column(name = "member_email", nullable = false, length = 254)
    private String memberEmail;

    @Column(nullable = false, length = 40)
    private String reason;

    @Column(name = "other_explanation", length = 1000)
    private String otherExplanation;

    @Column(nullable = false, columnDefinition = "text")
    private String remarks;

    @Column(name = "cancellation_date", nullable = false)
    private LocalDate cancellationDate;

    @Column(name = "requested_by_id", nullable = false)
    private Long requestedById;

    @Column(name = "requested_by_name", nullable = false, length = 100)
    private String requestedByName;

    @Column(name = "requested_by_email", nullable = false, length = 254)
    private String requestedByEmail;

    @Column(name = "performed_by_id", nullable = false)
    private Long performedById;

    @Column(name = "performed_by_name", nullable = false, length = 100)
    private String performedByName;

    @Column(name = "performed_by_email", nullable = false, length = 254)
    private String performedByEmail;

    @Column(name = "approved_by_id")
    private Long approvedById;

    @Column(name = "approved_by_name", length = 100)
    private String approvedByName;

    @Column(name = "approval_status", nullable = false, length = 24)
    private String approvalStatus;

    @Column(name = "status_before", nullable = false, length = 24)
    private String statusBefore;

    @Column(name = "status_after", nullable = false, length = 24)
    private String statusAfter;

    @Column(nullable = false, length = 24)
    private String status;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "performed_at", nullable = false, updatable = false)
    private Instant performedAt;

    @Column(name = "status_changed_at", nullable = false, updatable = false)
    private Instant statusChangedAt;

    protected MembershipCancellation() {
    }

    public MembershipCancellation(Membership membership, UserAccount actor, String reason,
            String otherExplanation, String remarks, LocalDate cancellationDate, String statusBefore) {
        this.organizationId = membership.getMember().getOrganization().getId();
        this.membershipId = membership.getId();
        this.memberId = membership.getMember().getId();
        this.memberName = membership.getMember().getFullName();
        this.memberEmail = membership.getMember().getEmail();
        this.reason = reason;
        this.otherExplanation = otherExplanation;
        this.remarks = remarks;
        this.cancellationDate = cancellationDate;
        this.requestedById = actor.getId();
        this.requestedByName = actor.getFullName();
        this.requestedByEmail = actor.getEmail();
        this.performedById = actor.getId();
        this.performedByName = actor.getFullName();
        this.performedByEmail = actor.getEmail();
        this.approvalStatus = "NOT_REQUIRED";
        this.statusBefore = statusBefore;
        this.statusAfter = membership.getStatus().name();
        this.status = "COMPLETED";
        this.requestedAt = Instant.now();
        this.performedAt = this.requestedAt;
        this.statusChangedAt = this.requestedAt;
    }

    public Long getId() { return id; }
    public Long getOrganizationId() { return organizationId; }
    public Long getMembershipId() { return membershipId; }
    public Long getMemberId() { return memberId; }
    public String getReason() { return reason; }
    public String getOtherExplanation() { return otherExplanation; }
    public String getRemarks() { return remarks; }
    public LocalDate getCancellationDate() { return cancellationDate; }
    public Long getRequestedById() { return requestedById; }
    public String getRequestedByName() { return requestedByName; }
    public Long getPerformedById() { return performedById; }
    public String getPerformedByName() { return performedByName; }
    public Long getApprovedById() { return approvedById; }
    public String getApprovalStatus() { return approvalStatus; }
    public String getStatusBefore() { return statusBefore; }
    public String getStatusAfter() { return statusAfter; }
    public String getStatus() { return status; }
    public Instant getRequestedAt() { return requestedAt; }
    public Instant getPerformedAt() { return performedAt; }
    public Instant getStatusChangedAt() { return statusChangedAt; }
}
