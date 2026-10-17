package com.gymflow.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "membership_plans")
public class MembershipPlan {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column
    private Integer durationMonths;

    @Column
    private Integer durationDays;

    @Column(nullable = false)
    private boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id")
    private GymOrganization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private GymBranch branch;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PtPackageType ptPackageType = PtPackageType.NONE;

    @Column
    private Integer includedPtSessions;

    // Amount of the plan price attributable to PT (for revenue split)
    @Column(precision = 10, scale = 2)
    private BigDecimal ptFeeIncluded;

    protected MembershipPlan() {
    }

    public MembershipPlan(String name, String description, BigDecimal price, int durationMonths) {
        this.name = name;
        this.description = description;
        this.price = price;
        this.durationMonths = durationMonths;
        this.active = true;
    }

    public void assignLocation(GymOrganization organization, GymBranch branch) {
        this.organization = organization;
        this.branch = branch;
    }

    public static MembershipPlan forDays(String name, String description, BigDecimal price, int durationDays) {
        MembershipPlan plan = new MembershipPlan();
        plan.name = name;
        plan.description = description;
        plan.price = price;
        plan.durationDays = durationDays;
        plan.active = true;
        return plan;
    }

    public void update(String name, String description, BigDecimal price, int durationMonths, boolean active) {
        this.name = name;
        this.description = description;
        this.price = price;
        this.durationMonths = durationMonths;
        this.active = active;
    }

    public void updateDuration(String name, String description, BigDecimal price, Integer durationMonths,
            Integer durationDays, boolean active) {
        this.name = name;
        this.description = description;
        this.price = price;
        this.durationMonths = durationMonths;
        this.durationDays = durationDays;
        this.active = active;
    }

    public void updatePt(PtPackageType ptPackageType, Integer includedPtSessions, BigDecimal ptFeeIncluded) {
        this.ptPackageType = ptPackageType == null ? PtPackageType.NONE : ptPackageType;
        this.includedPtSessions = includedPtSessions;
        this.ptFeeIncluded = ptFeeIncluded;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public Integer getDurationMonths() {
        return durationMonths;
    }

    public Integer getDurationDays() {
        return durationDays;
    }

    public boolean isActive() {
        return active;
    }

    public GymOrganization getOrganization() {
        return organization;
    }

    public GymBranch getBranch() {
        return branch;
    }

    public PtPackageType getPtPackageType() {
        return ptPackageType;
    }

    public void setPtPackageType(PtPackageType ptPackageType) {
        this.ptPackageType = ptPackageType == null ? PtPackageType.NONE : ptPackageType;
    }

    public Integer getIncludedPtSessions() {
        return includedPtSessions;
    }

    public void setIncludedPtSessions(Integer includedPtSessions) {
        this.includedPtSessions = includedPtSessions;
    }

    public BigDecimal getPtFeeIncluded() {
        return ptFeeIncluded;
    }

    public void setPtFeeIncluded(BigDecimal ptFeeIncluded) {
        this.ptFeeIncluded = ptFeeIncluded;
    }

}