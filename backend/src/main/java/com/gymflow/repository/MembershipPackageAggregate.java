package com.gymflow.repository;

import java.math.BigDecimal;

public interface MembershipPackageAggregate {
    Long getPackageId();
    String getPackageName();
    Long getMemberships();
    Long getNewMembers();
    Long getRenewals();
    BigDecimal getRevenue();
    BigDecimal getListPriceValue();
    BigDecimal getDiscountsGiven();
    BigDecimal getRecordedPriceVariance();
    BigDecimal getAverageSale();
    BigDecimal getAverageDiscountPercent();
}
