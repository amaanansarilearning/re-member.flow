package com.gymflow.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * A single PT session booking between a personal trainer and a member.
 * Financial snapshots (commissionRateSnapshot, sessionFeeSnapshot) are captured
 * at booking time so historical records are unaffected by rate changes.
 */
@Entity
@Table(name = "pt_sessions", indexes = {
        @Index(name = "idx_pt_session_trainer", columnList = "personal_trainer_id"),
        @Index(name = "idx_pt_session_membership", columnList = "membership_id"),
        @Index(name = "idx_pt_session_date", columnList = "scheduled_date")
})
public class PtSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "personal_trainer_id", nullable = false)
    private PersonalTrainer personalTrainer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "membership_id", nullable = false)
    private Membership membership;

    @Column(name = "scheduled_date", nullable = false)
    private LocalDate scheduledDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PtSessionStatus status = PtSessionStatus.BOOKED;

    @Column(length = 1000)
    private String notes;

    @Column(length = 500)
    private String cancelReason;

    @Column
    private Instant completedAt;

    @Column(name = "verification_method", length = 32)
    private String verificationMethod;

    /**
     * Commission rate snapshot at session booking time. Preserves historical accuracy
     * even if the PT's commission rate is later changed.
     */
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal commissionRateSnapshot;

    /**
     * Session fee snapshot at booking time (PT's sessionFeePerHour at the time).
     */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal sessionFeeSnapshot;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    protected PtSession() {
    }

    public PtSession(PersonalTrainer personalTrainer, Membership membership,
            LocalDate scheduledDate, LocalTime startTime, LocalTime endTime,
            String notes, BigDecimal commissionRateSnapshot, BigDecimal sessionFeeSnapshot) {
        this.personalTrainer = personalTrainer;
        this.membership = membership;
        this.scheduledDate = scheduledDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.notes = notes == null ? null : notes.trim();
        this.commissionRateSnapshot = commissionRateSnapshot;
        this.sessionFeeSnapshot = sessionFeeSnapshot;
        this.status = PtSessionStatus.BOOKED;
    }

    public void reschedule(LocalDate scheduledDate, LocalTime startTime, LocalTime endTime) {
        this.scheduledDate = scheduledDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.updatedAt = Instant.now();
    }

    public void markTentative() {
        this.status = PtSessionStatus.TENTATIVE;
        this.updatedAt = Instant.now();
    }

    public void confirm() {
        this.status = PtSessionStatus.BOOKED;
        this.updatedAt = Instant.now();
    }

    public void complete(String notes, String verificationMethod) {
        this.status = PtSessionStatus.COMPLETED;
        this.completedAt = Instant.now();
        this.updatedAt = Instant.now();
        this.verificationMethod = verificationMethod;
        if (notes != null && !notes.isBlank()) {
            this.notes = notes.trim();
        }
    }

    public void complete(String notes) {
        complete(notes, "SELF_VERIFIED");
    }

    public void cancel(String reason) {
        this.status = PtSessionStatus.CANCELLED;
        this.cancelReason = reason == null ? null : reason.trim();
        this.updatedAt = Instant.now();
    }

    public void markMissed() {
        this.status = PtSessionStatus.MISSED;
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public PersonalTrainer getPersonalTrainer() { return personalTrainer; }
    public Membership getMembership() { return membership; }
    public LocalDate getScheduledDate() { return scheduledDate; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public PtSessionStatus getStatus() { return status; }
    public String getNotes() { return notes; }
    public String getCancelReason() { return cancelReason; }
    public Instant getCompletedAt() { return completedAt; }
    public String getVerificationMethod() { return verificationMethod; }
    public BigDecimal getCommissionRateSnapshot() { return commissionRateSnapshot; }
    public BigDecimal getSessionFeeSnapshot() { return sessionFeeSnapshot; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

