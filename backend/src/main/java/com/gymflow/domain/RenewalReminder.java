package com.gymflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(name = "renewal_reminders", uniqueConstraints = @UniqueConstraint(
        name = "uk_renewal_reminder_membership_notice_recipient",
        columnNames = { "membership_id", "days_before", "recipient_email" }),
        indexes = @Index(name = "idx_renewal_reminders_status_retry", columnList = "status, next_attempt_at"))
public class RenewalReminder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "membership_id", nullable = false)
    private Membership membership;

    @Column(name = "days_before", nullable = false)
    private int daysBefore;

    @Column(name = "recipient_email", nullable = false, length = 254)
    private String recipientEmail;

    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt = Instant.now();

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    protected RenewalReminder() {
    }

    public RenewalReminder(Membership membership, int daysBefore, String recipientEmail) {
        this.membership = membership;
        this.daysBefore = daysBefore;
        this.recipientEmail = recipientEmail;
    }

    public void markSent(Instant now) {
        this.status = "SENT";
        this.attempts++;
        this.lastAttemptAt = now;
        this.sentAt = now;
        this.lastError = null;
    }

    public void markFailed(Instant now, Instant nextAttemptAt, String error) {
        this.status = "FAILED";
        this.attempts++;
        this.lastAttemptAt = now;
        this.nextAttemptAt = nextAttemptAt;
        this.lastError = error.length() > 1000 ? error.substring(0, 1000) : error;
    }

    public void suppress() {
        this.status = "SUPPRESSED";
        this.lastError = null;
    }

    public Long getId() { return id; }
    public Membership getMembership() { return membership; }
    public String getRecipientEmail() { return recipientEmail; }
    public int getDaysBefore() { return daysBefore; }
    public String getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public Instant getLastAttemptAt() { return lastAttemptAt; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public Instant getSentAt() { return sentAt; }
    public String getLastError() { return lastError; }
}
