package com.gymflow.repository;

import java.math.BigDecimal;

public interface MembershipSalesAggregate {
    Long getMemberships();
    Long getNewMembers();
    Long getRenewals();
    Long getRenewingMembers();
    BigDecimal getRevenue();
    BigDecimal getNewRevenue();
    BigDecimal getRenewalRevenue();
    BigDecimal getListPriceValue();
    BigDecimal getDiscountsGiven();
    BigDecimal getRecordedPriceVariance();
    BigDecimal getAverageSale();
    BigDecimal getAverageRenewalSale();
}
