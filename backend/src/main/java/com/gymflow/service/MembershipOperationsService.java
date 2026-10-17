package com.gymflow.service;

import static com.gymflow.web.ApiDtos.*;

import com.gymflow.domain.GymBranch;
import com.gymflow.domain.GymOrganization;
import com.gymflow.domain.Membership;
import com.gymflow.domain.MembershipFreeze;
import com.gymflow.domain.MembershipPlan;
import com.gymflow.domain.MembershipStatus;
import com.gymflow.domain.Role;
import com.gymflow.domain.UserAccount;
import com.gymflow.repository.BranchRepository;
import com.gymflow.repository.MembershipFreezeRepository;
import com.gymflow.repository.MembershipRepository;
import com.gymflow.repository.PlanRepository;
import com.gymflow.repository.UserRepository;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class MembershipOperationsService {
    private final UserRepository users;
    private final MembershipRepository memberships;
    private final MembershipFreezeRepository freezes;
    private final PlanRepository plans;
    private final BranchRepository branches;
    private final ActionHistoryService actionHistory;
    private final GymService gymService;
    private final java.time.ZoneId businessZone;

    public MembershipOperationsService(UserRepository users, MembershipRepository memberships,
            MembershipFreezeRepository freezes, PlanRepository plans, BranchRepository branches,
            ActionHistoryService actionHistory, GymService gymService,
            @Value("${gym.business-zone:Asia/Kolkata}") String businessZone) {
        this.users = users;
        this.memberships = memberships;
        this.freezes = freezes;
        this.plans = plans;
        this.branches = branches;
        this.actionHistory = actionHistory;
        this.gymService = gymService;
        this.businessZone = java.time.ZoneId.of(businessZone);
    }

    public MembershipDto freeze(String email, Role role, long id, MembershipFreezeRequest request) {
        UserAccount actor = account(email);
        Membership membership = findScopedMembershipForUpdate(actor, role, id);
        LocalDate today = LocalDate.now(businessZone);
        if (membership.getStatus() != MembershipStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only an active membership can be frozen");
        }
        if (membership.getRenewalStartDate() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cancel the scheduled renewal before freezing this membership");
        }
        if (request.startDate().isBefore(today) || request.endDate().isBefore(request.startDate())
                || request.startDate().isBefore(membership.getStartDate())
                || request.startDate().isAfter(membership.getEndDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Freeze must start today or later and begin within the membership term");
        }
        long days = ChronoUnit.DAYS.between(request.startDate(), request.endDate()) + 1;
        if (days > 365) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A single freeze cannot exceed 365 days");
        }
        if (freezes.existsOverlapping(id, request.startDate(), request.endDate())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Freeze dates overlap an existing freeze");
        }
        MembershipDto before = gymService.membershipDto(membership);
        freezes.save(new MembershipFreeze(membership, request.startDate(),
                request.endDate(), request.reason().trim(), actor.getEmail()));
        membership.extendForFreeze(days);
        MembershipDto after = gymService.membershipDto(membership);
        actionHistory.record(actor, "MEMBERSHIP_FROZEN", "MEMBERSHIP", id, request.reason(),
                before, after);
        return after;
    }

    @Transactional(readOnly = true)
    public MembershipAccessDto access(String email, Role role, long id) {
        UserAccount actor = account(email);
        Membership membership = findScopedMembership(actor, role, id);
        LocalDate today = LocalDate.now(businessZone);
        String status;
        String message;
        if (membership.getStatus() == MembershipStatus.CANCELLED) {
            status = "CANCELLED";
            message = "Membership is cancelled.";
        } else if (freezes.existsActiveOnDate(id, today)) {
            status = "FROZEN";
            message = "Membership is frozen today; do not grant entry.";
        } else if (today.isBefore(membership.getStartDate())) {
            status = "NOT_STARTED";
            message = "Membership has not started.";
        } else if (today.isAfter(membership.getEndDate())) {
            status = "EXPIRED";
            message = "Membership has expired.";
        } else {
            status = "ACTIVE";
            message = "Membership is active; entry is permitted.";
        }
        return new MembershipAccessDto("ACTIVE".equals(status), status, today, message);
    }

    public MembershipDto cancelFreeze(String email, Role role, long membershipId, long freezeId,
            MembershipFreezeChangeRequest request) {
        UserAccount actor = account(email);
        Membership membership = findScopedMembershipForUpdate(actor, role, membershipId);
        if (membership.getStatus() != MembershipStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A freeze can only be adjusted on an active membership");
        }
        MembershipFreeze freeze = findFreeze(membershipId, freezeId);
        LocalDate today = LocalDate.now(businessZone);
        if (!freeze.getStartDate().isAfter(today) || freeze.getCancelledAt() != null
                || freeze.getEndedEarlyAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only a freeze that has not started can be cancelled");
        }
        MembershipDto before = gymService.membershipDto(membership);
        long frozenDays = ChronoUnit.DAYS.between(freeze.getStartDate(), freeze.getEndDate()) + 1;
        membership.reduceForFreeze(frozenDays);
        freeze.cancel();
        MembershipDto after = gymService.membershipDto(membership);
        actionHistory.record(actor, "MEMBERSHIP_FREEZE_CANCELLED", "MEMBERSHIP", membershipId,
                request.reason(), before, after);
        return after;
    }

    public MembershipDto endFreezeEarly(String email, Role role, long membershipId, long freezeId,
            MembershipFreezeChangeRequest request) {
        UserAccount actor = account(email);
        Membership membership = findScopedMembershipForUpdate(actor, role, membershipId);
        if (membership.getStatus() != MembershipStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A freeze can only be adjusted on an active membership");
        }
        MembershipFreeze freeze = findFreeze(membershipId, freezeId);
        LocalDate today = LocalDate.now(businessZone);
        if (freeze.getCancelledAt() != null || freeze.getEndedEarlyAt() != null
                || freeze.getStartDate().isAfter(today) || freeze.getEndDate().isBefore(today)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only a freeze that is active today can be ended early");
        }
        MembershipDto before = gymService.membershipDto(membership);
        long unusedDays = ChronoUnit.DAYS.between(today, freeze.getEndDate()) + 1;
        membership.reduceForFreeze(unusedDays);
        freeze.endEarly(today);
        MembershipDto after = gymService.membershipDto(membership);
        actionHistory.record(actor, "MEMBERSHIP_FREEZE_ENDED_EARLY", "MEMBERSHIP", membershipId,
                request.reason(), before, after);
        return after;
    }

    public MembershipDto transfer(String email, long id, MembershipTransferRequest request) {
        UserAccount actor = account(email);
        GymOrganization organization = requireOrganization(actor);
        Membership membership = memberships.findByIdForUpdate(id)
                .filter(item -> item.getMember().getOrganization() != null
                        && item.getMember().getOrganization().getId().equals(organization.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Membership not found"));
        GymBranch branch = branches.findByIdAndOrganizationIdAndActiveTrue(
                request.branchId(), organization.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Active branch not found"));
        if (membership.getBranch() != null && membership.getBranch().getId().equals(branch.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Membership is already assigned to that branch");
        }
        MembershipDto before = gymService.membershipDto(membership);
        membership.transferTo(branch);
        MembershipDto after = gymService.membershipDto(membership);
        actionHistory.record(actor, "MEMBERSHIP_TRANSFERRED", "MEMBERSHIP", id, request.reason(),
                before, after);
        return after;
    }

    public MembershipDto correct(String email, long id, MembershipCorrectionRequest request) {
        UserAccount actor = account(email);
        GymOrganization organization = requireOrganization(actor);
        Membership membership = memberships.findByIdForUpdate(id)
                .filter(item -> item.getMember().getOrganization() != null
                        && item.getMember().getOrganization().getId().equals(organization.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Membership not found"));
        if (request.endDate().isBefore(request.startDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "End date cannot be before start date");
        }
        if (membership.getRenewalStartDate() != null
                && !request.endDate().isBefore(membership.getRenewalStartDate())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Corrected membership dates cannot overlap its scheduled renewal");
        }
        if (freezes.findByMembershipIdOrderByStartDateAsc(id).stream()
                .filter(freeze -> freeze.getCancelledAt() == null)
                .anyMatch(freeze -> {
                    LocalDate effectiveEnd = freeze.getEndedEarlyAt() == null
                            ? freeze.getEndDate() : freeze.getEndedEarlyAt().minusDays(1);
                    return !effectiveEnd.isBefore(freeze.getStartDate())
                            && (request.startDate().isAfter(freeze.getStartDate())
                                    || request.endDate().isBefore(effectiveEnd));
                })) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Corrected dates must continue to include all recorded freeze periods");
        }
        if (memberships.hasOverlappingActiveMembership(membership.getMember().getId(), id, MembershipStatus.ACTIVE,
                request.startDate(), request.endDate())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Corrected dates would overlap another active or scheduled membership");
        }
        MembershipPlan plan = plans.findByIdAndOrganizationId(request.planId(), organization.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Package not found"));
        if (plan.getBranch() != null && (membership.getBranch() == null
                || !plan.getBranch().getId().equals(membership.getBranch().getId()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Selected package is not available at this membership branch");
        }
        Map<String, Object> beforeState = correctionState(membership);
        membership.correct(plan, request.startDate(), request.endDate(), request.listPrice(),
                request.amountPaid().setScale(2, java.math.RoundingMode.HALF_UP),
                request.discountPercent().setScale(2, java.math.RoundingMode.HALF_UP));
        MembershipDto after = gymService.membershipDto(membership);
        actionHistory.record(actor, "MEMBERSHIP_CORRECTED", "MEMBERSHIP", id, request.reason(),
                beforeState, correctionState(membership));
        return after;
    }

    private Map<String, Object> correctionState(Membership membership) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("planId", membership.getPlan().getId());
        state.put("planName", membership.getPlanNameSnapshot());
        state.put("startDate", membership.getStartDate());
        state.put("endDate", membership.getEndDate());
        state.put("listPrice", membership.getListPrice());
        state.put("discountPercent", membership.getDiscountPercent());
        state.put("amountPaid", membership.getAmountPaid());
        state.put("branchId", membership.getBranch() == null ? null : membership.getBranch().getId());
        return state;
    }

    private MembershipFreeze findFreeze(long membershipId, long freezeId) {
        return freezes.findById(freezeId)
                .filter(freeze -> freeze.getMembership().getId().equals(membershipId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Freeze not found"));
    }

    private Membership findScopedMembership(UserAccount actor, Role role, long id) {
        Membership membership = memberships.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Membership not found"));
        return verifyMembershipScope(actor, role, membership);
    }

    private Membership findScopedMembershipForUpdate(UserAccount actor, Role role, long id) {
        Membership membership = memberships.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Membership not found"));
        return verifyMembershipScope(actor, role, membership);
    }

    private Membership verifyMembershipScope(UserAccount actor, Role role, Membership membership) {
        GymOrganization organization = requireOrganization(actor);
        if (membership.getMember().getOrganization() == null
                || !membership.getMember().getOrganization().getId().equals(organization.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Membership not found");
        }
        if (role == Role.STAFF && (actor.getBranch() == null || membership.getBranch() == null
                || !actor.getBranch().getId().equals(membership.getBranch().getId()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Staff can only manage memberships at their assigned branch");
        }
        if (role == Role.MEMBER && !membership.getMember().getId().equals(actor.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Membership not found");
        }
        if (role == Role.PERSONAL_TRAINER && (membership.getPersonalTrainer() == null
                || !membership.getPersonalTrainer().getUserAccount().getId().equals(actor.getId()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Personal trainers can only access memberships assigned to them");
        }
        return membership;
    }

    private UserAccount account(String email) {
        return users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account not found"));
    }

    private GymOrganization requireOrganization(UserAccount actor) {
        if (actor.getOrganization() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Account is not assigned to a gym organization");
        }
        return actor.getOrganization();
    }
}
