package com.gymflow.service;

import com.gymflow.domain.ActivationCode;
import com.gymflow.domain.ActivationCodeStatus;
import com.gymflow.domain.AppFeature;
import com.gymflow.domain.GymOrganization;
import com.gymflow.domain.Role;
import com.gymflow.domain.SubscriptionStatus;
import com.gymflow.domain.SubscriptionTier;
import com.gymflow.domain.UpgradeRequest;
import com.gymflow.domain.UpgradeRequestStatus;
import com.gymflow.domain.UserAccount;
import com.gymflow.repository.ActivationCodeRepository;
import com.gymflow.repository.BranchRepository;
import com.gymflow.repository.OrganizationRepository;
import com.gymflow.repository.UpgradeRequestRepository;
import com.gymflow.repository.UserRepository;
import com.gymflow.repository.*;
import com.gymflow.domain.*;
import static com.gymflow.web.ApiDtos.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.ArrayList;

@Service
@Transactional(readOnly = true)
public class SubscriptionService {
    private final UserRepository users;
    private final OrganizationRepository organizations;
    private final ActivationCodeRepository activationCodes;
    private final BranchRepository branches;
    private final UpgradeRequestRepository upgradeRequests;
    private final PlatformEmailService platformEmailService;
    private final MembershipRepository memberships;
    private final PlanRepository plans;
    private final PersonalTrainerRepository trainers;
    private final PtSessionRepository ptSessions;
    private final ActionHistoryRepository actionHistories;
    private final RenewalReminderRepository renewalReminders;
    private final PtEarningRepository ptEarnings;
    private final SecureRandom secureRandom = new SecureRandom();

    public SubscriptionService(UserRepository users,
                               OrganizationRepository organizations,
                               ActivationCodeRepository activationCodes,
                               BranchRepository branches,
                               UpgradeRequestRepository upgradeRequests,
                               PlatformEmailService platformEmailService,
                               MembershipRepository memberships,
                               PlanRepository plans,
                               PersonalTrainerRepository trainers,
                               PtSessionRepository ptSessions,
                               ActionHistoryRepository actionHistories,
                               RenewalReminderRepository renewalReminders,
                               PtEarningRepository ptEarnings) {
        this.users = users;
        this.organizations = organizations;
        this.activationCodes = activationCodes;
        this.branches = branches;
        this.upgradeRequests = upgradeRequests;
        this.platformEmailService = platformEmailService;
        this.memberships = memberships;
        this.plans = plans;
        this.trainers = trainers;
        this.ptSessions = ptSessions;
        this.actionHistories = actionHistories;
        this.renewalReminders = renewalReminders;
        this.ptEarnings = ptEarnings;
    }

