package com.gymflow.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Immutable financial record created when a PT session is completed.
 * Stores the revenue split at the exact commission rate that was active
 * at booking time. These records must never be recomputed — they are the
 * financial source of truth for PT earnings.
 */
@Entity
@Table(name = "pt_earnings", indexes = {
        @Index(name = "idx_pt_earning_trainer", columnList = "personal_trainer_id"),
        @Index(name = "idx_pt_earning_payout", columnList = "payout_id"),
        @Index(name = "idx_pt_earning_session", columnList = "pt_session_id", unique = true)
})
public class PtEarning {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pt_session_id", nullable = false, unique = true)
    private PtSession ptSession;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "personal_trainer_id", nullable = false)
    private PersonalTrainer personalTrainer;

    /** Total revenue attributed to this session (session fee or plan apportionment) */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal grossRevenue;

    /** PT's share = grossRevenue * commissionRate / 100 */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal ptCommission;

    /** Gym's share = grossRevenue – ptCommission */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal gymRetained;

    /** Snapshot of the rate used – never changes after creation */
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal commissionRate;

    /**
     * FK to the payout batch this earning was included in.
     * Null until a payout is created and paid.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payout_id")
    private PtPayout payout;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected PtEarning() {
    }

    public PtEarning(PtSession ptSession, PersonalTrainer personalTrainer,
            BigDecimal grossRevenue, BigDecimal ptCommission, BigDecimal gymRetained,
            BigDecimal commissionRate) {
        this.ptSession = ptSession;
        this.personalTrainer = personalTrainer;
        this.grossRevenue = grossRevenue;
        this.ptCommission = ptCommission;
        this.gymRetained = gymRetained;
        this.commissionRate = commissionRate;
    }

    public void assignPayout(PtPayout payout) {
        this.payout = payout;
    }

    public Long getId() { return id; }
    public PtSession getPtSession() { return ptSession; }
    public PersonalTrainer getPersonalTrainer() { return personalTrainer; }
    public BigDecimal getGrossRevenue() { return grossRevenue; }
    public BigDecimal getPtCommission() { return ptCommission; }
    public BigDecimal getGymRetained() { return gymRetained; }
    public BigDecimal getCommissionRate() { return commissionRate; }
    public PtPayout getPayout() { return payout; }
    public Instant getCreatedAt() { return createdAt; }
}

