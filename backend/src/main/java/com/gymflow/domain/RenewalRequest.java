package com.gymflow.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "membership_renewal_requests", indexes = {
    @Index(name = "idx_rnw_org", columnList = "organization_id"),
    @Index(name = "idx_rnw_branch", columnList = "branch_id"),
    @Index(name = "idx_rnw_member", columnList = "member_id"),
    @Index(name = "idx_rnw_status", columnList = "status"),
    @Index(name = "idx_rnw_membership", columnList = "membership_id")
})
public class RenewalRequest {

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
    @JoinColumn(name = "plan_id", nullable = false)
    private MembershipPlan plan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private GymBranch branch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private GymOrganization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "personal_trainer_id")
    private PersonalTrainer personalTrainer;

    @Column(name = "requested_start_date", nullable = false)
    private LocalDate requestedStartDate;

    @Column(name = "payment_method", nullable = false, length = 40)
    private String paymentMethod;

    @Column(name = "transaction_id", length = 120)
    private String transactionId;

    @Column(length = 500)
    private String notes;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "discount_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal discountPercent = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RenewalRequestStatus status = RenewalRequestStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_id")
    private UserAccount reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "rejection_reason", length = 255)
    private String rejectionReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resulting_membership_id")
    private Membership resultingMembership;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected RenewalRequest() {
    }

    public RenewalRequest(Membership membership,
                          UserAccount member,
                          MembershipPlan plan,
                          GymBranch branch,
                          GymOrganization organization,
                          PersonalTrainer personalTrainer,
                          LocalDate requestedStartDate,
                          String paymentMethod,
                          String transactionId,
                          String notes,
                          BigDecimal amount,
                          BigDecimal discountPercent) {
        this.membership = membership;
        this.member = member;
        this.plan = plan;
        this.branch = branch;
        this.organization = organization;
        this.personalTrainer = personalTrainer;
        this.requestedStartDate = requestedStartDate;
        this.paymentMethod = paymentMethod != null ? paymentMethod.trim().toUpperCase() : "CASH";
        this.transactionId = transactionId != null ? transactionId.trim() : null;
        this.notes = notes != null ? notes.trim() : null;
        this.amount = amount;
        this.discountPercent = discountPercent != null ? discountPercent : BigDecimal.ZERO;
        this.status = RenewalRequestStatus.PENDING;
        this.createdAt = Instant.now();
    }

    public void approve(UserAccount reviewer, Membership resultingMembership, BigDecimal finalDiscount,
                        String finalPaymentMethod, String finalTransactionId) {
        this.status = RenewalRequestStatus.APPROVED;
        this.reviewedBy = reviewer;
        this.reviewedAt = Instant.now();
        this.resultingMembership = resultingMembership;
        if (finalDiscount != null) {
            this.discountPercent = finalDiscount;
        }
        if (finalPaymentMethod != null && !finalPaymentMethod.isBlank()) {
            this.paymentMethod = finalPaymentMethod.trim().toUpperCase();
        }
        if (finalTransactionId != null) {
            this.transactionId = finalTransactionId.trim();
        }
    }

    public void reject(UserAccount reviewer, String reason) {
        this.status = RenewalRequestStatus.REJECTED;
        this.reviewedBy = reviewer;
        this.reviewedAt = Instant.now();
        this.rejectionReason = (reason != null && !reason.isBlank()) ? reason.trim() : "Rejected by staff";
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

    public MembershipPlan getPlan() {
        return plan;
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

    public LocalDate getRequestedStartDate() {
        return requestedStartDate;
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

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getDiscountPercent() {
        return discountPercent;
    }

    public RenewalRequestStatus getStatus() {
        return status;
    }

    public UserAccount getReviewedBy() {
        return reviewedBy;
    }

    public String getReviewedByName() {
        return reviewedBy != null ? reviewedBy.getFullName() : null;
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

