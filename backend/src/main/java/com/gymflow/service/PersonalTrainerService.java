package com.gymflow.service;

import com.gymflow.domain.*;
import com.gymflow.repository.*;
import static com.gymflow.web.ApiDtos.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class PersonalTrainerService {
    private final UserRepository users;
    private final PersonalTrainerRepository pts;
    private final PtAvailabilityRepository availabilities;
    private final PtSessionRepository sessions;
    private final PtEarningRepository earnings;
    private final PtPayoutRepository payouts;
    private final BranchRepository branches;
    private final PasswordEncoder passwordEncoder;
    private final ActionHistoryService actionHistory;
    private final GymService gymService;
    private final MembershipRepository memberships;

    public PersonalTrainerService(UserRepository users, PersonalTrainerRepository pts,
            PtAvailabilityRepository availabilities, PtSessionRepository sessions,
            PtEarningRepository earnings, PtPayoutRepository payouts,
            BranchRepository branches, PasswordEncoder passwordEncoder,
            ActionHistoryService actionHistory, GymService gymService,
            MembershipRepository memberships) {
        this.users = users;
        this.pts = pts;
        this.availabilities = availabilities;
        this.sessions = sessions;
        this.earnings = earnings;
        this.payouts = payouts;
        this.branches = branches;
        this.passwordEncoder = passwordEncoder;
        this.actionHistory = actionHistory;
        this.gymService = gymService;
        this.memberships = memberships;
    }

    @Transactional(readOnly = true)
    public List<PtProfileDto> listPts(String ownerEmail) {
        UserAccount owner = gymService.account(ownerEmail);
        GymOrganization org = requireOrg(owner);
        return pts.findByOrganizationIdOrderByUserAccountFullNameAsc(org.getId())
                .stream().map(this::toPtDto).toList();
    }

    @Transactional(readOnly = true)
    public List<PtProfileDto> listActivePts(String actorEmail) {
        UserAccount actor = gymService.account(actorEmail);
        GymOrganization org = requireOrg(actor);
        return pts.findByOrganizationIdAndActiveTrueOrderByUserAccountFullNameAsc(org.getId())
                .stream().map(this::toPtDto).toList();
    }

    public PtProfileDto createPt(String ownerEmail, PtRegistrationRequest req) {
        UserAccount owner = gymService.account(ownerEmail);
        GymOrganization org = requireOrg(owner);
        if (users.existsByEmailIgnoreCase(req.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "An account with this email already exists");
        }
        GymBranch branch = req.branchId() == null ? null
                : branches.findByIdAndOrganizationIdAndActiveTrue(req.branchId(), org.getId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Branch not found"));
        UserAccount user = users.save(new UserAccount(req.fullName(), req.email(), req.phoneNumber(),
                passwordEncoder.encode(req.password()), Role.PERSONAL_TRAINER, org, branch));
        PersonalTrainer pt = pts.save(new PersonalTrainer(user, org, branch,
                req.specializations(), req.bio(), req.sessionFeePerHour(), req.commissionPercent()));
        actionHistory.record(owner, "PT_CREATED", "PERSONAL_TRAINER", pt.getId(), null, null, toPtDto(pt));
        return toPtDto(pt);
    }

    public PtProfileDto updatePt(String ownerEmail, Long ptId, PtUpdateRequest req) {
        UserAccount owner = gymService.account(ownerEmail);
        GymOrganization org = requireOrg(owner);
        PersonalTrainer pt = pts.findByIdAndOrganizationId(ptId, org.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PT not found"));
        PtProfileDto before = toPtDto(pt);
        GymBranch branch = req.branchId() == null ? null
                : branches.findByIdAndOrganizationIdAndActiveTrue(req.branchId(), org.getId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Branch not found"));
        pt.update(branch, req.specializations(), req.bio(), req.sessionFeePerHour(), req.commissionPercent());
        // also update the user account's branch assignment
        pt.getUserAccount().assignLocation(org, branch);
        actionHistory.record(owner, "PT_UPDATED", "PERSONAL_TRAINER", ptId, null, before, toPtDto(pt));
        return toPtDto(pt);
    }

    public PtProfileDto activatePt(String ownerEmail, Long ptId) {
        PersonalTrainer pt = findPtForOwner(ownerEmail, ptId);
        pt.activate();
        return toPtDto(pt);
    }

    public PtProfileDto deactivatePt(String ownerEmail, Long ptId) {
        PersonalTrainer pt = findPtForOwner(ownerEmail, ptId);
        pt.deactivate();
        return toPtDto(pt);
    }

    @Transactional(readOnly = true)
    public PtProfileDto getMyProfile(String ptEmail) {
        UserAccount user = gymService.account(ptEmail);
        return pts.findByUserAccountId(user.getId())
                .map(this::toPtDto)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PT profile not found"));
    }

    @Transactional(readOnly = true)
    public List<MembershipDto> getMyTrainees(String ptEmail) {
        UserAccount user = gymService.account(ptEmail);
        PersonalTrainer pt = pts.findByUserAccountId(user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PT profile not found"));
        List<Membership> items = memberships.findByPersonalTrainerIdOrderByCreatedAtDesc(pt.getId());
        return gymService.toMembershipDtos(items);
    }

    // ─── Availability management ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PtAvailabilityDto> getAvailability(String ptEmail) {
        UserAccount user = gymService.account(ptEmail);
        PersonalTrainer pt = pts.findByUserAccountId(user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PT profile not found"));
        return availabilities.findByPersonalTrainerIdOrderByDayOfWeekAscStartTimeAsc(pt.getId())
                .stream().map(this::toAvailabilityDto).toList();
    }

    @Transactional(readOnly = true)
    public List<PtAvailabilityDto> getPtAvailability(String ownerEmail, Long ptId) {
        UserAccount owner = gymService.account(ownerEmail);
        GymOrganization org = requireOrg(owner);
        PersonalTrainer pt = pts.findByIdAndOrganizationId(ptId, org.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PT not found"));
        return availabilities.findByPersonalTrainerIdOrderByDayOfWeekAscStartTimeAsc(pt.getId())
                .stream().map(this::toAvailabilityDto).toList();
    }

    public List<PtAvailabilityDto> setAvailability(String ptEmail, List<PtAvailabilityRequest> reqs) {
        UserAccount user = gymService.account(ptEmail);
        PersonalTrainer pt = pts.findByUserAccountId(user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PT profile not found"));
        availabilities.deleteByPersonalTrainerId(pt.getId());
        List<PtAvailability> saved = reqs.stream().map(r ->
                availabilities.save(new PtAvailability(pt, r.dayOfWeek(), r.startTime(),
                        r.endTime(), r.effectiveFrom(), r.effectiveTo()))).toList();
        return saved.stream().map(this::toAvailabilityDto).toList();
    }

    // ─── Earnings ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PtEarningDto> getMyEarnings(String ptEmail) {
        UserAccount user = gymService.account(ptEmail);
        PersonalTrainer pt = pts.findByUserAccountId(user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PT profile not found"));
        return earnings.findByPersonalTrainerIdOrderByCreatedAtDesc(pt.getId())
                .stream().map(this::toEarningDto).toList();
    }

    @Transactional(readOnly = true)
    public List<PtEarningDto> getPtEarnings(String ownerEmail, Long ptId) {
        PersonalTrainer pt = findPtForOwner(ownerEmail, ptId);
        return earnings.findByPersonalTrainerIdOrderByCreatedAtDesc(pt.getId())
                .stream().map(this::toEarningDto).toList();
    }

    // ─── Payout management ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PtPayoutDto> getMyPayouts(String ptEmail) {
        UserAccount user = gymService.account(ptEmail);
        PersonalTrainer pt = pts.findByUserAccountId(user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PT profile not found"));
        return payouts.findByPersonalTrainerIdOrderByCreatedAtDesc(pt.getId())
                .stream().map(this::toPayoutDto).toList();
    }

    @Transactional(readOnly = true)
    public List<PtPayoutDto> getAllPayouts(String ownerEmail) {
        UserAccount owner = gymService.account(ownerEmail);
        GymOrganization org = requireOrg(owner);
        return payouts.findByPersonalTrainerOrganizationIdOrderByCreatedAtDesc(org.getId())
                .stream().map(this::toPayoutDto).toList();
    }

    public PtPayoutDto createPayout(String ownerEmail, Long ptId, PtPayoutCreateRequest req) {
        PersonalTrainer pt = findPtForOwner(ownerEmail, ptId);
        List<PtEarning> unpaid = earnings.findByPersonalTrainerIdAndPayoutIsNullOrderByCreatedAtDesc(pt.getId());
        // Filter by period
        List<PtEarning> inPeriod = unpaid.stream()
                .filter(e -> {
                    LocalDate d = e.getPtSession().getScheduledDate();
                    return !d.isBefore(req.periodFrom()) && !d.isAfter(req.periodTo());
                }).toList();
        if (inPeriod.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No unpaid earnings found in this period");
        }
        BigDecimal total = inPeriod.stream().map(PtEarning::getPtCommission)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        PtPayout payout = payouts.save(new PtPayout(pt, req.periodFrom(), req.periodTo(),
                total, req.notes()));
        inPeriod.forEach(e -> e.assignPayout(payout));
        return toPayoutDto(payout);
    }

    public PtPayoutDto markPaid(String ownerEmail, Long payoutId) {
        UserAccount owner = gymService.account(ownerEmail);
        GymOrganization org = requireOrg(owner);
        PtPayout payout = payouts.findByIdAndPersonalTrainerOrganizationId(payoutId, org.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payout not found"));
        if ("PAID".equals(payout.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Payout is already marked as paid");
        }
        payout.markPaid(owner.getFullName());
        return toPayoutDto(payout);
    }

    // ─── PT Analytics ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PtAnalyticsDto ptAnalytics(String ownerEmail, LocalDate from, LocalDate to) {
        UserAccount owner = gymService.account(ownerEmail);
        GymOrganization org = requireOrg(owner);
        long activePts = pts.findByOrganizationIdAndActiveTrueOrderByUserAccountFullNameAsc(org.getId()).size();
        long totalPts = pts.findByOrganizationIdOrderByUserAccountFullNameAsc(org.getId()).size();
        BigDecimal gross = earnings.sumGrossRevenueByOrgAndPeriod(org.getId(), from, to);
        BigDecimal commission = earnings.sumEarningsByOrgAndPeriod(org.getId(), from, to);
        BigDecimal gymRetained = earnings.sumGymRetainedByOrgAndPeriod(org.getId(), from, to);
        // Pending payouts across all PTs
        BigDecimal pending = pts.findByOrganizationIdAndActiveTrueOrderByUserAccountFullNameAsc(org.getId())
                .stream().map(pt -> earnings.sumPendingEarnings(pt.getId()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        // Session counts
        long completed = sessions.countByPersonalTrainerOrganizationIdAndStatusAndPeriod(org.getId(),
                PtSessionStatus.COMPLETED, from, to);
        long cancelled = sessions.countByPersonalTrainerOrganizationIdAndStatusAndPeriod(org.getId(),
                PtSessionStatus.CANCELLED, from, to);
        long missed = sessions.countByPersonalTrainerOrganizationIdAndStatusAndPeriod(org.getId(),
                PtSessionStatus.MISSED, from, to);

        // Contracted PT Package metrics (reflects active PT memberships immediately)
        long activeTrainees = memberships.countActivePtTrainees(org.getId());
        BigDecimal contractedPtRevenue = memberships.sumContractedPtRevenue(org.getId());
        long allocatedSessions = memberships.sumAllocatedPtSessions(org.getId());
        long usedSessions = memberships.sumUsedPtSessions(org.getId());
        long remainingSessions = Math.max(0, allocatedSessions - usedSessions);

        return new PtAnalyticsDto(activePts, totalPts,
                gross == null ? BigDecimal.ZERO : gross,
                commission == null ? BigDecimal.ZERO : commission,
                gymRetained == null ? BigDecimal.ZERO : gymRetained,
                pending == null ? BigDecimal.ZERO : pending,
                completed, cancelled, missed,
                activeTrainees, contractedPtRevenue, allocatedSessions, remainingSessions);
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private PersonalTrainer findPtForOwner(String ownerEmail, Long ptId) {
        UserAccount owner = gymService.account(ownerEmail);
        GymOrganization org = requireOrg(owner);
        return pts.findByIdAndOrganizationId(ptId, org.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PT not found"));
    }

    private GymOrganization requireOrg(UserAccount actor) {
        if (actor.getOrganization() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Account is not assigned to a gym organization");
        }
        return actor.getOrganization();
    }

    public PtProfileDto toPtDto(PersonalTrainer pt) {
        UserAccount ua = pt.getUserAccount();
        return new PtProfileDto(pt.getId(), ua.getId(), ua.getFullName(), ua.getEmail(),
                ua.getPhoneNumber(),
                pt.getBranch() == null ? null : pt.getBranch().getId(),
                pt.getBranch() == null ? null : pt.getBranch().getName(),
                pt.getSpecializations(), pt.getBio(),
                pt.getSessionFeePerHour(), pt.getCommissionPercent(),
                pt.isActive(), pts.countActiveMembers(pt.getId()),
                pt.getCreatedAt());
    }

    private PtAvailabilityDto toAvailabilityDto(PtAvailability a) {
        return new PtAvailabilityDto(a.getId(), a.getDayOfWeek(),
                a.getStartTime(), a.getEndTime(), a.getEffectiveFrom(), a.getEffectiveTo());
    }

    public PtEarningDto toEarningDto(PtEarning e) {
        return new PtEarningDto(e.getId(), e.getPtSession().getId(),
                e.getPtSession().getScheduledDate(),
                e.getPtSession().getMembership().getMember().getFullName(),
                e.getGrossRevenue(), e.getPtCommission(), e.getGymRetained(),
                e.getCommissionRate(),
                e.getPayout() == null ? null : e.getPayout().getId(),
                e.getCreatedAt());
    }

    public PtPayoutDto toPayoutDto(PtPayout p) {
        UserAccount ua = p.getPersonalTrainer().getUserAccount();
        return new PtPayoutDto(p.getId(), p.getPersonalTrainer().getId(),
                ua.getFullName(), p.getPeriodFrom(), p.getPeriodTo(),
                p.getTotalEarnings(), p.getStatus(), p.getPaidAt(),
                p.getPaidByName(), p.getNotes(), p.getCreatedAt());
    }
}
