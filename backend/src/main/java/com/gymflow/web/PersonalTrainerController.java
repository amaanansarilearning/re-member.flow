package com.gymflow.web;

import com.gymflow.domain.Role;
import com.gymflow.service.PersonalTrainerService;
import com.gymflow.service.PtSchedulingService;
import static com.gymflow.web.ApiDtos.*;
import jakarta.validation.Valid;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import com.gymflow.domain.AppFeature;
import com.gymflow.service.SubscriptionService;

@RestController
@RequestMapping("/api/pt")
public class PersonalTrainerController {
    private final PersonalTrainerService ptService;
    private final PtSchedulingService scheduling;
    private final SubscriptionService subscriptionService;

    public PersonalTrainerController(PersonalTrainerService ptService, PtSchedulingService scheduling,
            SubscriptionService subscriptionService) {
        this.ptService = ptService;
        this.scheduling = scheduling;
        this.subscriptionService = subscriptionService;
    }

    @ModelAttribute
    public void checkPtFeature(Principal principal) {
        if (principal != null) {
            subscriptionService.requireFeature(principal.getName(), AppFeature.PERSONAL_TRAINERS);
        }
    }

    // ─── Owner/Staff: PT CRUD ─────────────────────────────────────────────────
    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF', 'MEMBER')")
    public List<PtProfileDto> listPts(Principal principal) {
        subscriptionService.requireFeature(principal.getName(), AppFeature.PERSONAL_TRAINERS);
        Role role = currentRole();
        if (role == Role.OWNER) {
            return ptService.listPts(principal.getName());
        }
        return ptService.listActivePts(principal.getName());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('OWNER')")
    public PtProfileDto createPt(Principal principal, @Valid @RequestBody PtRegistrationRequest req) {
        return ptService.createPt(principal.getName(), req);
    }

    @PutMapping("/{ptId}")
    @PreAuthorize("hasRole('OWNER')")
    public PtProfileDto updatePt(Principal principal, @PathVariable Long ptId,
            @Valid @RequestBody PtUpdateRequest req) {
        return ptService.updatePt(principal.getName(), ptId, req);
    }

    @PostMapping("/{ptId}/activate")
    @PreAuthorize("hasRole('OWNER')")
    public PtProfileDto activatePt(Principal principal, @PathVariable Long ptId) {
        return ptService.activatePt(principal.getName(), ptId);
    }

    @PostMapping("/{ptId}/deactivate")
    @PreAuthorize("hasRole('OWNER')")
    public PtProfileDto deactivatePt(Principal principal, @PathVariable Long ptId) {
        return ptService.deactivatePt(principal.getName(), ptId);
    }

    // ─── PT: Own profile & Trainees ──────────────────────────────────────────
    @GetMapping("/me")
    @PreAuthorize("hasRole('PERSONAL_TRAINER')")
    public PtProfileDto myProfile(Principal principal) {
        return ptService.getMyProfile(principal.getName());
    }

    @GetMapping("/me/trainees")
    @PreAuthorize("hasRole('PERSONAL_TRAINER')")
    public List<MembershipDto> myTrainees(Principal principal) {
        return ptService.getMyTrainees(principal.getName());
    }

    // ─── PT availability ──────────────────────────────────────────────────────
    @GetMapping("/{ptId}/availability")
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF', 'MEMBER')")
    public List<PtAvailabilityDto> getPtAvailability(Principal principal, @PathVariable Long ptId) {
        return ptService.getPtAvailability(principal.getName(), ptId);
    }

    @GetMapping("/me/availability")
    @PreAuthorize("hasRole('PERSONAL_TRAINER')")
    public List<PtAvailabilityDto> myAvailability(Principal principal) {
        return ptService.getAvailability(principal.getName());
    }

    @PutMapping("/me/availability")
    @PreAuthorize("hasRole('PERSONAL_TRAINER')")
    public List<PtAvailabilityDto> setAvailability(Principal principal,
            @Valid @RequestBody List<PtAvailabilityRequest> reqs) {
        return ptService.setAvailability(principal.getName(), reqs);
    }

    // ─── Sessions ─────────────────────────────────────────────────────────────
    @GetMapping("/me/sessions")
    @PreAuthorize("hasRole('PERSONAL_TRAINER')")
    public List<PtSessionDto> mySessions(Principal principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate f = from != null ? from : LocalDate.now().minusDays(30);
        LocalDate t = to != null ? to : LocalDate.now().plusDays(30);
        return scheduling.getMySessionsRange(principal.getName(), f, t);
    }

    @GetMapping("/{ptId}/sessions")
    @PreAuthorize("hasRole('OWNER')")
    public List<PtSessionDto> ptSessions(Principal principal, @PathVariable Long ptId) {
        return scheduling.getPtSessions(principal.getName(), ptId);
    }

    @PostMapping("/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF', 'PERSONAL_TRAINER', 'MEMBER')")
    public PtSessionDto bookSession(Principal principal, @Valid @RequestBody PtSessionBookRequest req) {
        return scheduling.bookSession(principal.getName(), currentRole(), req);
    }

    @PutMapping("/sessions/{sessionId}")
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF', 'PERSONAL_TRAINER')")
    public PtSessionDto rescheduleSession(Principal principal, @PathVariable Long sessionId,
            @Valid @RequestBody PtSessionUpdateRequest req) {
        return scheduling.rescheduleSession(principal.getName(), currentRole(), sessionId, req);
    }

    @PostMapping("/sessions/{sessionId}/cancel")
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF', 'PERSONAL_TRAINER', 'MEMBER')")
    public PtSessionDto cancelSession(Principal principal, @PathVariable Long sessionId,
            @Valid @RequestBody(required = false) PtSessionCancelRequest body) {
        String reason = body != null && body.reason() != null ? body.reason() : "";
        return scheduling.cancelSession(principal.getName(), currentRole(), sessionId, reason);
    }

    @PostMapping("/sessions/{sessionId}/complete")
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF', 'PERSONAL_TRAINER')")
    public PtSessionDto completeSession(Principal principal, @PathVariable Long sessionId,
            @Valid @RequestBody(required = false) PtSessionCompleteRequest body) {
        String notes = body != null && body.notes() != null ? body.notes() : "";
        String pin = body != null && body.pin() != null ? body.pin() : "";
        return scheduling.completeSession(principal.getName(), currentRole(), sessionId, notes, pin);
    }

    @PostMapping("/sessions/{sessionId}/missed")
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF', 'PERSONAL_TRAINER')")
    public PtSessionDto markMissed(Principal principal, @PathVariable Long sessionId) {
        return scheduling.markMissed(principal.getName(), currentRole(), sessionId);
    }

    @GetMapping("/member/sessions")
    @PreAuthorize("hasRole('MEMBER')")
    public List<PtSessionDto> memberSessions(Principal principal) {
        return scheduling.getMemberSessions(principal.getName());
    }

    // ─── Earnings ─────────────────────────────────────────────────────────────
    @GetMapping("/me/earnings")
    @PreAuthorize("hasRole('PERSONAL_TRAINER')")
    public List<PtEarningDto> myEarnings(Principal principal) {
        return ptService.getMyEarnings(principal.getName());
    }

    @GetMapping("/{ptId}/earnings")
    @PreAuthorize("hasRole('OWNER')")
    public List<PtEarningDto> ptEarnings(Principal principal, @PathVariable Long ptId) {
        return ptService.getPtEarnings(principal.getName(), ptId);
    }

    // ─── Payouts ──────────────────────────────────────────────────────────────
    @GetMapping("/me/payouts")
    @PreAuthorize("hasRole('PERSONAL_TRAINER')")
    public List<PtPayoutDto> myPayouts(Principal principal) {
        return ptService.getMyPayouts(principal.getName());
    }

    @GetMapping("/payouts")
    @PreAuthorize("hasRole('OWNER')")
    public List<PtPayoutDto> allPayouts(Principal principal) {
        return ptService.getAllPayouts(principal.getName());
    }

    @PostMapping("/{ptId}/payouts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('OWNER')")
    public PtPayoutDto createPayout(Principal principal, @PathVariable Long ptId,
            @Valid @RequestBody PtPayoutCreateRequest req) {
        return ptService.createPayout(principal.getName(), ptId, req);
    }

    @PostMapping("/payouts/{payoutId}/mark-paid")
    @PreAuthorize("hasRole('OWNER')")
    public PtPayoutDto markPaid(Principal principal, @PathVariable Long payoutId) {
        return ptService.markPaid(principal.getName(), payoutId);
    }

    // ─── Analytics ────────────────────────────────────────────────────────────
    @GetMapping("/analytics")
    @PreAuthorize("hasRole('OWNER')")
    public PtAnalyticsDto ptAnalytics(Principal principal,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ptService.ptAnalytics(principal.getName(), from, to);
    }

    private Role currentRole() {
        String authority = SecurityContextHolder.getContext().getAuthentication().getAuthorities()
                .iterator().next().getAuthority();
        return Role.valueOf(authority.substring("ROLE_".length()));
    }
}

