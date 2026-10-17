package com.gymflow.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Personal Trainer profile entity. Each PT is linked 1:1 to a UserAccount
 * with role PERSONAL_TRAINER. The profile stores gym-specific attributes
 * (specializations, fee, commission) separately from authentication data.
 */
@Entity
@Table(name = "personal_trainers", indexes = {
        @Index(name = "idx_pt_organization", columnList = "organization_id"),
        @Index(name = "idx_pt_user_account", columnList = "user_account_id")
})
public class PersonalTrainer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_account_id", nullable = false, unique = true)
    private UserAccount userAccount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private GymOrganization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private GymBranch branch;

    /** Free-text list of specializations e.g. "Strength, HIIT, Yoga" */
    @Column(length = 500)
    private String specializations;

    /** Short professional bio shown to members */
    @Column(length = 1000)
    private String bio;

    /**
     * Default per-session fee charged to members when booking standalone PT sessions.
     * For package-inclusive sessions this may be overridden at the plan level.
     */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal sessionFeePerHour = BigDecimal.ZERO;

    /**
     * Commission percentage (0-100) the PT earns from their PT-inclusive revenue.
     * Stored as a snapshot on PtEarning rows so historical earnings are unaffected
     * when the rate is updated.
     */
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal commissionPercent = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected PersonalTrainer() {
    }

    public PersonalTrainer(UserAccount userAccount, GymOrganization organization, GymBranch branch,
            String specializations, String bio, BigDecimal sessionFeePerHour, BigDecimal commissionPercent) {
        this.userAccount = userAccount;
        this.organization = organization;
        this.branch = branch;
        this.specializations = specializations == null ? null : specializations.trim();
        this.bio = bio == null ? null : bio.trim();
        this.sessionFeePerHour = sessionFeePerHour == null ? BigDecimal.ZERO : sessionFeePerHour;
        this.commissionPercent = commissionPercent == null ? BigDecimal.ZERO : commissionPercent;
        this.active = true;
    }

    public void update(GymBranch branch, String specializations, String bio,
            BigDecimal sessionFeePerHour, BigDecimal commissionPercent) {
        this.branch = branch;
        this.specializations = specializations == null ? null : specializations.trim();
        this.bio = bio == null ? null : bio.trim();
        this.sessionFeePerHour = sessionFeePerHour == null ? BigDecimal.ZERO : sessionFeePerHour;
        this.commissionPercent = commissionPercent == null ? BigDecimal.ZERO : commissionPercent;
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    public Long getId() { return id; }
    public UserAccount getUserAccount() { return userAccount; }
    public GymOrganization getOrganization() { return organization; }
    public GymBranch getBranch() { return branch; }
    public String getSpecializations() { return specializations; }
    public String getBio() { return bio; }
    public BigDecimal getSessionFeePerHour() { return sessionFeePerHour; }
    public BigDecimal getCommissionPercent() { return commissionPercent; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
}

