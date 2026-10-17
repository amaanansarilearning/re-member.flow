package com.gymflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(name = "gym_branches", uniqueConstraints = @UniqueConstraint(name = "uk_gym_branch_org_code", columnNames = {
        "organization_id", "code" }))
public class GymBranch {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private GymOrganization organization;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 30)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private BranchType type;

    @Column(length = 500)
    private String address;

    @Column(length = 30)
    private String phoneNumber;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected GymBranch() {
    }

    public GymBranch(GymOrganization organization, String name, String code, BranchType type,
            String address, String phoneNumber) {
        this.organization = organization;
        this.name = name.trim();
        this.code = code.trim().toUpperCase();
        this.type = type;
        this.address = address == null ? null : address.trim();
        this.phoneNumber = phoneNumber == null ? null : phoneNumber.trim();
    }

    public void update(String name, String code, BranchType type, String address, String phoneNumber, boolean active) {
        this.name = name.trim();
        this.code = code.trim().toUpperCase();
        this.type = type;
        this.address = address == null ? null : address.trim();
        this.phoneNumber = phoneNumber == null ? null : phoneNumber.trim();
        this.active = active;
    }

    public Long getId() {
        return id;
    }

    public GymOrganization getOrganization() {
        return organization;
    }

    public String getName() {
        return name;
    }

    public String getCode() {
        return code;
    }

    public BranchType getType() {
        return type;
    }

    public String getAddress() {
        return address;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}