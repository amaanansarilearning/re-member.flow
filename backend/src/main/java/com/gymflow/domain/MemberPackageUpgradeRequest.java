package com.gymflow.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "member_package_upgrade_requests", indexes = {
    @Index(name = "idx_mem_upg_org", columnList = "organization_id"),
    @Index(name = "idx_mem_upg_branch", columnList = "branch_id"),
    @Index(name = "idx_mem_upg_member", columnList = "member_id"),
    @Index(name = "idx_mem_upg_status", columnList = "status"),
    @Index(name = "idx_mem_upg_membership", columnList = "membership_id")
})
public class MemberPackageUpgradeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "membership_id", nullable = false)
    private Membership membership;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private UserAccount member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "new_plan_id", nullable = false)
    private MembershipPlan newPlan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private GymBranch branch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private GymOrganization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "personal_trainer_id")
    private PersonalTrainer personalTrainer;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal topUpAmount = BigDecimal.ZERO;

    @Column(nullable = false, length = 50)
    private String paymentMethod;

    @Column(length = 120)
    private String transactionId;

    @Column(length = 500)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MemberUpgradeRequestStatus status = MemberUpgradeRequestStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_id")
    private UserAccount reviewedBy;

    @Column(length = 120)
    private String reviewedByName;

    @Column
    private Instant reviewedAt;

    @Column(length = 500)
    private String rejectionReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resulting_membership_id")
    private Membership resultingMembership;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected MemberPackageUpgradeRequest() {
    }

    public MemberPackageUpgradeRequest(
            Membership membership,
            UserAccount member,
            MembershipPlan newPlan,
            GymBranch branch,
            GymOrganization organization,
            PersonalTrainer personalTrainer,
            BigDecimal topUpAmount,
            String paymentMethod,
            String transactionId,
            String notes) {
        this.membership = membership;
        this.member = member;
        this.newPlan = newPlan;
        this.branch = branch;
        this.organization = organization;
        this.personalTrainer = personalTrainer;
        this.topUpAmount = topUpAmount;
        this.paymentMethod = paymentMethod;
        this.transactionId = transactionId;
        this.notes = notes;
        this.status = MemberUpgradeRequestStatus.PENDING;
        this.createdAt = Instant.now();
    }

    public void approve(UserAccount reviewer, Membership resultingMembership) {
        this.status = MemberUpgradeRequestStatus.APPROVED;
        this.reviewedBy = reviewer;
        this.reviewedByName = reviewer != null ? reviewer.getFullName() : "Staff";
        this.reviewedAt = Instant.now();
        this.resultingMembership = resultingMembership;
    }

    public void reject(UserAccount reviewer, String reason) {
        this.status = MemberUpgradeRequestStatus.REJECTED;
        this.reviewedBy = reviewer;
        this.reviewedByName = reviewer != null ? reviewer.getFullName() : "Staff";
        this.reviewedAt = Instant.now();
        this.rejectionReason = reason;
    }

    public Long getId() {
        return id;
    }

    public Membership getMembership() {
        return membership;
    }

    public UserAccount getMember() {
        return member;
    }

    public MembershipPlan getNewPlan() {
        return newPlan;
    }

    public GymBranch getBranch() {
        return branch;
    }

    public GymOrganization getOrganization() {
        return organization;
    }

    public PersonalTrainer getPersonalTrainer() {
        return personalTrainer;
    }

    public BigDecimal getTopUpAmount() {
        return topUpAmount;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getNotes() {
        return notes;
    }

    public MemberUpgradeRequestStatus getStatus() {
        return status;
    }

    public UserAccount getReviewedBy() {
        return reviewedBy;
    }

    public String getReviewedByName() {
        return reviewedByName;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public Membership getResultingMembership() {
        return resultingMembership;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

