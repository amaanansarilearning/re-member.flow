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

@Service
@Transactional(readOnly = true)
public class SubscriptionService {
    private final UserRepository users;
    private final OrganizationRepository organizations;
    private final ActivationCodeRepository activationCodes;
    private final BranchRepository branches;
    private final UpgradeRequestRepository upgradeRequests;
    private final PlatformEmailService platformEmailService;
    private final SecureRandom secureRandom = new SecureRandom();

    public SubscriptionService(UserRepository users,
                               OrganizationRepository organizations,
                               ActivationCodeRepository activationCodes,
                               BranchRepository branches,
                               UpgradeRequestRepository upgradeRequests,
                               PlatformEmailService platformEmailService) {
        this.users = users;
        this.organizations = organizations;
        this.activationCodes = activationCodes;
        this.branches = branches;
        this.upgradeRequests = upgradeRequests;
        this.platformEmailService = platformEmailService;
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

        return new PlatformMetricsDto(
                totalGyms,
                activePremiumGyms,
                standardGyms,
                totalMembersAcrossGyms,
                totalBranches,
                availableCodes
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

    private PlatformOrganizationDto toPlatformOrgDto(GymOrganization org) {
        UserAccount owner = users.findByOrganizationIdAndRoleOrderByFullNameAsc(org.getId(), Role.OWNER)
                .stream().findFirst().orElse(null);
        long branchCount = branches.countByOrganizationId(org.getId());
        long memberCount = users.countByRoleAndOrganizationId(Role.MEMBER, org.getId());

        return new PlatformOrganizationDto(
                org.getId(),
                org.getName(),
                org.getCode(),
                org.getLogoData(),
                org.getSubscriptionTier(),
                org.getSubscriptionStatus(),
                org.getSubscriptionExpiresAt(),
                org.getCreatedAt(),
                owner != null ? owner.getEmail() : null,
                owner != null ? owner.getFullName() : null,
                branchCount,
                memberCount,
                org.getCountryCode() != null ? org.getCountryCode() : "IN",
                org.getLegalJurisdiction() != null ? org.getLegalJurisdiction() : "INDIA_DPDP"
        );
    }
}
