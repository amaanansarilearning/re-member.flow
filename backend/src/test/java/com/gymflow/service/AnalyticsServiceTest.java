package com.gymflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gymflow.domain.GymOrganization;
import com.gymflow.domain.Role;
import com.gymflow.domain.UserAccount;
import com.gymflow.repository.BranchRepository;
import com.gymflow.repository.MembershipRepository;
import com.gymflow.repository.MembershipSalesAggregate;
import com.gymflow.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AnalyticsServiceTest {
    @Test
    void computesDistinctMemberRetentionGrowthAndTaxInclusiveRevenueEstimate() {
        UserRepository users = mock(UserRepository.class);
        BranchRepository branches = mock(BranchRepository.class);
        MembershipRepository memberships = mock(MembershipRepository.class);
        GymOrganization organization = mock(GymOrganization.class);
        when(organization.getId()).thenReturn(15L);
        UserAccount owner = new UserAccount("Owner", "owner@example.test", null, "hash",
                Role.OWNER, organization, null);
        when(users.findByEmailIgnoreCase(owner.getEmail())).thenReturn(Optional.of(owner));

        LocalDate from = LocalDate.of(2026, 7, 1);
        LocalDate to = LocalDate.of(2026, 7, 31);
        Instant currentFrom = from.atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant();
        Instant currentTo = to.plusDays(1).atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant();
        MembershipSalesAggregate current = sales(4, 3, 1, "1180.00", "800.00", "380.00");
        MembershipSalesAggregate previous = sales(2, 2, 0, "1000.00", "1000.00", "0.00");
        when(memberships.salesAggregate(15L, null, currentFrom, currentTo)).thenReturn(current);
        when(memberships.salesAggregate(15L, null, currentFrom.minusSeconds(31L * 24 * 60 * 60), currentFrom))
                .thenReturn(previous);
        when(memberships.countActiveMembersAsOf(15L, null, from.minusDays(1), currentFrom,
                com.gymflow.domain.MembershipStatus.ACTIVE)).thenReturn(5L);
        when(memberships.countActiveMembersAsOf(15L, null, to, currentTo,
                com.gymflow.domain.MembershipStatus.ACTIVE)).thenReturn(7L);
        when(memberships.countActiveMembershipsAsOf(15L, null, to, currentTo,
                com.gymflow.domain.MembershipStatus.ACTIVE)).thenReturn(4L);
        when(memberships.countEndedTerms(15L, null, currentFrom, currentTo, from, to)).thenReturn(2L);
        when(memberships.countMembersLeft(15L, null, currentFrom, currentTo, from, to, from.minusDays(1),
                com.gymflow.domain.MembershipStatus.ACTIVE)).thenReturn(1L);

        AnalyticsService service = new AnalyticsService(users, branches, memberships, "Asia/Kolkata",
                new BigDecimal("9"), new BigDecimal("9"), new BigDecimal("18"), false);
        var result = service.analytics(owner.getEmail(), from, to, null);

        assertThat(result.totalMembershipsSold()).isEqualTo(4);
        assertThat(result.newMembers()).isEqualTo(3);
        assertThat(result.membershipRenewals()).isEqualTo(1);
        assertThat(result.activeMemberships()).isEqualTo(4);
        assertThat(result.expiredOrCancelledMemberships()).isEqualTo(2);
        assertThat(result.membersLeft()).isEqualTo(1);
        assertThat(result.retentionRate()).isEqualByComparingTo("80.00");
        assertThat(result.churnRate()).isEqualByComparingTo("20.00");
        assertThat(result.membershipGrowthPercent()).isEqualByComparingTo("40.00");
        assertThat(result.newMemberSalesGrowthPercent()).isEqualByComparingTo("50.00");
        assertThat(result.revenueGrowthPercent()).isEqualByComparingTo("18.00");
        assertThat(result.totalRevenue()).isEqualByComparingTo("1180.00");
        assertThat(result.tax().estimatedTaxableSales()).isEqualByComparingTo("1000.00");
        assertThat(result.tax().estimatedGstIncluded()).isEqualByComparingTo("180.00");
        assertThat(result.tax().estimatedCgst()).isEqualByComparingTo("90.00");
        assertThat(result.tax().estimatedSgst()).isEqualByComparingTo("90.00");
        assertThat(result.tax().estimatedIgst()).isEqualByComparingTo("0.00");
        assertThat(result.tax().estimatedOutputGst()).isEqualByComparingTo("180.00");
    }

    @Test
    void returnsZeroRetentionAndChurnWhenThereIsNoOpeningCohort() {
        UserRepository users = mock(UserRepository.class);
        BranchRepository branches = mock(BranchRepository.class);
        MembershipRepository memberships = mock(MembershipRepository.class);
        GymOrganization organization = mock(GymOrganization.class);
        when(organization.getId()).thenReturn(2L);
        UserAccount owner = new UserAccount("Owner", "zero@example.test", null, "hash",
                Role.OWNER, organization, null);
        when(users.findByEmailIgnoreCase(owner.getEmail())).thenReturn(Optional.of(owner));
        LocalDate from = LocalDate.of(2026, 10, 1);
        LocalDate to = LocalDate.of(2026, 10, 1);
        Instant start = from.atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant();
        MembershipSalesAggregate emptySales = sales(0, 0, 0, "0", "0", "0");
        MembershipSalesAggregate previousSales = sales(0, 0, 0, "0", "0", "0");
        when(memberships.salesAggregate(2L, null, start, end)).thenReturn(emptySales);
        when(memberships.salesAggregate(2L, null, start.minusSeconds(86400), start))
                .thenReturn(previousSales);
        when(memberships.countActiveMembersAsOf(2L, null, from.minusDays(1), start,
                com.gymflow.domain.MembershipStatus.ACTIVE)).thenReturn(0L);
        when(memberships.countActiveMembershipsAsOf(2L, null, to, end,
                com.gymflow.domain.MembershipStatus.ACTIVE)).thenReturn(0L);
        when(memberships.countEndedTerms(2L, null, start, end, from, to)).thenReturn(0L);
        when(memberships.countMembersLeft(2L, null, start, end, from, to, from.minusDays(1),
                com.gymflow.domain.MembershipStatus.ACTIVE)).thenReturn(0L);

        var result = new AnalyticsService(users, branches, memberships, "Asia/Kolkata",
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, true)
                .analytics(owner.getEmail(), from, to, null);

        assertThat(result.retentionRate()).isEqualByComparingTo("0.00");
        assertThat(result.churnRate()).isEqualByComparingTo("0.00");
        assertThat(result.tax().estimatedTaxableSales()).isEqualByComparingTo("0.00");
        assertThat(result.tax().estimatedIgst()).isEqualByComparingTo("0.00");
    }

    private MembershipSalesAggregate sales(long memberships, long newMembers, long renewals,
            String revenue, String newRevenue, String renewalRevenue) {
        MembershipSalesAggregate aggregate = mock(MembershipSalesAggregate.class);
        when(aggregate.getMemberships()).thenReturn(memberships);
        when(aggregate.getNewMembers()).thenReturn(newMembers);
        when(aggregate.getRenewals()).thenReturn(renewals);
        when(aggregate.getRenewingMembers()).thenReturn(renewals == 0 ? 0L : 1L);
        when(aggregate.getRevenue()).thenReturn(new BigDecimal(revenue));
        when(aggregate.getNewRevenue()).thenReturn(new BigDecimal(newRevenue));
        when(aggregate.getRenewalRevenue()).thenReturn(new BigDecimal(renewalRevenue));
        when(aggregate.getListPriceValue()).thenReturn(new BigDecimal(revenue).add(new BigDecimal("100")));
        when(aggregate.getDiscountsGiven()).thenReturn(new BigDecimal("100"));
        when(aggregate.getRecordedPriceVariance()).thenReturn(BigDecimal.ZERO);
        when(aggregate.getAverageSale()).thenReturn(new BigDecimal(revenue).divide(
                BigDecimal.valueOf(Math.max(1, memberships)), 2, java.math.RoundingMode.HALF_UP));
        when(aggregate.getAverageRenewalSale()).thenReturn(new BigDecimal(renewalRevenue));
        return aggregate;
    }
}
