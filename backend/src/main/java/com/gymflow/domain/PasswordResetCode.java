package com.gymflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "password_reset_codes", indexes = @Index(name = "idx_reset_email_created", columnList = "email, created_at"))
public class PasswordResetCode {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(nullable = false, length = 100)
    private String codeHash;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant consumedAt;

    @Column(nullable = false)
    private int failedAttempts;

    protected PasswordResetCode() {
    }

    public PasswordResetCode(String email, String codeHash, Instant createdAt, Instant expiresAt) {
        this.email = email.toLowerCase();
        this.codeHash = codeHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public void recordFailedAttempt() {
        failedAttempts++;
        if (failedAttempts >= 5)
            consumedAt = Instant.now();
    }

    public void consume() {
        consumedAt = Instant.now();
    }

    public String getCodeHash() {
        return codeHash;
    }

    public Instant getConsumedAt() {
        return consumedAt;
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }
}