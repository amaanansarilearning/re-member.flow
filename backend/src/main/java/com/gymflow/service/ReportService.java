package com.gymflow.service;

import com.gymflow.domain.GymOrganization;
import com.gymflow.domain.Membership;
import com.gymflow.domain.UserAccount;
import com.gymflow.repository.BranchRepository;
import com.gymflow.repository.MembershipRepository;
import com.gymflow.repository.UserRepository;
import com.gymflow.web.ApiDtos.PackagePerformanceDto;
import com.gymflow.web.ApiDtos.ReportDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReportService {
    private final UserRepository users;
    private final MembershipRepository memberships;
    private final BranchRepository branches;
    private final ZoneId businessZone;

    public ReportService(UserRepository users, MembershipRepository memberships, BranchRepository branches,
            @Value("${gym.business-zone:Asia/Kolkata}") String businessZone) {
        this.users = users;
        this.memberships = memberships;
        this.branches = branches;
        this.businessZone = ZoneId.of(businessZone);
    }

    @Transactional(readOnly = true)
    public ReportDto report(String email, LocalDate from, LocalDate to, Long branchId) {
        ReportScope scope = reportScope(email, from, to, branchId);
        List<Membership> created = createdMemberships(scope, from, to);
        List<Membership> cancelled = cancelledMemberships(scope, from, to);
        Map<String, PackageAccumulator> grouped = new LinkedHashMap<>();
        for (Membership membership : created) {
            String key = membership.getPlan().getId() + "\u0000" + membership.getPlanNameSnapshot();
            PackageAccumulator item = grouped.computeIfAbsent(key,
                    ignored -> new PackageAccumulator(membership.getPlan().getId(),
                            membership.getPlanNameSnapshot()));
            item.count++;
            item.revenue = item.revenue.add(membership.getAmountPaid());
        }
        List<PackagePerformanceDto> packageRows = grouped.values().stream()
                .sorted(Comparator.comparing((PackageAccumulator item) -> item.revenue).reversed())
                .map(item -> new PackagePerformanceDto(item.id, item.name, item.count, item.revenue))
                .toList();
        BigDecimal revenue = created.stream().map(Membership::getAmountPaid)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ReportDto(from, to, created.size(), cancelled.size(), revenue, packageRows);
    }

    @Transactional(readOnly = true)
    public String export(String email, LocalDate from, LocalDate to, Long branchId, String type) {
        ReportScope scope = reportScope(email, from, to, branchId);
        StringBuilder csv = new StringBuilder();
        switch (type.toLowerCase(Locale.ROOT)) {
            case "memberships", "revenue" -> {
                csv.append("membership_id,member_name,email,package,branch,start_date,end_date,status,list_price,discount_percent,amount_paid,created_at\n");
                createdMemberships(scope, from, to).forEach(item -> row(csv, item.getId(), item.getMember().getFullName(),
                        item.getMember().getEmail(), item.getPlanNameSnapshot(),
                        item.getBranch() == null ? "" : item.getBranch().getName(),
                        item.getStartDate(), item.getEndDate(), item.getStatus(),
                        item.getListPrice(), item.getDiscountPercent(), item.getAmountPaid(), item.getCreatedAt()));
            }
            case "cancellations" -> {
                csv.append("membership_id,member_name,email,package,branch,cancelled_at,amount_paid\n");
                cancelledMemberships(scope, from, to).forEach(item -> row(csv, item.getId(), item.getMember().getFullName(),
                                item.getMember().getEmail(), item.getPlanNameSnapshot(),
                                item.getBranch() == null ? "" : item.getBranch().getName(),
                                item.getCancelledAt(), item.getAmountPaid()));
            }
            case "packages" -> {
                csv.append("package_id,package_name,memberships,revenue\n");
                report(email, from, to, branchId).packages().forEach(item ->
                        row(csv, item.packageId(), item.packageName(), item.memberships(), item.revenue()));
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "type must be memberships, revenue, cancellations, or packages");
        }
        return csv.toString();
    }

    private ReportScope reportScope(String email, LocalDate from, LocalDate to, Long branchId) {
        if (from == null || to == null || to.isBefore(from) || to.isAfter(from.plusDays(365))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Choose a valid date range of at most 366 days");
        }
        UserAccount actor = users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account not found"));
        GymOrganization organization = actor.getOrganization();
        if (organization == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Account is not assigned to a gym organization");
        }
        if (branchId != null && branches.findByIdAndOrganizationId(branchId, organization.getId()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Branch not found");
        }
        return new ReportScope(organization.getId(), branchId);
    }

    private List<Membership> createdMemberships(ReportScope scope, LocalDate from, LocalDate to) {
        return memberships.findCreatedForReport(scope.organizationId(),
                from.atStartOfDay(businessZone).toInstant(),
                to.plusDays(1).atStartOfDay(businessZone).toInstant(), scope.branchId());
    }

    private List<Membership> cancelledMemberships(ReportScope scope, LocalDate from, LocalDate to) {
        return memberships.findCancelledForReport(scope.organizationId(),
                from.atStartOfDay(businessZone).toInstant(),
                to.plusDays(1).atStartOfDay(businessZone).toInstant(), scope.branchId());
    }

    private void row(StringBuilder csv, Object... cells) {
        for (int index = 0; index < cells.length; index++) {
            if (index > 0) {
                csv.append(',');
            }
            String value = cells[index] == null ? "" : cells[index].toString();
            String formulaCheck = value.stripLeading();
            if (!formulaCheck.isEmpty() && "=+-@".indexOf(formulaCheck.charAt(0)) >= 0) {
                value = "'" + value;
            }
            csv.append('"').append(value.replace("\"", "\"\"")).append('"');
        }
        csv.append('\n');
    }

    private static final class PackageAccumulator {
        private final Long id;
        private final String name;
        private long count;
        private BigDecimal revenue = BigDecimal.ZERO;

        private PackageAccumulator(Long id, String name) {
            this.id = id;
            this.name = name;
        }

    }

    private record ReportScope(Long organizationId, Long branchId) {
    }
}
