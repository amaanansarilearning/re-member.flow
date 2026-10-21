package com.gymflow.domain;

import java.math.BigDecimal;

public enum SubscriptionTier {
    STANDARD,
    STARTER,
    PRO,
    ENTERPRISE,
    @Deprecated
    PREMIUM;

    public int maxBranches() {
        return switch (this) {
            case STANDARD, STARTER, PRO -> 1;
            case ENTERPRISE, PREMIUM -> Integer.MAX_VALUE;
        };
    }

    public int maxStaff() {
        return switch (this) {
            case STANDARD -> 2;
            case STARTER -> 5;
            case PRO -> 15;
            case ENTERPRISE, PREMIUM -> Integer.MAX_VALUE;
        };
    }

    public int maxMembers() {
        return switch (this) {
            case STANDARD -> 50;
            case STARTER -> 250;
            case PRO -> 1000;
            case ENTERPRISE, PREMIUM -> Integer.MAX_VALUE;
        };
    }

    public BigDecimal monthlyPrice() {
        return switch (this) {
            case STANDARD -> new BigDecimal("2000.00");
            case STARTER -> new BigDecimal("5000.00");
            case PRO -> new BigDecimal("12000.00");
            case ENTERPRISE, PREMIUM -> new BigDecimal("25000.00");
        };
    }
}
