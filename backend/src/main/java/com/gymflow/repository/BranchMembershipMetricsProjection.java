package com.gymflow.repository;

import java.math.BigDecimal;

public interface BranchMembershipMetricsProjection {
    Long getBranchId();

    BigDecimal getRevenueThisMonth();

    BigDecimal getRevenueLastMonth();

    Long getActiveMembers();

    Long getTotalMembers();
}
