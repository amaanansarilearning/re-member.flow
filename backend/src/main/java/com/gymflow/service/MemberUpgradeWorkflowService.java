package com.gymflow.service;

import com.gymflow.domain.*;
import com.gymflow.repository.*;
import com.gymflow.web.ApiDtos.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@Transactional
public class MemberUpgradeWorkflowService {

    private final UserRepository users;
    private final MembershipRepository memberships;
    private final PlanRepository plans;
    private final BranchRepository branches;
    private final PersonalTrainerRepository pts;
    private final MemberPackageUpgradeRequestRepository upgradeRequests;
    private final ActionHistoryService actionHistory;
    private final SubscriptionService subscriptionService;
    private final ZoneId businessZone;

    public MemberUpgradeWorkflowService(
            UserRepository users,
            MembershipRepository memberships,
            PlanRepository plans,
            BranchRepository branches,
            PersonalTrainerRepository pts,
            MemberPackageUpgradeRequestRepository upgradeRequests,
            ActionHistoryService actionHistory,
            SubscriptionService subscriptionService,
            @Value("${gym.business-zone:Asia/Kolkata}") String businessZone) {
        this.users = users;
        this.memberships = memberships;
        this.plans = plans;
        this.branches = branches;
        this.pts = pts;
        this.upgradeRequests = upgradeRequests;
        this.actionHistory = actionHistory;
        this.subscriptionService = subscriptionService;
        this.businessZone = ZoneId.of(businessZone);
    }

