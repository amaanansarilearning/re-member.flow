package com.gymflow.service;

import static com.gymflow.web.ApiDtos.*;

import com.gymflow.domain.GymOrganization;
import com.gymflow.domain.MembershipStatus;
import com.gymflow.domain.UserAccount;
import com.gymflow.repository.BranchRepository;
import com.gymflow.repository.MemberRenewalAggregate;
import com.gymflow.repository.MembershipPackageAggregate;
import com.gymflow.repository.MembershipRepository;
import com.gymflow.repository.MembershipSalesAggregate;
import com.gymflow.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AnalyticsService {
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final UserRepository users;
    private final BranchRepository branches;
    private final MembershipRepository memberships;
    private final ZoneId businessZone;
    private final BigDecimal cgstRate;
    private final BigDecimal sgstRate;
    private final BigDecimal igstRate;
    private final boolean interstate;

    public AnalyticsService(UserRepository users, BranchRepository branches, MembershipRepository memberships,
            @Value("${gym.business-zone:Asia/Kolkata}") String businessZone,
            @Value("${gym.tax.cgst-rate:9.00}") BigDecimal cgstRate,
            @Value("${gym.tax.sgst-rate:9.00}") BigDecimal sgstRate,
            @Value("${gym.tax.igst-rate:18.00}") BigDecimal igstRate,
            @Value("${gym.tax.interstate:false}") boolean interstate) {
        this.users = users;
        this.branches = branches;
        this.memberships = memberships;
        this.businessZone = ZoneId.of(businessZone);
        this.cgstRate = validateRate(cgstRate);
        this.sgstRate = validateRate(sgstRate);
        this.igstRate = validateRate(igstRate);
        this.interstate = interstate;
    }

    @Transactional(readOnly = true)
    public AnalyticsDto analytics(String email, LocalDate from, LocalDate to, Long branchId) {
        if (from == null || to == null || to.isBefore(from) || to.isAfter(from.plusDays(365))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Choose a valid date range of at most 366 days");
        }
        UserAccount actor = users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account not found"));
        GymOrganization organization = actor.getOrganization();
        if (organization == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Account is not assigned to a gym organization");
        }
        if (branchId != null && branches.findByIdAndOrganizationId(branchId, organization.getId()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Branch not found");
        }

        long periodDays = java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1;
        LocalDate previousFrom = from.minusDays(periodDays);
        Instant fromInstant = startOfDay(from);
        Instant toExclusive = startOfDay(to.plusDays(1));
        MembershipSalesAggregate current = sales(organization.getId(), branchId, fromInstant, toExclusive);
        MembershipSalesAggregate previous = sales(organization.getId(), branchId,
                startOfDay(previousFrom), fromInstant);

        long openingMembers = memberships.countActiveMembersAsOf(organization.getId(), branchId,
                from.minusDays(1), fromInstant, MembershipStatus.ACTIVE);
        long activeMemberships = memberships.countActiveMembershipsAsOf(organization.getId(), branchId,
                to, toExclusive, MembershipStatus.ACTIVE);
        long activeMembers = memberships.countActiveMembersAsOf(organization.getId(), branchId,
                to, toExclusive, MembershipStatus.ACTIVE);
        long endedMemberships = memberships.countEndedTerms(organization.getId(), branchId, fromInstant,
                toExclusive, from, to);
        long membersLeft = memberships.countMembersLeft(organization.getId(), branchId, fromInstant,
                toExclusive, from, to, from.minusDays(1), MembershipStatus.ACTIVE);

        BigDecimal revenue = money(current.getRevenue());
        TaxAnalyticsDto tax = estimateTax(revenue);
        List<PackageAnalyticsDto> packagePerformance = memberships.packagePerformance(
                        organization.getId(), branchId, fromInstant, toExclusive).stream()
                .sorted(Comparator.comparing((MembershipPackageAggregate item) -> money(item.getRevenue()))
                        .reversed().thenComparing(item -> value(item.getMemberships()), Comparator.reverseOrder()))
                .map(item -> new PackageAnalyticsDto(item.getPackageId(), item.getPackageName(),
                        value(item.getMemberships()), value(item.getNewMembers()), value(item.getRenewals()),
                        money(item.getRevenue()), ratioMoney(item.getRevenue(), revenue),
                        money(item.getAverageSale()), money(item.getDiscountsGiven()),
                        money(item.getRecordedPriceVariance()),
                        money(item.getAverageDiscountPercent())))
                .toList();
        List<RenewingMemberDto> renewingMembers = memberships.renewalMembers(
                        organization.getId(), branchId, fromInstant, toExclusive, PageRequest.of(0, 100)).stream()
                .map(this::renewalMember)
                .toList();
        long newMembers = value(current.getNewMembers());
        long totalMemberships = value(current.getMemberships());
        BigDecimal renewalRevenue = money(current.getRenewalRevenue());
        return new AnalyticsDto(from, to, value(current.getMemberships()), value(current.getNewMembers()),
                value(current.getRenewals()), value(current.getRenewingMembers()), activeMemberships,
                activeMembers, endedMemberships, membersLeft, openingMembers,
                ratio(Math.max(0, openingMembers - membersLeft), openingMembers), ratio(membersLeft, openingMembers),
                growthDifference(activeMembers, openingMembers),
                growth(newMembers, value(previous.getNewMembers())),
                revenue, money(current.getNewRevenue()), renewalRevenue,
                money(current.getAverageSale()), money(current.getAverageRenewalSale()),
                money(current.getListPriceValue()), money(current.getDiscountsGiven()),
                money(current.getRecordedPriceVariance()),
                ratioMoney(renewalRevenue, revenue), growthMoney(current.getRevenue(), previous.getRevenue()),
                tax, trend(organization.getId(), branchId, from, to),
                packagePerformance, renewingMembers, true);
    }

    private MembershipSalesAggregate sales(Long organizationId, Long branchId, Instant from, Instant to) {
        return memberships.salesAggregate(organizationId, branchId, from, to);
    }

    private List<AnalyticsPointDto> trend(Long organizationId, Long branchId,
            LocalDate dateFrom, LocalDate dateTo) {
        LocalDate month = dateFrom.withDayOfMonth(1);
        LocalDate lastMonth = dateTo.withDayOfMonth(1);
        java.util.ArrayList<AnalyticsPointDto> points = new java.util.ArrayList<>();
        while (!month.isAfter(lastMonth)) {
            LocalDate bucketFrom = month.isBefore(dateFrom) ? dateFrom : month;
            LocalDate nextMonth = month.plusMonths(1);
            LocalDate bucketTo = nextMonth.isAfter(dateTo.plusDays(1)) ? dateTo.plusDays(1) : nextMonth;
            MembershipSalesAggregate item = sales(organizationId, branchId,
                    startOfDay(bucketFrom), startOfDay(bucketTo));
            points.add(new AnalyticsPointDto(month, value(item.getMemberships()),
                    value(item.getNewMembers()), value(item.getRenewals()), money(item.getRevenue()),
                    money(item.getNewRevenue()), money(item.getRenewalRevenue()), money(item.getDiscountsGiven()),
                    money(item.getRecordedPriceVariance())));
            month = month.plusMonths(1);
        }
        return List.copyOf(points);
    }

    private RenewingMemberDto renewalMember(MemberRenewalAggregate member) {
        long years = value(member.getRenewalYears());
        Integer firstYear = member.getFirstRenewalYear();
        Integer lastYear = member.getLastRenewalYear();
        boolean renewedEveryObservedYear = years >= 2 && firstYear != null && lastYear != null
                && years == (long) lastYear - firstYear + 1;
        return new RenewingMemberDto(member.getMemberId(), member.getMemberName(), member.getMemberEmail(),
                value(member.getTerms()), value(member.getRenewals()), years, firstYear, lastYear,
                renewedEveryObservedYear, money(member.getLifetimeRevenue()), member.getLatestRenewalInPeriod());
    }

    private TaxAnalyticsDto estimateTax(BigDecimal grossRevenue) {
        BigDecimal totalRate = interstate ? igstRate : cgstRate.add(sgstRate);
        BigDecimal taxable = totalRate.signum() == 0 ? grossRevenue
                : grossRevenue.multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(100).add(totalRate), 2, RoundingMode.HALF_UP);
        BigDecimal gst = grossRevenue.subtract(taxable).setScale(2, RoundingMode.HALF_UP);
        BigDecimal cgst = interstate ? ZERO : proportional(gst, cgstRate, totalRate);
        BigDecimal sgst = interstate ? ZERO : gst.subtract(cgst).setScale(2, RoundingMode.HALF_UP);
        BigDecimal igst = interstate ? gst : ZERO;
        return new TaxAnalyticsDto(grossRevenue, taxable, gst, cgst, sgst, igst, taxable, gst,
                cgstRate, sgstRate, igstRate, true);
    }

    private BigDecimal proportional(BigDecimal tax, BigDecimal componentRate, BigDecimal totalRate) {
        return totalRate.signum() == 0 ? ZERO
                : tax.multiply(componentRate).divide(totalRate, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal ratio(long numerator, long denominator) {
        return denominator == 0 ? ZERO : BigDecimal.valueOf(numerator).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal ratioMoney(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.signum() == 0 || numerator == null) {
            return ZERO;
        }
        return numerator.multiply(BigDecimal.valueOf(100)).divide(denominator, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal growthDifference(long current, long previous) {
        return previous == 0 ? (current == 0 ? ZERO : null)
                : BigDecimal.valueOf(current - previous).multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(previous), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal growth(long current, long previous) {
        return previous == 0 ? (current == 0 ? ZERO : null)
                : BigDecimal.valueOf(current - previous).multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(previous), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal growthMoney(BigDecimal current, BigDecimal previous) {
        BigDecimal old = previous == null ? BigDecimal.ZERO : previous;
        if (old.signum() == 0) {
            return current == null || current.signum() == 0 ? ZERO : null;
        }
        return current.subtract(old).multiply(BigDecimal.valueOf(100))
                .divide(old, 2, RoundingMode.HALF_UP);
    }

    private Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(businessZone).toInstant();
    }

    private long value(Long value) {
        return value == null ? 0 : value;
    }

    private BigDecimal money(BigDecimal value) {
        return value == null ? ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal validateRate(BigDecimal rate) {
        if (rate == null || rate.signum() < 0 || rate.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("Configured GST rates must be between 0 and 100");
        }
        return rate.setScale(2, RoundingMode.HALF_UP);
    }
}
