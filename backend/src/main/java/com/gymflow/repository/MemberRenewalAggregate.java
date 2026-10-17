package com.gymflow.repository;

import java.math.BigDecimal;
import java.time.Instant;

public interface MemberRenewalAggregate {
    Long getMemberId();
    String getMemberName();
    String getMemberEmail();
    Long getTerms();
    Long getRenewals();
    Long getRenewalYears();
    Integer getFirstRenewalYear();
    Integer getLastRenewalYear();
    BigDecimal getLifetimeRevenue();
    Instant getLatestRenewalInPeriod();
}
