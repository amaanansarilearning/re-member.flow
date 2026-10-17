package com.gymflow.web;

import com.gymflow.domain.Role;
import com.gymflow.service.GymService;
import com.gymflow.service.MembershipOperationsService;
import com.gymflow.service.ActionHistoryService;
import com.gymflow.service.RenewalReminderService;
import com.gymflow.service.ReportService;
import com.gymflow.service.AnalyticsService;
import static com.gymflow.web.ApiDtos.*;
import jakarta.validation.Valid;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class GymController {
    private final GymService gymService;
    private final MembershipOperationsService membershipOperations;
    private final ActionHistoryService actionHistory;
    private final RenewalReminderService renewalReminders;
    private final ReportService reports;
    private final AnalyticsService analytics;
    private final CookieCsrfTokenRepository csrfTokens;
    private final com.gymflow.service.SubscriptionService subscriptionService;

    public GymController(GymService gymService, MembershipOperationsService membershipOperations,
            ActionHistoryService actionHistory, RenewalReminderService renewalReminders, ReportService reports,
            AnalyticsService analytics, CookieCsrfTokenRepository csrfTokens,
            com.gymflow.service.SubscriptionService subscriptionService) {
        this.gymService = gymService;
        this.membershipOperations = membershipOperations;
        this.actionHistory = actionHistory;
        this.renewalReminders = renewalReminders;
        this.reports = reports;
        this.analytics = analytics;
        this.csrfTokens = csrfTokens;
        this.subscriptionService = subscriptionService;
    }

    @GetMapping("/plans")
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF', 'MEMBER', 'PERSONAL_TRAINER')")
    public List<PlanDto> plans(Principal principal) {
        return gymService.plans(principal.getName(), currentRole());
    }

    @GetMapping("/csrf")
    public java.util.Map<String, String> csrfToken(jakarta.servlet.http.HttpServletRequest request,
            jakarta.servlet.http.HttpServletResponse response) {
        var token = csrfTokens.generateToken(request);
        csrfTokens.saveToken(token, request, response);
        return java.util.Map.of("token", token.getToken());
    }

    @PostMapping("/plans")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('OWNER')")
    public PlanDto createPlan(Principal principal, @Valid @RequestBody PlanRequest request) {
        return gymService.createPlan(principal.getName(), request);
    }

    @PutMapping("/plans/{id}")
    @PreAuthorize("hasRole('OWNER')")
    public PlanDto updatePlan(Principal principal, @PathVariable long id, @Valid @RequestBody PlanRequest request) {
        return gymService.updatePlan(principal.getName(), id, request);
    }

    @GetMapping("/branches")
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF', 'PERSONAL_TRAINER')")
    public List<BranchDto> branches(Principal principal) {
        return gymService.branches(principal.getName(), currentRole());
    }

    @PostMapping("/branches")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('OWNER')")
    public BranchDto createBranch(Principal principal, @Valid @RequestBody BranchRequest request) {
        return gymService.createBranch(principal.getName(), request);
    }

    @PutMapping("/branches/{id}")
    @PreAuthorize("hasRole('OWNER')")
    public BranchDto updateBranch(Principal principal, @PathVariable long id,
            @Valid @RequestBody BranchRequest request) {
        return gymService.updateBranch(principal.getName(), id, request);
    }

    @GetMapping("/staff")
    @PreAuthorize("hasRole('OWNER')")
    public List<UserDto> staff(Principal principal) {
        return gymService.staff(principal.getName());
    }

    @PostMapping("/staff")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('OWNER')")
    public UserDto createStaff(Principal principal, @Valid @RequestBody StaffRegistrationRequest request) {
        return gymService.createStaff(principal.getName(), request);
    }

    @DeleteMapping("/staff/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('OWNER')")
    public void deleteStaff(Principal principal, @PathVariable long id) {
        gymService.deleteStaff(principal.getName(), id);
    }

    @GetMapping("/memberships")
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF', 'MEMBER', 'PERSONAL_TRAINER')")
    public List<MembershipDto> memberships(Principal principal) {
        return gymService.memberships(principal.getName(), currentRole());
    }

    @GetMapping("/memberships/recent")
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
    public List<MembershipDto> recentMemberships(Principal principal,
            @RequestParam(required = false) Long branchId) {
        return gymService.recentMemberships(principal.getName(), branchId);
    }

    @GetMapping("/memberships/page")
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF', 'MEMBER', 'PERSONAL_TRAINER')")
    public MembershipPageDto membershipPage(Principal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "all") String filter) {
        return gymService.membershipPage(principal.getName(), currentRole(), page, size, search, filter);
    }

    @PostMapping("/memberships")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
    public MembershipDto register(Principal principal, @Valid @RequestBody MembershipRegistrationRequest request) {
        return gymService.register(principal.getName(), currentRole(), request);
    }

    @PostMapping("/memberships/{id}/renew")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
    public MembershipDto renew(Principal principal, @PathVariable long id,
            @Valid @RequestBody MembershipRenewalRequest request) {
        return gymService.renew(principal.getName(), currentRole(), id, request);
    }

    @GetMapping("/memberships/expiring")
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
    public List<MembershipDto> expiring(Principal principal, @RequestParam(defaultValue = "7") int withinDays) {
        return gymService.expiring(principal.getName(), currentRole(), withinDays);
    }

    @PostMapping("/memberships/{id}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
    public void cancel(Principal principal, @PathVariable long id, @Valid @RequestBody CancellationRequest request) {
        gymService.cancel(principal.getName(), currentRole(), id, request);
    }

    @PostMapping("/memberships/{id}/cancel-renewal")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
    public void cancelRenewal(Principal principal, @PathVariable long id,
            @Valid @RequestBody CancellationRequest request) {
        gymService.cancelScheduledRenewal(principal.getName(), currentRole(), id, request);
    }

    @PostMapping("/memberships/{id}/freeze")
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
    public MembershipDto freeze(Principal principal, @PathVariable long id,
            @Valid @RequestBody MembershipFreezeRequest request) {
        return membershipOperations.freeze(principal.getName(), currentRole(), id, request);
    }

    @PostMapping("/memberships/{id}/freezes/{freezeId}/cancel")
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
    public MembershipDto cancelFreeze(Principal principal, @PathVariable long id, @PathVariable long freezeId,
            @Valid @RequestBody MembershipFreezeChangeRequest request) {
        return membershipOperations.cancelFreeze(principal.getName(), currentRole(), id, freezeId, request);
    }

    @PostMapping("/memberships/{id}/freezes/{freezeId}/end-early")
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
    public MembershipDto endFreezeEarly(Principal principal, @PathVariable long id, @PathVariable long freezeId,
            @Valid @RequestBody MembershipFreezeChangeRequest request) {
        return membershipOperations.endFreezeEarly(principal.getName(), currentRole(), id, freezeId, request);
    }

    @GetMapping("/memberships/{id}/access")
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF', 'MEMBER', 'PERSONAL_TRAINER')")
    public MembershipAccessDto membershipAccess(Principal principal, @PathVariable long id) {
        return membershipOperations.access(principal.getName(), currentRole(), id);
    }

    @PutMapping("/memberships/my-pin")
    @PreAuthorize("hasRole('MEMBER')")
    public void updateMyPin(Principal principal, @Valid @RequestBody UpdatePinRequest request) {
        gymService.updateMemberPin(principal.getName(), request.pin());
    }

    @PostMapping("/memberships/{id}/transfer")
    @PreAuthorize("hasRole('OWNER')")
    public MembershipDto transfer(Principal principal, @PathVariable long id,
            @Valid @RequestBody MembershipTransferRequest request) {
        return membershipOperations.transfer(principal.getName(), id, request);
    }

    @PutMapping("/memberships/{id}/correction")
    @PreAuthorize("hasRole('OWNER')")
    public MembershipDto correct(Principal principal, @PathVariable long id,
            @Valid @RequestBody MembershipCorrectionRequest request) {
        return membershipOperations.correct(principal.getName(), id, request);
    }

    @GetMapping("/history")
    @PreAuthorize("hasRole('OWNER')")
    public List<ActionHistoryDto> history(Principal principal) {
        subscriptionService.requireFeature(principal.getName(), com.gymflow.domain.AppFeature.ACTION_HISTORY);
        return actionHistory.list(gymService.organizationId(principal.getName()));
    }

    @GetMapping("/renewal-reminders")
    @PreAuthorize("hasRole('OWNER')")
    public List<RenewalReminderDto> renewalReminders(Principal principal) {
        return renewalReminders.list(gymService.organizationId(principal.getName()));
    }

    @GetMapping("/reports")
    @PreAuthorize("hasRole('OWNER')")
    public ReportDto report(Principal principal, @RequestParam LocalDate from, @RequestParam LocalDate to,
            @RequestParam(required = false) Long branchId) {
        return reports.report(principal.getName(), from, to, branchId);
    }

    @GetMapping("/analytics")
    @PreAuthorize("hasRole('OWNER')")
    public AnalyticsDto analytics(Principal principal, @RequestParam LocalDate from, @RequestParam LocalDate to,
            @RequestParam(required = false) Long branchId) {
        return analytics.analytics(principal.getName(), from, to, branchId);
    }

    @GetMapping(value = "/reports/export.csv", produces = "text/csv")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<String> exportReport(Principal principal, @RequestParam LocalDate from,
            @RequestParam LocalDate to, @RequestParam String type,
            @RequestParam(required = false) Long branchId) {
        String csv = reports.export(principal.getName(), from, to, branchId, type);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=gym-" + type + "-report.csv")
                .body(csv);
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('OWNER')")
    public DashboardDto dashboard(Principal principal) {
        return gymService.dashboard(principal.getName());
    }

    private Role currentRole() {
        String authority = SecurityContextHolder.getContext().getAuthentication().getAuthorities()
                .iterator().next().getAuthority();
        return Role.valueOf(authority.substring("ROLE_".length()));
    }
}