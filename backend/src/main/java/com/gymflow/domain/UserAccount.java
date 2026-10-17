package com.gymflow.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "user_accounts")
public class UserAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(length = 30)
    private String phoneNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id")
    private GymOrganization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private GymBranch branch;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false, columnDefinition = "integer default 0")
    private int tokenVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Role role;

    @Column(name = "member_pin", length = 8)
    private String memberPin;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected UserAccount() {
    }

    public UserAccount(String fullName, String email, String phoneNumber, String passwordHash, Role role,
            GymOrganization organization, GymBranch branch) {
        this.fullName = fullName;
        this.email = email.trim().toLowerCase();
        this.phoneNumber = phoneNumber == null ? null : phoneNumber.trim();
        this.passwordHash = passwordHash;
        this.role = role;
        this.organization = organization;
        this.branch = branch;
        if (role == Role.MEMBER) {
            this.memberPin = generateDefaultPin();
        }
    }

    public void updateProfile(String fullName, String email, String phoneNumber) {
        this.fullName = fullName.trim();
        this.email = email.trim().toLowerCase();
        this.phoneNumber = phoneNumber == null ? null : phoneNumber.trim();
    }

    public void invalidateTokens() {
        this.tokenVersion++;
    }

    public void updateMemberPin(String memberPin) {
        this.memberPin = memberPin != null ? memberPin.trim() : null;
    }

    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
        this.tokenVersion++;
    }

    public Long getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public GymOrganization getOrganization() {
        return organization;
    }

    public GymBranch getBranch() {
        return branch;
    }

    public void assignLocation(GymOrganization organization, GymBranch branch) {
        this.organization = organization;
        this.branch = branch;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public int getTokenVersion() {
        return tokenVersion;
    }

    public Role getRole() {
        return role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getMemberPin() {
        if (this.memberPin == null && this.role == Role.MEMBER) {
            this.memberPin = generateDefaultPin();
        }
        return this.memberPin;
    }

    public void setMemberPin(String memberPin) {
        this.memberPin = memberPin;
    }

    private static final java.security.SecureRandom SECURE_RANDOM = new java.security.SecureRandom();

    private static String generateDefaultPin() {
        int pinNum = 1000 + SECURE_RANDOM.nextInt(9000);
        return String.format("%04d", pinNum);
    }
}