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
import java.util.List;

@Service
@Transactional
public class RenewalWorkflowService {

    private final UserRepository users;
    private final MembershipRepository memberships;
    private final PlanRepository plans;
    private final BranchRepository branches;
    private final PersonalTrainerRepository pts;
    private final RenewalRequestRepository renewalRequests;
    private final ActionHistoryService actionHistory;
    private final GymService gymService;
    private final SubscriptionService subscriptionService;
    private final ZoneId businessZone;

    public RenewalWorkflowService(UserRepository users,
                                  MembershipRepository memberships,
                                  PlanRepository plans,
                                  BranchRepository branches,
                                  PersonalTrainerRepository pts,
                                  RenewalRequestRepository renewalRequests,
                                  ActionHistoryService actionHistory,
                                  GymService gymService,
                                  SubscriptionService subscriptionService,
                                  @Value("${gym.business-zone:Asia/Kolkata}") String businessZone) {
        this.users = users;
        this.memberships = memberships;
        this.plans = plans;
        this.branches = branches;
        this.pts = pts;
        this.renewalRequests = renewalRequests;
        this.actionHistory = actionHistory;
        this.gymService = gymService;
        this.subscriptionService = subscriptionService;
        this.businessZone = ZoneId.of(businessZone);
    }

    public RenewalRequestDto submitRenewalRequest(String email, Role role, long membershipId,
                                                   MemberRenewalSubmissionRequest request) {
        Membership previous = memberships.findByIdForUpdate(membershipId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Membership not found"));
        UserAccount actor = account(email);

        // Security check: Only the member owning the membership (or staff/owner) can submit a request
        if (role == Role.MEMBER && !previous.getMember().getEmail().equalsIgnoreCase(actor.getEmail())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only request renewals for your own memberships");
        }

        GymOrganization organization = requireOrganization(actor);

        if (previous.getRenewalStartDate() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This membership already has a renewal scheduled");
        }

        if (renewalRequests.existsByMembershipIdAndStatus(previous.getId(), RenewalRequestStatus.PENDING)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A renewal request is already pending review by gym staff for this membership");
        }

        GymBranch targetBranch = previous.getBranch();

        MembershipPlan plan = plans.findByIdAndOrganizationId(request.planId(), organization.getId())
                .filter(MembershipPlan::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Active package not found"));

        if (plan.getBranch() != null && !plan.getBranch().getId().equals(targetBranch.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Active package not found for this branch");
        }

        LocalDate today = LocalDate.now(businessZone);
        if (previous.getStatus() == MembershipStatus.ACTIVE && previous.getStartDate().isAfter(today)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A future membership term cannot be renewed before it starts");
        }

        LocalDate earliestStart = previous.getStatus() == MembershipStatus.CANCELLED
                || previous.getEndDate().isBefore(today) ? today : previous.getEndDate().plusDays(1);
        LocalDate startDate = request.startDate() == null ? earliestStart : request.startDate();
        if (startDate.isBefore(earliestStart)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A renewal cannot start before " + earliestStart);
        }

        LocalDate renewalEndDate = calculateEndDate(startDate, plan);
        if (memberships.hasOverlappingActiveMembership(previous.getMember().getId(), previous.getId(),
                MembershipStatus.ACTIVE, startDate, renewalEndDate)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This member already has an active membership during the selected renewal dates");
        }

        PersonalTrainer pt = null;
        if (request.personalTrainerId() != null) {
            subscriptionService.requireFeature(actor, AppFeature.PERSONAL_TRAINERS);
            pt = pts.findByIdAndOrganizationId(request.personalTrainerId(), organization.getId())
                    .filter(PersonalTrainer::isActive)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Active personal trainer not found"));
        } else if (previous.getPersonalTrainer() != null && previous.getPersonalTrainer().isActive()) {
            pt = previous.getPersonalTrainer();
        }

        String paymentMethod = (request.paymentMethod() == null || request.paymentMethod().isBlank())
                ? "CASH"
                : request.paymentMethod().trim().toUpperCase();
        String transactionId = (request.transactionId() == null || request.transactionId().isBlank())
                ? null
                : request.transactionId().trim();

        // Enforce 0 discount for self-service submissions
        BigDecimal discount = BigDecimal.ZERO;
        BigDecimal amount = plan.getPrice().setScale(2, RoundingMode.HALF_UP);

        RenewalRequest entity = new RenewalRequest(
                previous,
                previous.getMember(),
                plan,
                targetBranch,
                organization,
                pt,
                startDate,
                paymentMethod,
                transactionId,
                request.notes(),
                amount,
                discount
        );

        entity = renewalRequests.save(entity);

        actionHistory.record(actor, "RENEWAL_REQUEST_SUBMITTED", "RENEWAL_REQUEST", entity.getId(),
                "Member submitted renewal request for plan: " + plan.getName() + " via " + paymentMethod,
                null, toDto(entity));

        return toDto(entity);
    }

    @Transactional(readOnly = true)
    public List<RenewalRequestDto> listRenewalRequests(String email, Role role, RenewalRequestStatus status) {
        UserAccount actor = account(email);
        GymOrganization org = requireOrganization(actor);

        List<RenewalRequest> list;
        if (role == Role.MEMBER) {
            list = renewalRequests.findByMemberIdOrderByCreatedAtDesc(actor.getId());
        } else if (role == Role.STAFF) {
            GymBranch staffBranch = actor.getBranch();
            if (staffBranch != null) {
                list = renewalRequests.findByBranchOrUnassigned(org.getId(), staffBranch.getId());
            } else {
                list = renewalRequests.findByOrganizationIdOrderByCreatedAtDesc(org.getId());
            }
        } else {
            list = renewalRequests.findByOrganizationIdOrderByCreatedAtDesc(org.getId());
        }

        if (status != null) {
            list = list.stream().filter(r -> r.getStatus() == status).toList();
        }

        return list.stream().map(this::toDto).toList();
    }

    public RenewalRequestDto approveRenewalRequest(String email, Role role, long requestId,
                                                   RenewalApprovalRequest approval) {
        UserAccount actor = account(email);
        GymOrganization organization = requireOrganization(actor);

        RenewalRequest request = renewalRequests.findByIdAndOrganizationIdForUpdate(requestId, organization.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Renewal request not found"));

        if (request.getStatus() != RenewalRequestStatus.PENDING) {
            String reviewer = request.getReviewedByName() != null ? request.getReviewedByName() : "another staff member";
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This renewal request has already been processed by " + reviewer + " (Status: " + request.getStatus() + ")");
        }

        if (role == Role.STAFF) {
            GymBranch staffBranch = actor.getBranch();
            if (staffBranch != null && request.getBranch() != null && !staffBranch.getId().equals(request.getBranch().getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Staff can only review renewal requests for their assigned branch");
            }
        }

        Membership previous = memberships.findByIdForUpdate(request.getMembership().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Target membership not found"));

        if (previous.getRenewalStartDate() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This membership already has another renewal scheduled");
        }

        MembershipPlan plan = request.getPlan();
        GymBranch targetBranch = request.getBranch();
        LocalDate startDate = request.getRequestedStartDate();
        LocalDate renewalEndDate = calculateEndDate(startDate, plan);

        if (memberships.hasOverlappingActiveMembership(previous.getMember().getId(), previous.getId(),
                MembershipStatus.ACTIVE, startDate, renewalEndDate)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This member already has an active membership during the renewal dates");
        }

        BigDecimal discount = (approval != null && approval.discountPercent() != null)
                ? approval.discountPercent().setScale(2, RoundingMode.HALF_UP)
                : request.getDiscountPercent().setScale(2, RoundingMode.HALF_UP);

        BigDecimal amount = plan.getPrice().multiply(BigDecimal.ONE.subtract(discount.movePointLeft(2)))
                .setScale(2, RoundingMode.HALF_UP);

        String paymentMethod = (approval != null && approval.paymentMethod() != null && !approval.paymentMethod().isBlank())
                ? approval.paymentMethod().trim().toUpperCase()
                : request.getPaymentMethod();

        String transactionId = (approval != null && approval.transactionId() != null && !approval.transactionId().isBlank())
                ? approval.transactionId().trim()
                : request.getTransactionId();

        Membership renewal = memberships.save(new Membership(
                previous.getMember(),
                plan,
                targetBranch,
                startDate,
                renewalEndDate,
                plan.getPrice(),
                amount,
                discount,
                previous,
                request.getPersonalTrainer(),
                paymentMethod,
                transactionId
        ));

        previous.scheduleRenewal(startDate);
        request.approve(actor, renewal, discount, paymentMethod, transactionId);

        actionHistory.record(actor, "RENEWAL_REQUEST_APPROVED", "RENEWAL_REQUEST", request.getId(),
                "Approved renewal for " + previous.getMember().getFullName() + " (" + plan.getName() + ")",
                null, toDto(request));

        return toDto(request);
    }

    public RenewalRequestDto rejectRenewalRequest(String email, Role role, long requestId,
                                                  RenewalRejectionRequest rejection) {
        UserAccount actor = account(email);
        GymOrganization organization = requireOrganization(actor);

        RenewalRequest request = renewalRequests.findByIdAndOrganizationIdForUpdate(requestId, organization.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Renewal request not found"));

        if (request.getStatus() != RenewalRequestStatus.PENDING) {
            String reviewer = request.getReviewedByName() != null ? request.getReviewedByName() : "another staff member";
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This renewal request has already been processed by " + reviewer + " (Status: " + request.getStatus() + ")");
        }

        if (role == Role.STAFF) {
            GymBranch staffBranch = actor.getBranch();
            if (staffBranch != null && request.getBranch() != null && !staffBranch.getId().equals(request.getBranch().getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Staff can only review renewal requests for their assigned branch");
            }
        }

        String reason = (rejection != null && rejection.reason() != null && !rejection.reason().isBlank())
                ? rejection.reason().trim()
                : "Rejected by gym staff";

        request.reject(actor, reason);

        actionHistory.record(actor, "RENEWAL_REQUEST_REJECTED", "RENEWAL_REQUEST", request.getId(),
                "Rejected renewal request for " + request.getMember().getFullName() + ". Reason: " + reason,
                null, toDto(request));

        return toDto(request);
    }

    private LocalDate calculateEndDate(LocalDate startDate, MembershipPlan plan) {
        LocalDate exclusiveEnd = plan.getDurationDays() == null
                ? startDate.plusMonths(plan.getDurationMonths())
                : startDate.plusDays(plan.getDurationDays());
        return exclusiveEnd.minusDays(1);
    }

    private UserAccount account(String email) {
        return users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User account not found"));
    }

    private GymOrganization requireOrganization(UserAccount actor) {
        GymOrganization organization = actor.getOrganization();
        if (organization == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Organization context required");
        }
        return organization;
    }

    private GymBranch requireUserBranch(UserAccount actor) {
        GymBranch branch = actor.getBranch();
        if (branch == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User has no branch assignment");
        }
        return branch;
    }

    private RenewalRequestDto toDto(RenewalRequest r) {
        return new RenewalRequestDto(
                r.getId(),
                r.getMembership().getId(),
                r.getMember().getId(),
                r.getMember().getFullName(),
                r.getMember().getEmail(),
                r.getMember().getPhoneNumber(),
                r.getPlan().getId(),
                r.getPlan().getName(),
                r.getPlan().getPrice(),
                r.getBranch().getId(),
                r.getBranch().getName(),
                r.getPersonalTrainer() == null ? null : r.getPersonalTrainer().getId(),
                r.getPersonalTrainer() == null ? null : r.getPersonalTrainer().getUserAccount().getFullName(),
                r.getRequestedStartDate(),
                r.getPaymentMethod(),
                r.getTransactionId(),
                r.getNotes(),
                r.getAmount(),
                r.getDiscountPercent(),
                r.getStatus().name(),
                r.getReviewedBy() == null ? null : r.getReviewedBy().getFullName(),
                r.getReviewedAt(),
                r.getRejectionReason(),
                r.getResultingMembership() == null ? null : r.getResultingMembership().getId(),
                r.getCreatedAt()
        );
    }
}

