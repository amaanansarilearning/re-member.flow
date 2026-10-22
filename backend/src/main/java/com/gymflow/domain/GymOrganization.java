package com.gymflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Lob;
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

    @Lob
    @Column(name = "logo_data", columnDefinition = "LONGTEXT")
    private String logoData;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SubscriptionTier subscriptionTier = SubscriptionTier.STANDARD;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SubscriptionStatus subscriptionStatus = SubscriptionStatus.ACTIVE;

    @Column
    private Instant subscriptionExpiresAt;

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode = "IN";

    @Column(name = "legal_jurisdiction", nullable = false, length = 50)
    private String legalJurisdiction = "INDIA_DPDP";

    @Column(name = "grievance_officer_email", length = 120)
    private String grievanceOfficerEmail;

    @Column(name = "dpo_email", length = 120)
    private String dpoEmail;

    @Column(name = "accepted_legal_version", length = 30)
    private String acceptedLegalVersion;

    @Column(name = "accepted_legal_at")
    private Instant acceptedLegalAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected GymOrganization() {
    }

    public GymOrganization(String name, String code) {
        this(name, code, SubscriptionTier.STANDARD);
    }

    public GymOrganization(String name, String code, SubscriptionTier subscriptionTier) {
        this(name, code, subscriptionTier, "IN");
    }

    public GymOrganization(String name, String code, SubscriptionTier subscriptionTier, String countryCode) {
        this.name = name.trim();
        this.code = code.trim().toUpperCase();
        this.subscriptionTier = subscriptionTier == null ? SubscriptionTier.STANDARD : subscriptionTier;
        this.subscriptionStatus = SubscriptionStatus.ACTIVE;
        configureJurisdiction(countryCode);
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

    public String getLogoData() {
        return logoData;
    }

    public void updateLogo(String logoData) {
        this.logoData = logoData;
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
            case CUSTOM_BRANDING -> subscriptionTier == SubscriptionTier.STARTER
                    || subscriptionTier == SubscriptionTier.PRO
                    || subscriptionTier == SubscriptionTier.ENTERPRISE
                    || subscriptionTier == SubscriptionTier.PREMIUM;
            case RECEIPT_IMAGE_UPLOAD, ACTION_HISTORY -> subscriptionTier == SubscriptionTier.PRO
                    || subscriptionTier == SubscriptionTier.ENTERPRISE
                    || subscriptionTier == SubscriptionTier.PREMIUM;
            case PERSONAL_TRAINERS, ANALYTICS -> subscriptionTier == SubscriptionTier.ENTERPRISE
                    || subscriptionTier == SubscriptionTier.PREMIUM;
        };
    }

    public List<String> getEnabledFeatures() {
        List<String> list = new ArrayList<>();
        list.add("CORE_MANAGEMENT");
        if (hasFeature(AppFeature.CUSTOM_BRANDING)) {
            list.add("CUSTOM_BRANDING");
        }
        if (hasFeature(AppFeature.RECEIPT_IMAGE_UPLOAD)) {
            list.add("RECEIPT_IMAGE_UPLOAD");
        }
        if (hasFeature(AppFeature.ACTION_HISTORY)) {
            list.add("ACTION_HISTORY");
        }
        if (hasFeature(AppFeature.PERSONAL_TRAINERS)) {
            list.add("PERSONAL_TRAINERS");
        }
        if (hasFeature(AppFeature.ANALYTICS)) {
            list.add("ANALYTICS");
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

    public static String resolveJurisdiction(String countryCode) {
        if (countryCode == null || countryCode.isBlank()) {
            return "INDIA_DPDP";
        }
        return switch (countryCode.trim().toUpperCase()) {
            case "IN" -> "INDIA_DPDP";
            case "GB" -> "UK_GDPR";
            case "US" -> "US_CCPA";
            case "CA" -> "CANADA_PIPEDA";
            case "AU" -> "AUSTRALIA_PRIVACY_ACT";
            case "AE", "SA" -> "UAE_PDPL";
            case "DE", "FR", "IT", "ES", "NL", "IE", "SE", "PL", "BE", "AT", "DK", "FI", "PT", "GR", "CZ", "RO", "HU", "EU" -> "EU_GDPR";
            default -> "GLOBAL_STANDARD";
        };
    }

    public void configureJurisdiction(String countryCode) {
        this.countryCode = (countryCode == null || countryCode.isBlank()) ? "IN" : countryCode.trim().toUpperCase();
        this.legalJurisdiction = resolveJurisdiction(this.countryCode);
        if ("INDIA_DPDP".equals(this.legalJurisdiction)) {
            this.grievanceOfficerEmail = "grievance@rememberflow.com";
            this.dpoEmail = null;
        } else if ("EU_GDPR".equals(this.legalJurisdiction) || "UK_GDPR".equals(this.legalJurisdiction)) {
            this.dpoEmail = "dpo@rememberflow.com";
            this.grievanceOfficerEmail = null;
        } else {
            this.dpoEmail = "privacy@rememberflow.com";
            this.grievanceOfficerEmail = null;
        }
    }

    public void recordLegalAcceptance(String version) {
        this.acceptedLegalVersion = version;
        this.acceptedLegalAt = Instant.now();
    }

    public String getCountryCode() {
        return countryCode;
    }

    public String getLegalJurisdiction() {
        return legalJurisdiction;
    }

    public String getGrievanceOfficerEmail() {
        return grievanceOfficerEmail;
    }

    public String getDpoEmail() {
        return dpoEmail;
    }

    public String getAcceptedLegalVersion() {
        return acceptedLegalVersion;
    }

    public Instant getAcceptedLegalAt() {
        return acceptedLegalAt;
    }
}
