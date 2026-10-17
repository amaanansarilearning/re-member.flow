package com.gymflow.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "activation_codes", indexes = {
    @Index(name = "idx_act_code", columnList = "code", unique = true)
})
public class ActivationCode {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SubscriptionTier tier = SubscriptionTier.PREMIUM;

    @Column
    private Integer durationDays; // e.g., 30, 90, 365, or null for perpetual

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ActivationCodeStatus status = ActivationCodeStatus.AVAILABLE;

    @Column(nullable = false)
    private int maxRedemptions = 1;

    @Column(nullable = false)
    private int redemptionCount = 0;

    @Column(length = 255)
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "redeemed_by_org_id")
    private GymOrganization redeemedByOrganization;

    @Column(length = 120)
    private String redeemedByEmail;

    @Column
    private Instant redeemedAt;

    @Column
    private Instant codeExpiresAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(length = 120)
    private String createdBy;

    protected ActivationCode() {
    }

    public ActivationCode(String code, SubscriptionTier tier, Integer durationDays, String notes, String createdBy) {
        this.code = code.trim().toUpperCase();
        this.tier = tier != null ? tier : SubscriptionTier.PREMIUM;
        this.durationDays = durationDays;
        this.notes = notes != null ? notes.trim() : null;
        this.createdBy = createdBy != null ? createdBy.trim().toLowerCase() : null;
        this.status = ActivationCodeStatus.AVAILABLE;
        this.maxRedemptions = 1;
        this.redemptionCount = 0;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public SubscriptionTier getTier() {
        return tier;
    }

    public Integer getDurationDays() {
        return durationDays;
    }

    public ActivationCodeStatus getStatus() {
        if (status == ActivationCodeStatus.AVAILABLE && codeExpiresAt != null && Instant.now().isAfter(codeExpiresAt)) {
            return ActivationCodeStatus.EXPIRED;
        }
        return status;
    }

    public int getMaxRedemptions() {
        return maxRedemptions;
    }

    public int getRedemptionCount() {
        return redemptionCount;
    }

    public String getNotes() {
        return notes;
    }

    public GymOrganization getRedeemedByOrganization() {
        return redeemedByOrganization;
    }

    public String getRedeemedByEmail() {
        return redeemedByEmail;
    }

    public Instant getRedeemedAt() {
        return redeemedAt;
    }

    public Instant getCodeExpiresAt() {
        return codeExpiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public boolean isRedeemable() {
        if (getStatus() != ActivationCodeStatus.AVAILABLE) {
            return false;
        }
        return redemptionCount < maxRedemptions;
    }

    public void redeem(GymOrganization org, String email) {
        if (!isRedeemable()) {
            throw new IllegalStateException("Activation code is not valid for redemption");
        }
        this.redemptionCount++;
        this.redeemedByOrganization = org;
        this.redeemedByEmail = email != null ? email.trim().toLowerCase() : null;
        this.redeemedAt = Instant.now();
        if (this.redemptionCount >= this.maxRedemptions) {
            this.status = ActivationCodeStatus.REDEEMED;
        }
    }

    public void revoke() {
        this.status = ActivationCodeStatus.REVOKED;
    }
}

