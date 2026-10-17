package com.gymflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "gym_organizations")
public class GymOrganization {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SubscriptionTier subscriptionTier = SubscriptionTier.STANDARD;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SubscriptionStatus subscriptionStatus = SubscriptionStatus.ACTIVE;

    @Column
    private Instant subscriptionExpiresAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected GymOrganization() {
    }

    public GymOrganization(String name, String code) {
        this(name, code, SubscriptionTier.STANDARD);
    }

    public GymOrganization(String name, String code, SubscriptionTier subscriptionTier) {
        this.name = name.trim();
        this.code = code.trim().toUpperCase();
        this.subscriptionTier = subscriptionTier == null ? SubscriptionTier.STANDARD : subscriptionTier;
        this.subscriptionStatus = SubscriptionStatus.ACTIVE;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCode() {
        return code;
    }

    public SubscriptionTier getSubscriptionTier() {
        return subscriptionTier;
    }

    public SubscriptionStatus getSubscriptionStatus() {
        return subscriptionStatus;
    }

    public Instant getSubscriptionExpiresAt() {
        return subscriptionExpiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void rename(String name) {
        this.name = name.trim();
    }

    public boolean isPremium() {
        return hasFeature(AppFeature.PERSONAL_TRAINERS);
    }

    public boolean hasFeature(AppFeature feature) {
        if (subscriptionStatus == SubscriptionStatus.SUSPENDED || subscriptionStatus == SubscriptionStatus.EXPIRED) {
            return false;
        }
        if (subscriptionStatus == SubscriptionStatus.CANCELLED) {
            if (subscriptionExpiresAt == null || Instant.now().isAfter(subscriptionExpiresAt)) {
                return false;
            }
        }
        if (subscriptionExpiresAt != null && Instant.now().isAfter(subscriptionExpiresAt)) {
            return false;
        }
        return switch (feature) {
            case CORE_MANAGEMENT -> true;
            case PERSONAL_TRAINERS, ACTION_HISTORY -> subscriptionTier == SubscriptionTier.PREMIUM;
        };
    }

    public List<String> getEnabledFeatures() {
        List<String> list = new ArrayList<>();
        list.add("CORE_MANAGEMENT");
        if (hasFeature(AppFeature.PERSONAL_TRAINERS)) {
            list.add("PERSONAL_TRAINERS");
        }
        if (hasFeature(AppFeature.ACTION_HISTORY)) {
            list.add("ACTION_HISTORY");
        }
        return list;
    }

    public void upgradeTier(SubscriptionTier tier) {
        this.subscriptionTier = tier;
        this.subscriptionStatus = SubscriptionStatus.ACTIVE;
    }

    public void cancelSubscription(boolean immediate) {
        if (immediate) {
            this.subscriptionTier = SubscriptionTier.STANDARD;
            this.subscriptionStatus = SubscriptionStatus.ACTIVE;
            this.subscriptionExpiresAt = null;
        } else {
            this.subscriptionStatus = SubscriptionStatus.CANCELLED;
            if (this.subscriptionExpiresAt == null) {
                // Default 30-day grace period from now if no prior expiry was set
                this.subscriptionExpiresAt = Instant.now().plus(java.time.Duration.ofDays(30));
            }
        }
    }

    public void reactivateSubscription() {
        if (this.subscriptionStatus == SubscriptionStatus.CANCELLED) {
            this.subscriptionStatus = SubscriptionStatus.ACTIVE;
        }
    }

    public void applyActivationCode(SubscriptionTier tier, Integer durationDays) {
        this.subscriptionTier = tier != null ? tier : SubscriptionTier.PREMIUM;
        this.subscriptionStatus = SubscriptionStatus.ACTIVE;
        if (durationDays == null) {
            this.subscriptionExpiresAt = null; // perpetual / lifetime
        } else {
            Instant now = Instant.now();
            if (this.subscriptionExpiresAt != null && this.subscriptionExpiresAt.isAfter(now)) {
                this.subscriptionExpiresAt = this.subscriptionExpiresAt.plus(java.time.Duration.ofDays(durationDays));
            } else {
                this.subscriptionExpiresAt = now.plus(java.time.Duration.ofDays(durationDays));
            }
        }
    }

    public void updateSubscription(SubscriptionTier tier, SubscriptionStatus status, Instant expiresAt) {
        if (tier != null) {
            this.subscriptionTier = tier;
        }
        if (status != null) {
            this.subscriptionStatus = status;
        }
        this.subscriptionExpiresAt = expiresAt;
    }
}