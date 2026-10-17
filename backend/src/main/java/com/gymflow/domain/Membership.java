package com.gymflow.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "memberships", indexes = {
        @Index(name = "idx_memberships_created_at", columnList = "created_at"),
        @Index(name = "idx_memberships_cancelled_at", columnList = "cancelled_at"),
        @Index(name = "idx_memberships_renewed_from", columnList = "renewed_from_id")
})
public class Membership {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private UserAccount member;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private MembershipPlan plan;

    @Column(name = "plan_name_snapshot", length = 80)
    private String planNameSnapshot;

    @Column(name = "plan_description_snapshot", length = 1000)
    private String planDescriptionSnapshot;

    @Column(name = "plan_duration_months_snapshot")
    private Integer planDurationMonthsSnapshot;

    @Column(name = "plan_duration_days_snapshot")
    private Integer planDurationDaysSnapshot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private GymBranch branch;

    @Column(name = "renewal_start_date")
    private LocalDate renewalStartDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "renewed_from_id")
    private Membership renewedFrom;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal listPrice;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amountPaid;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal discountPercent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MembershipStatus status = MembershipStatus.ACTIVE;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    // ─── PT fields ───────────────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "personal_trainer_id")
    private PersonalTrainer personalTrainer;

    @Column
    private Integer ptSessionsIncluded; // snapshot at registration

    @Column(nullable = false)
    private int ptSessionsUsed = 0;

    @Column(precision = 5, scale = 2)
    private BigDecimal ptCommissionRateSnapshot;

    // ─── Constructors ─────────────────────────────────────────────────────────

    protected Membership() {
    }

    public Membership(UserAccount member, MembershipPlan plan, LocalDate startDate, LocalDate endDate,
            BigDecimal listPrice, BigDecimal amountPaid, BigDecimal discountPercent) {
        this(member, plan, member.getBranch(), startDate, endDate, listPrice, amountPaid, discountPercent, null);
    }

    public Membership(UserAccount member, MembershipPlan plan, LocalDate startDate, LocalDate endDate,
            BigDecimal listPrice, BigDecimal amountPaid, BigDecimal discountPercent, Membership renewedFrom) {
        this(member, plan, member.getBranch(), startDate, endDate, listPrice, amountPaid, discountPercent, renewedFrom);
    }

    public Membership(UserAccount member, MembershipPlan plan, GymBranch branch, LocalDate startDate, LocalDate endDate,
            BigDecimal listPrice, BigDecimal amountPaid, BigDecimal discountPercent) {
        this(member, plan, branch, startDate, endDate, listPrice, amountPaid, discountPercent, null);
    }

    public Membership(UserAccount member, MembershipPlan plan, GymBranch branch, LocalDate startDate, LocalDate endDate,
            BigDecimal listPrice, BigDecimal amountPaid, BigDecimal discountPercent, Membership renewedFrom) {
        this.member = member;
        this.plan = plan;
        snapshotPlan(plan);
        this.branch = branch;
        this.renewedFrom = renewedFrom;
        this.startDate = startDate;
        this.endDate = endDate;
        this.listPrice = listPrice;
        this.amountPaid = amountPaid;
        this.discountPercent = discountPercent;
    }

    /**
     * PT-aware constructor variant — sets PT, and snapshots ptSessionsIncluded
     * and ptCommissionRateSnapshot from the plan and PT profile respectively.
     */
    public Membership(UserAccount member, MembershipPlan plan, GymBranch branch, LocalDate startDate, LocalDate endDate,
            BigDecimal listPrice, BigDecimal amountPaid, BigDecimal discountPercent, Membership renewedFrom,
            PersonalTrainer personalTrainer) {
        this(member, plan, branch, startDate, endDate, listPrice, amountPaid, discountPercent, renewedFrom);
        if (personalTrainer != null) {
            this.personalTrainer = personalTrainer;
            this.ptSessionsIncluded = plan.getIncludedPtSessions();
            this.ptCommissionRateSnapshot = personalTrainer.getCommissionPercent();
        }
    }

    // ─── Business methods ─────────────────────────────────────────────────────

    public void cancel() {
        cancel(Instant.now());
    }

    public void cancel(Instant cancellationTime) {
        this.status = MembershipStatus.CANCELLED;
        this.cancelledAt = cancellationTime;
    }

    public void extendForFreeze(long days) {
        this.endDate = this.endDate.plusDays(days);
    }

    public void reduceForFreeze(long days) {
        if (days < 1 || this.endDate.minusDays(days).isBefore(this.startDate)) {
            throw new IllegalStateException("Freeze extension cannot be reduced by the requested number of days");
        }
        this.endDate = this.endDate.minusDays(days);
    }

    public void transferTo(GymBranch branch) {
        this.branch = branch;
    }

    public void correct(MembershipPlan plan, LocalDate startDate, LocalDate endDate, BigDecimal listPrice,
            BigDecimal amountPaid, BigDecimal discountPercent) {
        this.plan = plan;
        snapshotPlan(plan);
        this.startDate = startDate;
        this.endDate = endDate;
        this.listPrice = listPrice;
        this.amountPaid = amountPaid;
        this.discountPercent = discountPercent;
    }

    public void incrementPtSessionsUsed() {
        this.ptSessionsUsed++;
    }

    public void assignPersonalTrainer(PersonalTrainer pt, Integer sessionsIncluded, BigDecimal commissionRate) {
        this.personalTrainer = pt;
        this.ptSessionsIncluded = sessionsIncluded;
        this.ptCommissionRateSnapshot = commissionRate;
    }

    private void snapshotPlan(MembershipPlan plan) {
        this.planNameSnapshot = plan.getName();
        this.planDescriptionSnapshot = plan.getDescription();
        this.planDurationMonthsSnapshot = plan.getDurationMonths();
        this.planDurationDaysSnapshot = plan.getDurationDays();
    }

    // ─── Getters ──────────────────────────────────────────────────────────────

    public Long getId() {
        return id;
    }

    public UserAccount getMember() {
        return member;
    }

    public MembershipPlan getPlan() {
        return plan;
    }

    public String getPlanNameSnapshot() {
        return planNameSnapshot == null ? plan.getName() : planNameSnapshot;
    }

    public String getPlanDescriptionSnapshot() {
        return planDescriptionSnapshot == null ? plan.getDescription() : planDescriptionSnapshot;
    }

    public Integer getPlanDurationMonthsSnapshot() {
        return planDurationMonthsSnapshot == null ? plan.getDurationMonths() : planDurationMonthsSnapshot;
    }

    public Integer getPlanDurationDaysSnapshot() {
        return planDurationDaysSnapshot == null ? plan.getDurationDays() : planDurationDaysSnapshot;
    }

    public GymBranch getBranch() {
        return branch;
    }

    public Membership getRenewedFrom() {
        return renewedFrom;
    }

    public LocalDate getRenewalStartDate() {
        return renewalStartDate;
    }

    public void scheduleRenewal(LocalDate startDate) {
        this.renewalStartDate = startDate;
    }

    public void clearScheduledRenewal() {
        this.renewalStartDate = null;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public BigDecimal getListPrice() {
        return listPrice;
    }

    public BigDecimal getAmountPaid() {
        return amountPaid;
    }

    public BigDecimal getDiscountPercent() {
        return discountPercent;
    }

    public MembershipStatus getStatus() {
        return status;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public PersonalTrainer getPersonalTrainer() {
        return personalTrainer;
    }

    public Integer getPtSessionsIncluded() {
        return ptSessionsIncluded;
    }

    public int getPtSessionsUsed() {
        return ptSessionsUsed;
    }

    public BigDecimal getPtCommissionRateSnapshot() {
        return ptCommissionRateSnapshot;
    }
}