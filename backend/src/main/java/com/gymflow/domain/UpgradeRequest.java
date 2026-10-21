package com.gymflow.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "subscription_upgrade_requests", indexes = {
    @Index(name = "idx_upg_org", columnList = "organization_id"),
    @Index(name = "idx_upg_status", columnList = "status")
})
public class UpgradeRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private GymOrganization organization;

    @Column(nullable = false, length = 120)
    private String requesterEmail;

    @Column(length = 120)
    private String requesterName;

    @Column(length = 30)
    private String requesterPhone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SubscriptionTier targetTier = SubscriptionTier.PREMIUM;

    @Column(nullable = false)
    private Integer durationDays; // e.g. 30, 90, 180, 365

    @Column(length = 100)
    private String billingPlanName; // e.g. "Annual PRO (365 Days)"

    @Column(length = 50)
    private String paymentMethod; // e.g. "BANK_WIRE", "UPI_QR", "CORPORATE_INVOICE", "CASH"

    @Column(length = 50)
    private String gstNumber;

    @Column(length = 255)
    private String billingAddress;

    @Column(length = 500)
    private String notes;

    @Column(precision = 10, scale = 2)
    private BigDecimal estimatedTopUp;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UpgradeRequestStatus status = UpgradeRequestStatus.PENDING;

    @Column(length = 64)
    private String issuedActivationCode;

    @Column(length = 120)
    private String reviewedBy;

    @Column
    private Instant reviewedAt;

    @Column(length = 255)
    private String rejectionReason;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected UpgradeRequest() {
    }

    public UpgradeRequest(GymOrganization organization,
                          String requesterEmail,
                          String requesterName,
                          String requesterPhone,
                          SubscriptionTier targetTier,
                          Integer durationDays,
                          String billingPlanName,
                          String paymentMethod,
                          String gstNumber,
                          String billingAddress,
                          String notes) {
        this.organization = organization;
        this.requesterEmail = requesterEmail != null ? requesterEmail.trim().toLowerCase() : null;
        this.requesterName = requesterName != null ? requesterName.trim() : null;
        this.requesterPhone = requesterPhone != null ? requesterPhone.trim() : null;
        this.targetTier = targetTier != null ? targetTier : SubscriptionTier.PREMIUM;
        this.durationDays = durationDays != null ? durationDays : 365;
        this.billingPlanName = billingPlanName != null ? billingPlanName.trim() : "Annual PRO (365 Days)";
        this.paymentMethod = paymentMethod != null ? paymentMethod.trim().toUpperCase() : "BANK_WIRE";
        this.gstNumber = gstNumber != null ? gstNumber.trim().toUpperCase() : null;
        this.billingAddress = billingAddress != null ? billingAddress.trim() : null;
        this.notes = notes != null ? notes.trim() : null;
        this.status = UpgradeRequestStatus.PENDING;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public GymOrganization getOrganization() {
        return organization;
    }

    public String getRequesterEmail() {
        return requesterEmail;
    }

    public String getRequesterName() {
        return requesterName;
    }

    public String getRequesterPhone() {
        return requesterPhone;
    }

    public SubscriptionTier getTargetTier() {
        return targetTier;
    }

    public Integer getDurationDays() {
        return durationDays;
    }

    public String getBillingPlanName() {
        return billingPlanName;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public String getGstNumber() {
        return gstNumber;
    }

    public String getBillingAddress() {
        return billingAddress;
    }

    public BigDecimal getEstimatedTopUp() {
        return estimatedTopUp;
    }

    public void setEstimatedTopUp(BigDecimal estimatedTopUp) {
        this.estimatedTopUp = estimatedTopUp;
    }

    public String getNotes() {
        return notes;
    }

    public UpgradeRequestStatus getStatus() {
        return status;
    }

    public String getIssuedActivationCode() {
        return issuedActivationCode;
    }

    public String getReviewedBy() {
        return reviewedBy;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void markApprovedWithKey(String code, String adminEmail) {
        this.status = UpgradeRequestStatus.APPROVED_KEY_ISSUED;
        this.issuedActivationCode = code != null ? code.trim().toUpperCase() : null;
        this.reviewedBy = adminEmail != null ? adminEmail.trim().toLowerCase() : "system";
        this.reviewedAt = Instant.now();
    }

    public void markApprovedDirect(String adminEmail) {
        this.status = UpgradeRequestStatus.APPROVED_DIRECT_UPGRADE;
        this.reviewedBy = adminEmail != null ? adminEmail.trim().toLowerCase() : "system";
        this.reviewedAt = Instant.now();
    }

    public void markRejected(String reason, String adminEmail) {
        this.status = UpgradeRequestStatus.REJECTED;
        this.rejectionReason = reason != null ? reason.trim() : "Rejected by Super-Admin";
        this.reviewedBy = adminEmail != null ? adminEmail.trim().toLowerCase() : "system";
        this.reviewedAt = Instant.now();
    }
}