    public MemberPackageUpgradeRequestDto submitUpgradeRequest(
            String email, Role role, long membershipId, MemberUpgradeSubmissionRequest request) {
        Membership old = memberships.findByIdForUpdate(membershipId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Membership not found"));
        UserAccount actor = account(email);
        verifyMembershipScope(actor, role, old);
        GymOrganization organization = requireOrganization(actor);

        if (old.getStatus() != MembershipStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only active memberships can be upgraded");
        }
        LocalDate today = LocalDate.now(businessZone);
        if (old.getEndDate().isBefore(today)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Membership has expired - please renew instead");
        }
        if (today.isBefore(old.getStartDate())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Membership has not started yet");
        }
        if (old.getRenewalStartDate() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This membership has a scheduled renewal. Please cancel or update the renewal before upgrading the current term.");
        }

        // Check if there is already a pending upgrade request for this membership
        if (upgradeRequests.existsByMembershipIdAndStatus(membershipId, MemberUpgradeRequestStatus.PENDING)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "An upgrade request for this membership is already pending review");
        }

        if (request.newPlanId().equals(old.getPlan().getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Member is already on this plan");
        }

        MembershipPlan newPlan = plans.findByIdAndOrganizationId(request.newPlanId(), organization.getId())
                .filter(MembershipPlan::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Active package not found"));

        int totalDays = (int) (ChronoUnit.DAYS.between(old.getStartDate(), old.getEndDate()) + 1);
        if (totalDays < 1) totalDays = 1;
        int remainingDays = (int) (ChronoUnit.DAYS.between(today, old.getEndDate()) + 1);
        if (remainingDays < 0) remainingDays = 0;

        int oldDuration = old.getPlanDurationDaysSnapshot() != null ? old.getPlanDurationDaysSnapshot()
                : (old.getPlanDurationMonthsSnapshot() != null ? old.getPlanDurationMonthsSnapshot() * 30 : totalDays);
        int newDuration = newPlan.getDurationDays() != null ? newPlan.getDurationDays()
                : (newPlan.getDurationMonths() != null ? newPlan.getDurationMonths() * 30 : totalDays);
        if (newDuration < oldDuration) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot upgrade to a package of shorter duration (" + newDuration + " days < " + oldDuration + " days)");
        }

        int newTotalDays = (newDuration == oldDuration) ? totalDays : Math.max(1, newDuration);

        BigDecimal oldDailyRate = old.getListPrice().divide(BigDecimal.valueOf(totalDays), 4, RoundingMode.HALF_UP);
        BigDecimal newDailyRate = newPlan.getPrice().divide(BigDecimal.valueOf(newTotalDays), 4, RoundingMode.HALF_UP);
        BigDecimal creditAmount = oldDailyRate.multiply(BigDecimal.valueOf(remainingDays)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal newPlanProrataCost = newDailyRate.multiply(BigDecimal.valueOf(remainingDays)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal topUpDue = newPlanProrataCost.subtract(creditAmount).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

        PersonalTrainer pt = null;
        boolean newHasPt = newPlan.getPtPackageType() != null && newPlan.getPtPackageType() != PtPackageType.NONE;
        if (request.personalTrainerId() != null) {
            subscriptionService.requireFeature(actor, AppFeature.PERSONAL_TRAINERS);
            pt = pts.findByIdAndOrganizationId(request.personalTrainerId(), organization.getId())
                    .filter(PersonalTrainer::isActive)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Active personal trainer not found"));
        } else if (old.getPersonalTrainer() != null && old.getPersonalTrainer().isActive() && newHasPt) {
            pt = old.getPersonalTrainer();
        }

        String paymentMethod = (request.paymentMethod() == null || request.paymentMethod().isBlank())
                ? "CASH"
                : request.paymentMethod().trim().toUpperCase();
        String transactionId = (request.transactionId() == null || request.transactionId().isBlank())
                ? null
                : request.transactionId().trim();

        MemberPackageUpgradeRequest entity = new MemberPackageUpgradeRequest(
                old,
                old.getMember(),
                newPlan,
                old.getBranch(),
                organization,
                pt,
                topUpDue,
                paymentMethod,
                transactionId,
                request.notes()
        );

        entity = upgradeRequests.save(entity);

        actionHistory.record(actor, "MEMBER_UPGRADE_REQUEST_SUBMITTED", "MEMBER_UPGRADE_REQUEST", entity.getId(),
                "Member submitted upgrade request to plan " + newPlan.getName() + " with top-up amount " + topUpDue,
                null, toDto(entity));

        return toDto(entity);
    }

    @Transactional(readOnly = true)
    public List<MemberPackageUpgradeRequestDto> listUpgradeRequests(String email, Role role, MemberUpgradeRequestStatus status) {
        UserAccount actor = account(email);
        GymOrganization org = requireOrganization(actor);

        List<MemberPackageUpgradeRequest> list;
        if (role == Role.MEMBER) {
            list = upgradeRequests.findByMemberIdOrderByCreatedAtDesc(actor.getId());
        } else if (role == Role.STAFF) {
            GymBranch staffBranch = actor.getBranch();
            if (staffBranch != null) {
                list = upgradeRequests.findByBranchOrUnassigned(org.getId(), staffBranch.getId());
            } else {
                list = upgradeRequests.findByOrganizationIdOrderByCreatedAtDesc(org.getId());
            }
        } else {
            list = upgradeRequests.findByOrganizationIdOrderByCreatedAtDesc(org.getId());
        }

        if (status != null) {
            list = list.stream().filter(r -> r.getStatus() == status).toList();
        }

        return list.stream().map(this::toDto).toList();
    }

    public MemberPackageUpgradeRequestDto approveUpgradeRequest(
            String email, Role role, long requestId, MemberPackageUpgradeApprovalRequest approval) {
        UserAccount actor = account(email);
        GymOrganization organization = requireOrganization(actor);

        // Pessimistic write lock prevents concurrent staff double-approval
        MemberPackageUpgradeRequest request = upgradeRequests.findByIdAndOrganizationIdForUpdate(requestId, organization.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Upgrade request not found"));

        if (request.getStatus() != MemberUpgradeRequestStatus.PENDING) {
            String reviewer = request.getReviewedByName() != null ? request.getReviewedByName() : "another staff member";
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This upgrade request has already been processed by " + reviewer + " (Status: " + request.getStatus() + ")");
        }

        if (role == Role.STAFF) {
            GymBranch staffBranch = actor.getBranch();
            if (staffBranch != null && request.getBranch() != null && !staffBranch.getId().equals(request.getBranch().getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Staff can only review upgrade requests for their assigned branch");
            }
        }

        Membership old = memberships.findByIdForUpdate(request.getMembership().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Target membership not found"));

        if (old.getStatus() != MembershipStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Target membership is no longer active (current status: " + old.getStatus() + ")");
        }

        LocalDate today = LocalDate.now(businessZone);
        if (old.getEndDate().isBefore(today)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Target membership has expired");
        }

        String paymentMethod = (approval != null && approval.paymentMethod() != null && !approval.paymentMethod().isBlank())
                ? approval.paymentMethod().trim().toUpperCase()
                : request.getPaymentMethod();
        String transactionId = (approval != null && approval.transactionId() != null && !approval.transactionId().isBlank())
                ? approval.transactionId().trim()
                : request.getTransactionId();

        // Mark old as UPGRADED
        old.markUpgraded();

        // Create new membership from today to old.getEndDate()
        MembershipPlan newPlan = request.getNewPlan();
        Membership upgraded = Membership.createUpgraded(
                old.getMember(),
                newPlan,
                old.getBranch(),
                today,
                old.getEndDate(),
                newPlan.getPrice(),
                request.getTopUpAmount(),
                old,
                request.getPersonalTrainer(),
                paymentMethod,
                transactionId
        );

        upgraded = memberships.save(upgraded);
        request.approve(actor, upgraded);

        actionHistory.record(actor, "MEMBER_UPGRADE_REQUEST_APPROVED", "MEMBER_UPGRADE_REQUEST", request.getId(),
                "Approved member package upgrade to " + newPlan.getName() + " for " + request.getMember().getFullName(),
                null, toDto(request));

        return toDto(request);
    }

    public MemberPackageUpgradeRequestDto rejectUpgradeRequest(
            String email, Role role, long requestId, MemberPackageUpgradeRejectionRequest rejection) {
        UserAccount actor = account(email);
        GymOrganization organization = requireOrganization(actor);

        // Pessimistic write lock prevents concurrent staff double-approval / race condition
        MemberPackageUpgradeRequest request = upgradeRequests.findByIdAndOrganizationIdForUpdate(requestId, organization.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Upgrade request not found"));

        if (request.getStatus() != MemberUpgradeRequestStatus.PENDING) {
            String reviewer = request.getReviewedByName() != null ? request.getReviewedByName() : "another staff member";
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This upgrade request has already been processed by " + reviewer + " (Status: " + request.getStatus() + ")");
        }

        if (role == Role.STAFF) {
            GymBranch staffBranch = actor.getBranch();
            if (staffBranch != null && request.getBranch() != null && !staffBranch.getId().equals(request.getBranch().getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Staff can only review upgrade requests for their assigned branch");
            }
        }

        String reason = (rejection != null && rejection.reason() != null && !rejection.reason().isBlank())
                ? rejection.reason().trim()
                : "Rejected by gym staff";

        request.reject(actor, reason);

        actionHistory.record(actor, "MEMBER_UPGRADE_REQUEST_REJECTED", "MEMBER_UPGRADE_REQUEST", request.getId(),
                "Rejected member upgrade request for " + request.getMember().getFullName() + ". Reason: " + reason,
                null, toDto(request));

        return toDto(request);
    }

    private UserAccount account(String email) {
        return users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private GymOrganization requireOrganization(UserAccount user) {
        if (user.getOrganization() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User does not belong to an organization");
        }
        return user.getOrganization();
    }

    private GymBranch requireUserBranch(UserAccount user) {
        if (user.getBranch() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User does not belong to a branch");
        }
        return user.getBranch();
    }

    private void verifyMembershipScope(UserAccount actor, Role role, Membership membership) {
        GymOrganization actorOrg = requireOrganization(actor);
        if (!membership.getBranch().getOrganization().getId().equals(actorOrg.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied to cross-organization membership");
        }
        if (role == Role.MEMBER && !membership.getMember().getId().equals(actor.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Members can only manage their own memberships");
        }
        if (role == Role.STAFF && actor.getBranch() != null
                && !membership.getBranch().getId().equals(actor.getBranch().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Staff can only manage memberships in their assigned branch");
        }
    }

    public MemberPackageUpgradeRequestDto toDto(MemberPackageUpgradeRequest r) {
        return new MemberPackageUpgradeRequestDto(
                r.getId(),
                r.getMembership() != null ? r.getMembership().getId() : null,
                r.getMember() != null ? r.getMember().getId() : null,
                r.getMember() != null ? r.getMember().getFullName() : null,
                r.getMember() != null ? r.getMember().getEmail() : null,
                r.getMember() != null ? r.getMember().getPhoneNumber() : null,
                r.getMembership() != null && r.getMembership().getPlan() != null ? r.getMembership().getPlan().getId() : null,
                r.getMembership() != null && r.getMembership().getPlan() != null ? r.getMembership().getPlan().getName() : null,
                r.getNewPlan() != null ? r.getNewPlan().getId() : null,
                r.getNewPlan() != null ? r.getNewPlan().getName() : null,
                r.getNewPlan() != null ? r.getNewPlan().getPrice() : null,
                r.getBranch() != null ? r.getBranch().getId() : null,
                r.getBranch() != null ? r.getBranch().getName() : null,
                r.getPersonalTrainer() != null ? r.getPersonalTrainer().getId() : null,
                r.getPersonalTrainer() != null && r.getPersonalTrainer().getUserAccount() != null ? r.getPersonalTrainer().getUserAccount().getFullName() : null,
                r.getTopUpAmount(),
                r.getPaymentMethod(),
                r.getTransactionId(),
                r.getNotes(),
                r.getStatus().name(),
                r.getReviewedByName(),
                r.getReviewedAt(),
                r.getRejectionReason(),
                r.getResultingMembership() != null ? r.getResultingMembership().getId() : null,
                r.getCreatedAt()
        );
    }
}
