package com.gymflow.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A payout batch grouping unpaid PtEarning records for a personal trainer
 * over a time period. Once PAID, the record is immutable.
 */
@Entity
@Table(name = "pt_payouts", indexes = {
        @Index(name = "idx_pt_payout_trainer", columnList = "personal_trainer_id"),
        @Index(name = "idx_pt_payout_status", columnList = "status")
})
public class PtPayout {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "personal_trainer_id", nullable = false)
    private PersonalTrainer personalTrainer;

    @Column(nullable = false)
    private LocalDate periodFrom;

    @Column(nullable = false)
    private LocalDate periodTo;

    /** Sum of all ptCommission values for earnings included in this payout */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalEarnings;

    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    @Column
    private Instant paidAt;

    /** Full name of the person who marked this as paid */
    @Column(length = 100)
    private String paidByName;

    @Column(length = 1000)
    private String notes;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected PtPayout() {
    }

    public PtPayout(PersonalTrainer personalTrainer, LocalDate periodFrom, LocalDate periodTo,
            BigDecimal totalEarnings, String notes) {
        this.personalTrainer = personalTrainer;
        this.periodFrom = periodFrom;
        this.periodTo = periodTo;
        this.totalEarnings = totalEarnings;
        this.notes = notes;
        this.status = "PENDING";
    }

    public void markPaid(String paidByName) {
        this.status = "PAID";
        this.paidAt = Instant.now();
        this.paidByName = paidByName;
    }

    public Long getId() { return id; }
    public PersonalTrainer getPersonalTrainer() { return personalTrainer; }
    public LocalDate getPeriodFrom() { return periodFrom; }
    public LocalDate getPeriodTo() { return periodTo; }
    public BigDecimal getTotalEarnings() { return totalEarnings; }
    public String getStatus() { return status; }
    public Instant getPaidAt() { return paidAt; }
    public String getPaidByName() { return paidByName; }
    public String getNotes() { return notes; }
    public Instant getCreatedAt() { return createdAt; }
}

