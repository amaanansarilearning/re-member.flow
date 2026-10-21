package com.gymflow.service;

import com.gymflow.domain.*;
import com.gymflow.repository.*;
import static com.gymflow.web.ApiDtos.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class PtSchedulingService {
    private final PersonalTrainerRepository pts;
    private final PtSessionRepository sessions;
    private final PtEarningRepository earnings;
    private final MembershipRepository memberships;
    private final UserRepository users;
    private final ActionHistoryService actionHistory;
    private final GymService gymService;

    public PtSchedulingService(PersonalTrainerRepository pts, PtSessionRepository sessions,
            PtEarningRepository earnings, MembershipRepository memberships,
            UserRepository users, ActionHistoryService actionHistory, GymService gymService) {
        this.pts = pts;
        this.sessions = sessions;
        this.earnings = earnings;
        this.memberships = memberships;
        this.users = users;
        this.actionHistory = actionHistory;
        this.gymService = gymService;
    }

    @Transactional(readOnly = true)
    public List<PtSessionDto> getMySessionsRange(String ptEmail, LocalDate from, LocalDate to) {
        UserAccount user = gymService.account(ptEmail);
        PersonalTrainer pt = requirePtProfile(user);
        return sessions.findByPersonalTrainerIdAndScheduledDateBetweenOrderByScheduledDateAscStartTimeAsc(
                pt.getId(), from, to).stream().map(this::toSessionDto).toList();
    }

    @Transactional(readOnly = true)
    public List<PtSessionDto> getMemberSessions(String memberEmail) {
        // get their memberships and aggregate all sessions
        return memberships.findByMemberEmailIgnoreCaseOrderByCreatedAtDesc(memberEmail).stream()
                .flatMap(m -> sessions.findByMembershipIdOrderByScheduledDateAscStartTimeAsc(m.getId()).stream())
                .map(this::toSessionDto).toList();
    }

    @Transactional(readOnly = true)
    public List<PtSessionDto> getPtSessions(String ownerEmail, Long ptId) {
        UserAccount owner = gymService.account(ownerEmail);
        GymOrganization org = requireOrg(owner);
        PersonalTrainer pt = pts.findByIdAndOrganizationId(ptId, org.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PT not found"));
        return sessions.findByPersonalTrainerIdOrderByScheduledDateDescStartTimeDesc(pt.getId())
                .stream().map(this::toSessionDto).toList();
    }

    public PtSessionDto bookSession(String actorEmail, Role actorRole, PtSessionBookRequest req) {
        UserAccount actor = gymService.account(actorEmail);
        GymOrganization org = requireOrg(actor);
        Membership membership = memberships.findById(req.membershipId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Membership not found"));

        if (membership.getMember().getOrganization() == null ||
                !membership.getMember().getOrganization().getId().equals(org.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Membership not found");
        }

        if (actorRole == Role.MEMBER && !membership.getMember().getId().equals(actor.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Members can only book sessions for their own membership");
        }

        if (actorRole == Role.STAFF && (actor.getBranch() == null || membership.getBranch() == null ||
                !actor.getBranch().getId().equals(membership.getBranch().getId()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Staff can only book sessions for memberships at their assigned branch");
        }

        // Resolve PT
        PersonalTrainer pt;
        if (actorRole == Role.PERSONAL_TRAINER) {
            pt = requirePtProfile(actor);
        } else {
            if (membership.getPersonalTrainer() == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "This membership has no assigned PT");
            }
            pt = membership.getPersonalTrainer();
        }

        // Validate membership has this PT assigned
        if (membership.getPersonalTrainer() == null ||
                !membership.getPersonalTrainer().getId().equals(pt.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This membership is not assigned to this PT");
        }
        // Check PT sessions remaining
        if (membership.getPtSessionsIncluded() != null &&
                membership.getPtSessionsUsed() >= membership.getPtSessionsIncluded()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No remaining PT sessions in this membership");
        }
        // Check membership is active
        if (membership.getStatus() != MembershipStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Can only book sessions for active memberships");
        }
        // Conflict check
        if (sessions.hasConflict(pt.getId(), req.scheduledDate(),
                req.startTime(), req.endTime(), null)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "PT has a conflicting session at this time");
        }
        BigDecimal effectiveFee = calculateEffectiveSessionFee(membership, pt);
        PtSession session = sessions.save(new PtSession(pt, membership,
                req.scheduledDate(), req.startTime(), req.endTime(),
                req.notes(), pt.getCommissionPercent(), effectiveFee));
        actionHistory.record(actor, "PT_SESSION_BOOKED", "PT_SESSION", session.getId(),
                null, null, toSessionDto(session));
        return toSessionDto(session);
    }

    public PtSessionDto rescheduleSession(String actorEmail, Role actorRole,
            Long sessionId, PtSessionUpdateRequest req) {
        PtSession session = findSessionForActor(actorEmail, actorRole, sessionId);
        if (session.getStatus() == PtSessionStatus.COMPLETED ||
                session.getStatus() == PtSessionStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot reschedule a completed or cancelled session");
        }
        if (sessions.hasConflict(session.getPersonalTrainer().getId(),
                req.scheduledDate(), req.startTime(), req.endTime(), sessionId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "PT has a conflicting session at this time");
        }
        session.reschedule(req.scheduledDate(), req.startTime(), req.endTime());
        return toSessionDto(session);
    }

    public PtSessionDto cancelSession(String actorEmail, Role actorRole,
            Long sessionId, String reason) {
        PtSession session = findSessionForActor(actorEmail, actorRole, sessionId);
        if (session.getStatus() == PtSessionStatus.COMPLETED ||
                session.getStatus() == PtSessionStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Session is already completed or cancelled");
        }
        session.cancel(reason);
        return toSessionDto(session);
    }

    public PtSessionDto completeSession(String ptEmail, Long sessionId, String notes) {
        return completeSession(ptEmail, Role.PERSONAL_TRAINER, sessionId, notes, null);
    }

    public PtSessionDto completeSession(String actorEmail, Role actorRole, Long sessionId, String notes) {
        return completeSession(actorEmail, actorRole, sessionId, notes, null);
    }

    public PtSessionDto completeSession(String actorEmail, Role actorRole, Long sessionId, String notes, String pin) {
        PtSession session = findSessionForActor(actorEmail, actorRole, sessionId);
        PersonalTrainer pt = session.getPersonalTrainer();
        UserAccount user = gymService.account(actorEmail);
        if (session.getStatus() != PtSessionStatus.BOOKED &&
                session.getStatus() != PtSessionStatus.TENTATIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only a booked or tentative session can be completed");
        }

        UserAccount member = session.getMembership().getMember();
        String expectedPin = member.getMemberPin();

        String verificationMethod;
        if (actorRole == Role.PERSONAL_TRAINER) {
            if (pin != null && !pin.isBlank()) {
                if (!pin.trim().equals(expectedPin)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Invalid client verification PIN. Please request the 4-digit PIN from your client to verify session attendance.");
                }
                verificationMethod = "MEMBER_PIN";
            } else if (expectedPin != null && !expectedPin.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Client verification PIN is required to complete this session. Ask the member for their 4-digit PIN.");
            } else {
                verificationMethod = "SELF_VERIFIED";
            }
        } else {
            // OWNER or STAFF
            if (pin != null && !pin.isBlank() && pin.trim().equals(expectedPin)) {
                verificationMethod = "MEMBER_PIN";
            } else {
                verificationMethod = "STAFF_OVERRIDE";
            }
        }

        session.complete(notes, verificationMethod);
        // Increment sessions used
        Membership m = session.getMembership();
        m.incrementPtSessionsUsed();
        // Create earning record
        if (!earnings.existsByPtSessionId(session.getId())) {
            BigDecimal gross = session.getSessionFeeSnapshot() != null ? session.getSessionFeeSnapshot() : BigDecimal.ZERO;
            if (gross.compareTo(BigDecimal.ZERO) < 0) gross = BigDecimal.ZERO;
            BigDecimal rate = session.getCommissionRateSnapshot() != null ? session.getCommissionRateSnapshot() : BigDecimal.ZERO;
            if (rate.compareTo(BigDecimal.ZERO) < 0) rate = BigDecimal.ZERO;
            if (rate.compareTo(BigDecimal.valueOf(100)) > 0) rate = BigDecimal.valueOf(100);
            BigDecimal ptCut = gross.multiply(rate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            BigDecimal gymCut = gross.subtract(ptCut).setScale(2, RoundingMode.HALF_UP);
            earnings.save(new PtEarning(session, pt, gross, ptCut, gymCut, rate));
        }
        actionHistory.record(user, "PT_SESSION_COMPLETED", "PT_SESSION", sessionId,
                null, null, toSessionDto(session));
        return toSessionDto(session);
    }

    public PtSessionDto markMissed(String ptEmail, Long sessionId) {
        return markMissed(ptEmail, Role.PERSONAL_TRAINER, sessionId);
    }

    public PtSessionDto markMissed(String actorEmail, Role actorRole, Long sessionId) {
        PtSession session = findSessionForActor(actorEmail, actorRole, sessionId);
        if (session.getStatus() != PtSessionStatus.BOOKED &&
                session.getStatus() != PtSessionStatus.TENTATIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only a booked session can be marked missed");
        }
        session.markMissed();
        return toSessionDto(session);
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private PtSession findSessionForActor(String actorEmail, Role actorRole, Long sessionId) {
        UserAccount actor = gymService.account(actorEmail);
        if (actorRole == Role.PERSONAL_TRAINER) {
            PersonalTrainer pt = requirePtProfile(actor);
            return sessions.findByIdAndPersonalTrainerId(sessionId, pt.getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));
        } else if (actorRole == Role.MEMBER) {
            return sessions.findByIdAndMembershipMemberId(sessionId, actor.getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));
        } else {
            // OWNER or STAFF
            PtSession session = sessions.findById(sessionId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));
            GymOrganization org = requireOrg(actor);
            if (session.getPersonalTrainer().getOrganization() == null ||
                    !session.getPersonalTrainer().getOrganization().getId().equals(org.getId())) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found");
            }
            if (actorRole == Role.STAFF) {
                GymBranch branch = actor.getBranch();
                GymBranch sessionBranch = session.getMembership().getBranch();
                if (branch == null || sessionBranch == null || !branch.getId().equals(sessionBranch.getId())) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Staff can only manage sessions at their assigned branch");
                }
            }
            return session;
        }
    }

    private PersonalTrainer requirePtProfile(UserAccount user) {
        return pts.findByUserAccountId(user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PT profile not found"));
    }

    private GymOrganization requireOrg(UserAccount actor) {
        if (actor.getOrganization() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Account is not assigned to a gym organization");
        }
        return actor.getOrganization();
    }

    private BigDecimal calculateEffectiveSessionFee(Membership membership, PersonalTrainer pt) {
        if (membership != null && membership.getPtSessionsIncluded() != null && membership.getPtSessionsIncluded() > 0) {
            BigDecimal ptTotal;
            if (membership.getPlan() != null && membership.getPlan().getPtFeeIncluded() != null
                    && membership.getPlan().getPtFeeIncluded().compareTo(BigDecimal.ZERO) > 0) {
                ptTotal = membership.getPlan().getPtFeeIncluded();
                if (membership.getAmountPaid() != null && membership.getAmountPaid().compareTo(BigDecimal.ZERO) > 0
                        && ptTotal.compareTo(membership.getAmountPaid()) > 0) {
                    ptTotal = membership.getAmountPaid();
                }
            } else if (membership.getAmountPaid() != null && membership.getAmountPaid().compareTo(BigDecimal.ZERO) > 0) {
                ptTotal = membership.getAmountPaid();
            } else if (membership.getListPrice() != null && membership.getListPrice().compareTo(BigDecimal.ZERO) > 0) {
                ptTotal = membership.getListPrice();
            } else {
                return pt.getSessionFeePerHour();
            }
            return ptTotal.divide(BigDecimal.valueOf(membership.getPtSessionsIncluded()), 2, RoundingMode.HALF_UP);
        }
        return pt.getSessionFeePerHour();
    }

    public PtSessionDto toSessionDto(PtSession s) {
        Membership m = s.getMembership();
        return new PtSessionDto(s.getId(),
                s.getPersonalTrainer().getId(),
                s.getPersonalTrainer().getUserAccount().getFullName(),
                m.getId(), m.getMember().getId(), m.getMember().getFullName(),
                s.getScheduledDate(), s.getStartTime(), s.getEndTime(),
                s.getStatus().name(), s.getNotes(), s.getCancelReason(),
                s.getCompletedAt(), s.getSessionFeeSnapshot(),
                s.getCommissionRateSnapshot(), s.getCreatedAt(),
                s.getVerificationMethod());
    }
}