    public void requireFeature(String userEmail, AppFeature feature) {
        UserAccount user = users.findByEmailIgnoreCase(userEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        requireFeature(user, feature);
    }

    public void requireFeature(UserAccount actor, AppFeature feature) {
        if (actor.getOrganization() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No gym organization associated with this account");
        }
        GymOrganization organization = organizations.findById(actor.getOrganization().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Gym organization not found"));
        if (!organization.hasFeature(feature)) {
            String featureName = switch (feature) {
                case CUSTOM_BRANDING -> "Custom Branding (Logo & Gym Name)";
                case RECEIPT_IMAGE_UPLOAD -> "Receipt Image Upload";
                case ACTION_HISTORY -> "Audit History & Logs";
                case PERSONAL_TRAINERS -> "Personal Trainer Management";
                case ANALYTICS -> "Advanced Analytics & Business Intelligence";
                default -> feature.name();
            };
            String requiredTier = switch (feature) {
                case CUSTOM_BRANDING -> "Starter, Pro, or Enterprise";
                case RECEIPT_IMAGE_UPLOAD, ACTION_HISTORY -> "Pro or Enterprise";
                case PERSONAL_TRAINERS, ANALYTICS -> "Enterprise";
                default -> "Premium";
            };
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    featureName + " is a Premium feature. Please upgrade your gym's subscription to access it (" + requiredTier + " plan required).");
        }
    }

    @Transactional(readOnly = true)
    public SubscriptionStatusDto getSubscription(String userEmail) {
        UserAccount user = users.findByEmailIgnoreCase(userEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        GymOrganization org = user.getOrganization();
        if (org == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Gym organization not found");
        }
        return toSubscriptionDto(org);
    }

    @Transactional
    public SubscriptionStatusDto upgrade(String userEmail, SubscriptionTier targetTier) {
        UserAccount user = users.findByEmailIgnoreCase(userEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        GymOrganization org = user.getOrganization();
        if (org == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Gym organization not found");
        }
        SubscriptionTier tier = targetTier == null ? SubscriptionTier.PREMIUM : targetTier;
        org.upgradeTier(tier);
        organizations.save(org);
        return toSubscriptionDto(org);
    }

    @Transactional
    public SubscriptionStatusDto redeemCode(String userEmail, String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Activation code cannot be empty");
        }
        UserAccount user = users.findByEmailIgnoreCase(userEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        if (user.getRole() != Role.OWNER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only gym owners can redeem activation codes.");
        }
        if (user.getOrganization() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Gym organization not found");
        }
        ActivationCode code = activationCodes.findByCodeIgnoreCase(rawCode.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid activation code."));
        if (!code.isRedeemable()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "This activation code is expired, revoked, or has already reached its redemption limit.");
        }

        GymOrganization org = organizations.findById(user.getOrganization().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Gym organization not found"));

        org.applyActivationCode(code.getTier(), code.getDurationDays());
        code.redeem(org, user.getEmail());

        organizations.save(org);
        activationCodes.save(code);
        return toSubscriptionDto(org);
    }

    @Transactional
    public SubscriptionStatusDto cancelSubscription(String userEmail, boolean immediate) {
        UserAccount user = users.findByEmailIgnoreCase(userEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        if (user.getRole() != Role.OWNER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only gym owners can cancel subscriptions.");
        }
        if (user.getOrganization() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Gym organization not found");
        }
        GymOrganization org = organizations.findById(user.getOrganization().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Gym organization not found"));

        org.cancelSubscription(immediate);
        organizations.save(org);
        return toSubscriptionDto(org);
    }

    @Transactional
    public SubscriptionStatusDto reactivateSubscription(String userEmail) {
        UserAccount user = users.findByEmailIgnoreCase(userEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        if (user.getRole() != Role.OWNER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only gym owners can reactivate subscriptions.");
        }
        if (user.getOrganization() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Gym organization not found");
        }
        GymOrganization org = organizations.findById(user.getOrganization().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Gym organization not found"));

        org.reactivateSubscription();
        organizations.save(org);
        return toSubscriptionDto(org);
    }

    // ─── Platform Super-Admin Operations ─────────────────────────────────────────

    @Transactional
    public ActivationCodeDto generateActivationCode(String adminEmail, SubscriptionTier tier, Integer durationDays, String notes) {
        String generatedKey = generateKeyString(tier != null ? tier : SubscriptionTier.PREMIUM);
        while (activationCodes.existsByCodeIgnoreCase(generatedKey)) {
            generatedKey = generateKeyString(tier != null ? tier : SubscriptionTier.PREMIUM);
        }
        ActivationCode code = new ActivationCode(
                generatedKey,
                tier != null ? tier : SubscriptionTier.PREMIUM,
                durationDays,
                notes,
                adminEmail
        );
        ActivationCode saved = activationCodes.save(code);
        return toActivationCodeDto(saved);
    }

    public List<ActivationCodeDto> listActivationCodes() {
        return activationCodes.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toActivationCodeDto)
                .toList();
    }

    @Transactional
    public ActivationCodeDto revokeActivationCode(Long codeId) {
        ActivationCode code = activationCodes.findById(codeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Activation code not found"));
        code.revoke();
        ActivationCode saved = activationCodes.save(code);
        return toActivationCodeDto(saved);
    }

    public PlatformMetricsDto getPlatformMetrics() {
        long totalGyms = organizations.count();
        List<GymOrganization> allOrgs = organizations.findAll();
        long activePremiumGyms = allOrgs.stream()
                .filter(o -> o.getSubscriptionTier() != SubscriptionTier.STANDARD && o.getSubscriptionStatus() == SubscriptionStatus.ACTIVE)
                .count();
        long standardGyms = totalGyms - activePremiumGyms;
        long totalMembersAcrossGyms = users.countByRole(Role.MEMBER);
        long totalBranches = branches.count();
        long availableCodes = activationCodes.countByStatus(ActivationCodeStatus.AVAILABLE);

        BigDecimal totalPlatformGmv = memberships.sumAllPlatformRevenue();
        BigDecimal platformEstimatedMrr = allOrgs.stream()
                .filter(o -> o.getSubscriptionStatus() == SubscriptionStatus.ACTIVE)
                .map(o -> o.getSubscriptionTier() != null ? o.getSubscriptionTier().monthlyPrice() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long totalActiveMembershipsAcrossGyms = memberships.countByStatus(MembershipStatus.ACTIVE);
        long totalPtSessionsAcrossGyms = ptSessions.count();
        long totalPlansAcrossGyms = plans.count();

        long healthyGymsCount = 0;
        long atRiskGymsCount = 0;
        long upsellCandidatesCount = 0;

        for (GymOrganization org : allOrgs) {
            PlatformOrganizationDto dto = toPlatformOrgDto(org);
            if ("HEALTHY".equals(dto.healthStatus())) healthyGymsCount++;
            else if ("AT_RISK".equals(dto.healthStatus())) atRiskGymsCount++;

            if (dto.decisionInsights().stream().anyMatch(i -> i.contains("Upsell") || i.contains("Upgrade") || i.contains("Opportunity"))) {
                upsellCandidatesCount++;
            }
        }

        return new PlatformMetricsDto(
                totalGyms,
                activePremiumGyms,
                standardGyms,
                totalMembersAcrossGyms,
                totalBranches,
                availableCodes,
                totalPlatformGmv != null ? totalPlatformGmv : BigDecimal.ZERO,
                platformEstimatedMrr != null ? platformEstimatedMrr : BigDecimal.ZERO,
                totalActiveMembershipsAcrossGyms,
                totalPtSessionsAcrossGyms,
                totalPlansAcrossGyms,
                healthyGymsCount,
                atRiskGymsCount,
                upsellCandidatesCount
        );
    }

    public List<PlatformOrganizationDto> listAllOrganizations(String search) {
        List<GymOrganization> allOrgs = organizations.findAll();
        return allOrgs.stream()
                .filter(o -> {
                    if (search == null || search.isBlank()) return true;
                    String s = search.toLowerCase().trim();
                    return o.getName().toLowerCase().contains(s) || o.getCode().toLowerCase().contains(s);
                })
                .map(this::toPlatformOrgDto)
                .toList();
    }

    @Transactional
    public PlatformOrganizationDto updateOrganizationSubscriptionAdmin(Long organizationId,
                                                                       SubscriptionTier tier,
                                                                       SubscriptionStatus status,
                                                                       Instant expiresAt) {
        GymOrganization org = organizations.findById(organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Gym organization not found"));
        org.updateSubscription(tier, status, expiresAt);
        organizations.save(org);
        return toPlatformOrgDto(org);
    }

    @Transactional
    public PlatformOrganizationDto extendOrganizationSubscription(Long organizationId, Integer days) {
        GymOrganization org = organizations.findById(organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Gym organization not found"));
        int extensionDays = (days != null && days > 0) ? days : 30;
        Instant now = Instant.now();
        Instant currentExpiry = org.getSubscriptionExpiresAt();
        Instant newExpiry = (currentExpiry != null && currentExpiry.isAfter(now))
                ? currentExpiry.plus(Duration.ofDays(extensionDays))
                : now.plus(Duration.ofDays(extensionDays));

        org.updateSubscription(SubscriptionTier.PREMIUM, SubscriptionStatus.ACTIVE, newExpiry);
        organizations.save(org);
        return toPlatformOrgDto(org);
    }

    @Transactional
    public PlatformOrganizationDto suspendOrganization(Long organizationId, boolean suspend) {
        GymOrganization org = organizations.findById(organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Gym organization not found"));
        SubscriptionStatus newStatus = suspend ? SubscriptionStatus.SUSPENDED : SubscriptionStatus.ACTIVE;
        org.updateSubscription(null, newStatus, org.getSubscriptionExpiresAt());
        organizations.save(org);
        return toPlatformOrgDto(org);
    }

    @Transactional
    public SubscriptionStatusDto updateOrganizationTier(Long organizationId, SubscriptionTier tier,
                                                        SubscriptionStatus status, Instant expiresAt) {
        GymOrganization org = organizations.findById(organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Gym organization not found"));
        org.updateSubscription(tier, status, expiresAt);
        organizations.save(org);
        return toSubscriptionDto(org);
    }

    // ─── Upgrade Request Operations (B2B SaaS Lifecycle) ─────────────────────────

    public SaasUpgradeQuoteDto quoteSaasUpgrade(String userEmail, SubscriptionTier targetTier) {
        UserAccount user = users.findByEmailIgnoreCase(userEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        GymOrganization org = user.getOrganization();
        if (org == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Gym organization not found");
        }
        SubscriptionTier currentTier = org.getSubscriptionTier() != null ? org.getSubscriptionTier() : SubscriptionTier.STANDARD;
        SubscriptionTier resolvedTarget = (targetTier != null) ? targetTier : SubscriptionTier.PRO;

        BigDecimal currentMonthlyPrice = currentTier.monthlyPrice();
        BigDecimal targetMonthlyPrice = resolvedTarget.monthlyPrice();

        Instant now = Instant.now();
        int remainingDays = 30;
        if (org.getSubscriptionExpiresAt() != null && org.getSubscriptionExpiresAt().isAfter(now)) {
            long days = ChronoUnit.DAYS.between(now, org.getSubscriptionExpiresAt());
            remainingDays = (int) Math.min(30, Math.max(1, days));
        }

        BigDecimal priceDifference = targetMonthlyPrice.subtract(currentMonthlyPrice).max(BigDecimal.ZERO);
        BigDecimal dailyRateDiff = priceDifference.divide(BigDecimal.valueOf(30), 4, RoundingMode.HALF_UP);
        BigDecimal estimatedTopUp = dailyRateDiff.multiply(BigDecimal.valueOf(remainingDays)).setScale(2, RoundingMode.HALF_UP);

        return new SaasUpgradeQuoteDto(
                currentTier,
                resolvedTarget,
                currentMonthlyPrice,
                targetMonthlyPrice,
                remainingDays,
                estimatedTopUp,
                LocalDate.now()
        );
    }


    @Transactional
    public UpgradeRequestDto submitUpgradeRequest(String userEmail, SubmitUpgradeRequestDto request) {
        UserAccount user = users.findByEmailIgnoreCase(userEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        if (user.getRole() != Role.OWNER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only gym owners can submit upgrade requests.");
        }
        GymOrganization org = user.getOrganization();
        if (org == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Gym organization not found");
        }

        if (upgradeRequests.findTopByOrganizationIdAndStatusOrderByCreatedAtDesc(org.getId(), UpgradeRequestStatus.PENDING).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "An upgrade request for your gym is already pending review. Our accounts team will contact you shortly.");
        }

        SubscriptionTier targetTier = SubscriptionTier.PREMIUM;
        if (request.targetTier() != null && !request.targetTier().isBlank()) {
            try {
                targetTier = SubscriptionTier.valueOf(request.targetTier().trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
            }
        } else if (request.billingPlan() != null && request.billingPlan().toUpperCase().contains("ENTERPRISE")) {
            targetTier = SubscriptionTier.ENTERPRISE;
        } else if (request.billingPlan() != null && request.billingPlan().toUpperCase().contains("STARTER")) {
            targetTier = SubscriptionTier.STARTER;
        }

        UpgradeRequest upgradeReq = new UpgradeRequest(
                org,
                user.getEmail(),
                user.getFullName(),
                user.getPhoneNumber(),
                targetTier,
                request.durationDays(),
                request.billingPlan(),
                request.paymentMethod(),
                request.gstNumber(),
                request.billingAddress(),
                request.notes()
        );

        if (request.estimatedTopUp() != null) {
            upgradeReq.setEstimatedTopUp(request.estimatedTopUp());
        }
        UpgradeRequest saved = upgradeRequests.save(upgradeReq);
        platformEmailService.sendUpgradeRequestConfirmationToOwner(saved);
        platformEmailService.sendUpgradeRequestAlertToAdmin(saved);

        return toUpgradeRequestDto(saved);
    }

    public List<UpgradeRequestDto> listMyUpgradeRequests(String userEmail) {
        UserAccount user = users.findByEmailIgnoreCase(userEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        GymOrganization org = user.getOrganization();
        if (org == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Gym organization not found");
        }
        return upgradeRequests.findByOrganizationIdOrderByCreatedAtDesc(org.getId())
                .stream()
                .map(this::toUpgradeRequestDto)
                .toList();
    }

    public List<UpgradeRequestDto> listAllUpgradeRequests(UpgradeRequestStatus status) {
        List<UpgradeRequest> requests = status == null
                ? upgradeRequests.findAllByOrderByCreatedAtDesc()
                : upgradeRequests.findByStatusOrderByCreatedAtDesc(status);
        return requests.stream()
                .map(this::toUpgradeRequestDto)
                .toList();
    }

    @Transactional
    public UpgradeRequestDto approveUpgradeRequestWithKey(Long requestId, String adminEmail) {
        UpgradeRequest req = upgradeRequests.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Upgrade request not found"));
        if (req.getStatus() != UpgradeRequestStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upgrade request is already " + req.getStatus());
        }

        ActivationCodeDto code = generateActivationCode(
                adminEmail,
                req.getTargetTier(),
                req.getDurationDays(),
                "Issued for Upgrade Request REQ-" + req.getId() + " (" + req.getOrganization().getName() + ")"
        );

        req.markApprovedWithKey(code.code(), adminEmail);
        UpgradeRequest saved = upgradeRequests.save(req);

        platformEmailService.sendActivationKeyToOwner(saved, code.code(), req.getDurationDays());
        return toUpgradeRequestDto(saved);
    }

    @Transactional
    public UpgradeRequestDto approveUpgradeRequestDirect(Long requestId, Integer durationDaysOverride, String adminEmail) {
        UpgradeRequest req = upgradeRequests.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Upgrade request not found"));
        if (req.getStatus() != UpgradeRequestStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upgrade request is already " + req.getStatus());
        }

        int durationDays = (durationDaysOverride != null && durationDaysOverride > 0)
                ? durationDaysOverride
                : (req.getDurationDays() != null ? req.getDurationDays() : 365);

        GymOrganization org = req.getOrganization();
        org.applyActivationCode(req.getTargetTier(), durationDays);
        organizations.save(org);

        req.markApprovedDirect(adminEmail);
        UpgradeRequest saved = upgradeRequests.save(req);

        platformEmailService.sendDirectUpgradeNoticeToOwner(org, req.getRequesterEmail(), durationDays);
        return toUpgradeRequestDto(saved);
    }

    @Transactional
    public UpgradeRequestDto rejectUpgradeRequest(Long requestId, String reason, String adminEmail) {
        UpgradeRequest req = upgradeRequests.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Upgrade request not found"));
        if (req.getStatus() != UpgradeRequestStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upgrade request is already " + req.getStatus());
        }

        req.markRejected(reason, adminEmail);
        UpgradeRequest saved = upgradeRequests.save(req);
        return toUpgradeRequestDto(saved);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    private UpgradeRequestDto toUpgradeRequestDto(UpgradeRequest req) {
        return new UpgradeRequestDto(
                req.getId(),
                req.getOrganization() != null ? req.getOrganization().getId() : null,
                req.getOrganization() != null ? req.getOrganization().getName() : null,
                req.getOrganization() != null ? req.getOrganization().getCode() : null,
                req.getRequesterEmail(),
                req.getRequesterName(),
                req.getRequesterPhone(),
                req.getTargetTier(),
                req.getDurationDays(),
                req.getBillingPlanName(),
                req.getPaymentMethod(),
                req.getGstNumber(),
                req.getBillingAddress(),
                req.getNotes(),
                req.getStatus(),
                req.getIssuedActivationCode(),
                req.getReviewedBy(),
                req.getReviewedAt(),
                req.getRejectionReason(),
                req.getCreatedAt(),
                req.getEstimatedTopUp()
        );
    }

    private String generateKeyString(SubscriptionTier tier) {
        byte[] bytes = new byte[4];
        secureRandom.nextBytes(bytes);
        StringBuilder hex = new StringBuilder();
        for (byte b : bytes) {
            hex.append(String.format("%02X", b));
        }
        String prefix = switch (tier != null ? tier : SubscriptionTier.PRO) {
            case STARTER -> "GF-STR";
            case PRO, ENTERPRISE, PREMIUM -> "GF-PRO";
            default -> "GF-STD";
        };
        return prefix + "-" + hex.substring(0, 4) + "-" + hex.substring(4, 8);
    }

    private SubscriptionStatusDto toSubscriptionDto(GymOrganization org) {
        SubscriptionTier tier = org.getSubscriptionTier() != null ? org.getSubscriptionTier() : SubscriptionTier.STANDARD;
        return new SubscriptionStatusDto(
                org.getId(),
                org.getName(),
                org.getCode(),
                org.getLogoData(),
                org.getSubscriptionTier(),
                org.getSubscriptionStatus(),
                org.getSubscriptionExpiresAt(),
                org.hasFeature(AppFeature.CUSTOM_BRANDING),
                org.hasFeature(AppFeature.RECEIPT_IMAGE_UPLOAD),
                org.hasFeature(AppFeature.ACTION_HISTORY),
                org.hasFeature(AppFeature.PERSONAL_TRAINERS),
                org.hasFeature(AppFeature.ANALYTICS),
                org.getEnabledFeatures(),
                tier.maxBranches(),
                tier.maxStaff(),
                tier.maxMembers(),
                org.getCountryCode() != null ? org.getCountryCode() : "IN",
                org.getLegalJurisdiction() != null ? org.getLegalJurisdiction() : "INDIA_DPDP",
                org.getGrievanceOfficerEmail(),
                org.getDpoEmail()
        );
    }

    private ActivationCodeDto toActivationCodeDto(ActivationCode code) {
        return new ActivationCodeDto(
                code.getId(),
                code.getCode(),
                code.getTier(),
                code.getDurationDays(),
                code.getStatus(),
                code.getMaxRedemptions(),
                code.getRedemptionCount(),
                code.getNotes(),
                code.getRedeemedByOrganization() != null ? code.getRedeemedByOrganization().getId() : null,
                code.getRedeemedByOrganization() != null ? code.getRedeemedByOrganization().getName() : null,
                code.getRedeemedByEmail(),
                code.getRedeemedAt(),
                code.getCodeExpiresAt(),
                code.getCreatedAt(),
                code.getCreatedBy()
        );
    }

    @Transactional(readOnly = true)
    public PlatformGymDetailDto getOrganizationDetails(Long organizationId) {
        GymOrganization org = organizations.findById(organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Gym organization not found"));

        PlatformOrganizationDto orgDto = toPlatformOrgDto(org);

        UserAccount owner = users.findByOrganizationIdAndRoleOrderByFullNameAsc(org.getId(), Role.OWNER)
                .stream().findFirst().orElse(null);
        UserDto ownerDto = owner != null ? toUserDto(owner) : null;

        List<PlatformGymBranchDto> branchDtos = branches.findByOrganizationIdOrderByNameAsc(org.getId()).stream()
                .map(b -> {
                    long activeMembers = users.countByRoleAndOrganizationIdAndBranchId(Role.MEMBER, org.getId(), b.getId());
                    BigDecimal branchRevenue = memberships.sumAmountPaidByBranchId(b.getId());
                    if (branchRevenue == null) branchRevenue = BigDecimal.ZERO;
                    return new PlatformGymBranchDto(
                            b.getId(),
                            b.getName(),
                            b.getCode(),
                            b.getType() != null ? b.getType().name() : "COMPANY_OWNED",
                            b.getAddress(),
                            b.getPhoneNumber(),
                            activeMembers,
                            activeMembers,
                            branchRevenue
                    );
                }).toList();

        List<PlatformGymPlanDto> planDtos = plans.findByOrganizationIdOrderByPriceAsc(org.getId()).stream()
                .map(p -> new PlatformGymPlanDto(
                        p.getId(),
                        p.getName(),
                        p.getDescription(),
                        p.getPrice(),
                        p.getDurationMonths(),
                        p.getDurationDays(),
                        p.isActive(),
                        memberships.countActiveByPlanId(p.getId())
                )).toList();

        List<PlatformGymTrainerDto> trainerDtos = trainers.findByOrganizationIdOrderByUserAccountFullNameAsc(org.getId()).stream()
                .map(t -> {
                    long activeM = trainers.countActiveMembers(t.getId());
                    long totalS = ptSessions.countByPersonalTrainerId(t.getId());
                    BigDecimal pendingE = ptEarnings.sumPendingEarnings(t.getId());
                    if (pendingE == null) pendingE = BigDecimal.ZERO;
                    return new PlatformGymTrainerDto(
                            t.getId(),
                            t.getUserAccount() != null ? t.getUserAccount().getFullName() : "Trainer",
                            t.getUserAccount() != null ? t.getUserAccount().getEmail() : "",
                            t.getUserAccount() != null ? t.getUserAccount().getPhoneNumber() : "",
                            t.getSpecializations(),
                            t.getCommissionPercent(),
                            t.isActive(),
                            activeM,
                            totalS,
                            pendingE
                    );
                }).toList();

        List<PlatformGymStaffDto> staffDtos = users.findByOrganizationIdAndRoleOrderByFullNameAsc(org.getId(), Role.STAFF).stream()
                .map(u -> new PlatformGymStaffDto(
                        u.getId(),
                        u.getFullName(),
                        u.getEmail(),
                        u.getPhoneNumber(),
                        u.getRole(),
                        u.getBranch() != null ? u.getBranch().getName() : "All Branches"
                )).toList();

        List<PlatformGymActionDto> actionDtos = actionHistories.findTop250ByOrganizationIdOrderByOccurredAtDesc(org.getId()).stream()
                .limit(25)
                .map(a -> new PlatformGymActionDto(
                        a.getId(),
                        a.getActorEmail(),
                        a.getActorName(),
                        a.getAction(),
                        a.getEntityType(),
                        a.getEntityId(),
                        a.getOccurredAt(),
                        a.getReason()
                )).toList();

        BigDecimal totalRev = orgDto.totalRevenue();
        BigDecimal rev30 = orgDto.revenueLast30Days();
        BigDecimal discounts = orgDto.discountsGiven();
        BigDecimal estimatedTax = totalRev.multiply(new BigDecimal("0.18")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal avgTicket = orgDto.memberCount() > 0 
                ? totalRev.divide(BigDecimal.valueOf(orgDto.memberCount()), 2, RoundingMode.HALF_UP) 
                : BigDecimal.ZERO;
        BigDecimal arpu = orgDto.activeMemberCount() > 0 
                ? rev30.divide(BigDecimal.valueOf(orgDto.activeMemberCount()), 2, RoundingMode.HALF_UP) 
                : BigDecimal.ZERO;

        PlatformGymFinancialsDto financials = new PlatformGymFinancialsDto(
                totalRev, rev30, discounts, estimatedTax, avgTicket, arpu
        );

        long renewalRemindersSent = renewalReminders.countByMembershipMemberOrganizationId(org.getId());
        long expiredMembers = Math.max(0, orgDto.memberCount() - orgDto.activeMemberCount());

        PlatformGymServiceUsageDto serviceUsage = new PlatformGymServiceUsageDto(
                orgDto.memberCount(),
                orgDto.activeMemberCount(),
                expiredMembers,
                orgDto.branchCount(),
                orgDto.planCount(),
                orgDto.ptTrainerCount(),
                orgDto.ptSessionsConducted(),
                orgDto.actionHistoryCount(),
                renewalRemindersSent,
                orgDto.customBrandingActive()
        );

        Long daysUntilExpiry = null;
        if (org.getSubscriptionExpiresAt() != null) {
            daysUntilExpiry = ChronoUnit.DAYS.between(Instant.now(), org.getSubscriptionExpiresAt());
        }
        int scoreVal = orgDto.healthScore();
        String churnRisk = scoreVal < 45 ? "HIGH" : scoreVal < 75 ? "MEDIUM" : "LOW";

        List<String> positiveIndicators = new ArrayList<>();
        if (orgDto.activeMemberCount() > 0) positiveIndicators.add(orgDto.activeMemberCount() + " actively paying gym members on roster");
        if (rev30.compareTo(BigDecimal.ZERO) > 0) positiveIndicators.add("₹" + rev30 + " revenue recorded in previous 30 days");
        if (orgDto.ptTrainerCount() > 0) positiveIndicators.add("Personal Training service active with " + orgDto.ptTrainerCount() + " trainer(s)");
        if (orgDto.branchCount() > 1) positiveIndicators.add("Multi-facility operation across " + orgDto.branchCount() + " branches");
        if (orgDto.customBrandingActive()) positiveIndicators.add("White-label custom branding configured");

        List<String> riskFactors = new ArrayList<>();
        if (daysUntilExpiry != null && daysUntilExpiry <= 14) riskFactors.add("Subscription expiring in " + daysUntilExpiry + " days");
        if (org.getSubscriptionStatus() == SubscriptionStatus.SUSPENDED) riskFactors.add("Tenant is currently SUSPENDED");
        if (rev30.compareTo(BigDecimal.ZERO) == 0 && orgDto.memberCount() > 0) riskFactors.add("Zero sales transactions recorded in last 30 days");
        if (expiredMembers > orgDto.activeMemberCount()) riskFactors.add("High expired membership ratio vs active members");

        PlatformGymDecisionSupportDto decisionSupport = new PlatformGymDecisionSupportDto(
                orgDto.healthScore(),
                orgDto.healthStatus(),
                daysUntilExpiry,
                churnRisk,
                orgDto.decisionInsights(),
                positiveIndicators,
                riskFactors
        );

        return new PlatformGymDetailDto(
                orgDto,
                ownerDto,
                branchDtos,
                planDtos,
                trainerDtos,
                staffDtos,
                actionDtos,
                financials,
                serviceUsage,
                decisionSupport
        );
    }

    private UserDto toUserDto(UserAccount user) {
        GymOrganization org = user.getOrganization();
        GymBranch branch = user.getBranch();
        return new UserDto(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getRole(),
                branch != null ? branch.getId() : null,
                branch != null ? branch.getName() : null,
                org != null ? org.getId() : null,
                org != null ? org.getName() : null,
                org != null ? org.getLogoData() : null,
                org == null ? SubscriptionTier.STANDARD : org.getSubscriptionTier(),
                org == null ? List.of("CORE_MANAGEMENT") : org.getEnabledFeatures(),
                org != null ? org.getSubscriptionStatus() : null,
                org != null ? org.getSubscriptionExpiresAt() : null,
                user.getMemberPin(),
                org != null && org.getCountryCode() != null ? org.getCountryCode() : "IN",
                org != null && org.getLegalJurisdiction() != null ? org.getLegalJurisdiction() : "INDIA_DPDP"
        );
    }

    private PlatformOrganizationDto toPlatformOrgDto(GymOrganization org) {
        UserAccount owner = users.findByOrganizationIdAndRoleOrderByFullNameAsc(org.getId(), Role.OWNER)
                .stream().findFirst().orElse(null);
        long branchCount = branches.countByOrganizationId(org.getId());
        long memberCount = users.countByRoleAndOrganizationId(Role.MEMBER, org.getId());
        long activeMemberCount = memberships.countByOrganizationIdAndStatus(org.getId(), MembershipStatus.ACTIVE);
        BigDecimal totalRevenue = memberships.sumAmountPaidByOrganizationId(org.getId());
        if (totalRevenue == null) totalRevenue = BigDecimal.ZERO;
        
        Instant thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS);
        BigDecimal revenueLast30Days = memberships.sumAmountPaidByOrganizationIdSince(org.getId(), thirtyDaysAgo);
        if (revenueLast30Days == null) revenueLast30Days = BigDecimal.ZERO;

        BigDecimal discountsGiven = memberships.sumDiscountsByOrganizationId(org.getId());
        if (discountsGiven == null) discountsGiven = BigDecimal.ZERO;

        long planCount = plans.countByOrganizationId(org.getId());
        long ptTrainerCount = trainers.countByOrganizationId(org.getId());
        long ptSessionsConducted = ptSessions.countByOrganizationId(org.getId());
        long staffCount = users.countByRoleAndOrganizationId(Role.STAFF, org.getId());
        long actionHistoryCount = actionHistories.countByOrganizationId(org.getId());
        
        boolean customBrandingActive = org.getLogoData() != null && !org.getLogoData().isBlank();

        int healthScore = 0;

        Long daysUntilExpiry = null;
        if (org.getSubscriptionExpiresAt() != null) {
            daysUntilExpiry = ChronoUnit.DAYS.between(Instant.now(), org.getSubscriptionExpiresAt());
        }

        if (org.getSubscriptionStatus() == SubscriptionStatus.ACTIVE) {
            if (daysUntilExpiry == null) {
                healthScore += 25;
            } else if (daysUntilExpiry > 30) {
                healthScore += 25;
            } else if (daysUntilExpiry > 7) {
                healthScore += 15;
            } else if (daysUntilExpiry >= 0) {
                healthScore += 5;
            }
        } else if (org.getSubscriptionStatus() == SubscriptionStatus.TRIAL) {
            healthScore += 15;
        }

        if (memberCount > 0) {
            double activeRatio = (double) activeMemberCount / memberCount;
            if (activeRatio >= 0.70) healthScore += 30;
            else if (activeRatio >= 0.40) healthScore += 20;
            else healthScore += 10;
        }

        if (revenueLast30Days.compareTo(BigDecimal.ZERO) > 0) {
            healthScore += 25;
        } else if (totalRevenue.compareTo(BigDecimal.ZERO) > 0) {
            healthScore += 15;
        }

        if (ptTrainerCount > 0) healthScore += 5;
        if (branchCount > 1) healthScore += 5;
        if (planCount >= 2) healthScore += 5;
        if (customBrandingActive || actionHistoryCount > 5) healthScore += 5;

        healthScore = Math.min(100, Math.max(0, healthScore));

        String healthStatus = healthScore >= 75 ? "HEALTHY" : healthScore >= 45 ? "MODERATE" : "AT_RISK";

        List<String> decisionInsights = new ArrayList<>();
        SubscriptionTier tier = org.getSubscriptionTier() != null ? org.getSubscriptionTier() : SubscriptionTier.STANDARD;

        if (tier == SubscriptionTier.STANDARD && (memberCount >= 20 || branchCount > 1 || totalRevenue.compareTo(new BigDecimal("20000")) > 0)) {
            decisionInsights.add("🚀 Upsell Target: High activity on STANDARD tier. Recommend PRO or ENTERPRISE.");
        }
        if (tier == SubscriptionTier.PRO && branchCount >= 2) {
            decisionInsights.add("🏢 Multi-Branch Expansion: Ready for ENTERPRISE multi-site agreement.");
        }
        if (daysUntilExpiry != null && daysUntilExpiry >= 0 && daysUntilExpiry <= 14) {
            decisionInsights.add("⏳ Renewal Approaching: Subscription expires in " + daysUntilExpiry + " days. Contact owner.");
        }
        if (org.getSubscriptionStatus() == SubscriptionStatus.SUSPENDED || org.getSubscriptionStatus() == SubscriptionStatus.EXPIRED) {
            decisionInsights.add("⚠️ Tenant Inactive: Subscription is " + org.getSubscriptionStatus() + ". Follow up on reactivation.");
        }
        if (memberCount > 10 && revenueLast30Days.compareTo(BigDecimal.ZERO) == 0) {
            decisionInsights.add("💤 Low Momentum: 0 memberships sold in last 30 days. Recommend outreach.");
        }
        if (memberCount >= 15 && ptTrainerCount == 0) {
            decisionInsights.add("🏋️ PT Adoption Opportunity: 0 Personal Trainers registered. Pitch PT module.");
        }
        if (healthScore >= 85 && activeMemberCount >= 15) {
            decisionInsights.add("⭐ Platform Champion: Outstanding retention and steady cashflow.");
        }
        if (decisionInsights.isEmpty()) {
            decisionInsights.add("✓ Core Operations: Tenant operating within expected operational baselines.");
        }

        return new PlatformOrganizationDto(
                org.getId(),
                org.getName(),
                org.getCode(),
                org.getLogoData(),
                tier,
                org.getSubscriptionStatus(),
                org.getSubscriptionExpiresAt(),
                org.getCreatedAt(),
                owner != null ? owner.getEmail() : null,
                owner != null ? owner.getFullName() : null,
                branchCount,
                memberCount,
                org.getCountryCode() != null ? org.getCountryCode() : "IN",
                org.getLegalJurisdiction() != null ? org.getLegalJurisdiction() : "INDIA_DPDP",
                activeMemberCount,
                totalRevenue,
                revenueLast30Days,
                discountsGiven,
                planCount,
                ptTrainerCount,
                ptSessionsConducted,
                staffCount,
                actionHistoryCount,
                customBrandingActive,
                healthScore,
                healthStatus,
                decisionInsights
        );
    }
}
