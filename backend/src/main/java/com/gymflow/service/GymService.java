package com.gymflow.service;

import com.gymflow.domain.Membership;
import com.gymflow.domain.MembershipPlan;
import com.gymflow.domain.MembershipStatus;
import com.gymflow.domain.BranchType;
import com.gymflow.domain.GymBranch;
import com.gymflow.domain.GymOrganization;
import com.gymflow.domain.Role;
import com.gymflow.domain.SubscriptionTier;
import com.gymflow.domain.SubscriptionStatus;
import com.gymflow.domain.AppFeature;
import com.gymflow.domain.UserAccount;
import com.gymflow.repository.MembershipRepository;
import com.gymflow.repository.MembershipCancellationRepository;
import com.gymflow.repository.BranchMembershipMetricsProjection;
import com.gymflow.repository.MembershipReceiptRepository;
import com.gymflow.repository.MembershipFreezeRepository;
import com.gymflow.repository.PlanRepository;
import com.gymflow.repository.UserRepository;
import com.gymflow.repository.BranchRepository;
import com.gymflow.repository.OrganizationRepository;
import java.util.Base64;
import java.util.Comparator;
import static com.gymflow.web.ApiDtos.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.gymflow.domain.PersonalTrainer;
import com.gymflow.repository.PersonalTrainerRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class GymService {
    private final UserRepository users;
    private final PlanRepository plans;
    private final MembershipRepository memberships;
    private final MembershipCancellationRepository cancellations;
    private final MembershipReceiptRepository receipts;
    private final OrganizationRepository organizations;
    private final BranchRepository branches;
    private final PersonalTrainerRepository pts;
    private final PasswordEncoder passwordEncoder;
    private final MembershipEmailService membershipEmailService;
    private final MembershipFreezeRepository freezes;
    private final ActionHistoryService actionHistory;
    private final SubscriptionService subscriptionService;
    private final GymRegistrationOtpService gymRegistrationOtpService;
    private final ZoneId businessZone;

    public GymService(UserRepository users, PlanRepository plans, MembershipRepository memberships,
            MembershipCancellationRepository cancellations,
            OrganizationRepository organizations, BranchRepository branches,
            PersonalTrainerRepository pts,
            MembershipReceiptRepository receipts, PasswordEncoder passwordEncoder,
            MembershipEmailService membershipEmailService, MembershipFreezeRepository freezes,
            ActionHistoryService actionHistory,
            SubscriptionService subscriptionService,
            GymRegistrationOtpService gymRegistrationOtpService,
            @Value("${gym.business-zone:Asia/Kolkata}") String businessZone) {
        this.users = users;
        this.plans = plans;
        this.memberships = memberships;
        this.cancellations = cancellations;
        this.receipts = receipts;
        this.organizations = organizations;
        this.branches = branches;
        this.pts = pts;
        this.passwordEncoder = passwordEncoder;
        this.membershipEmailService = membershipEmailService;
        this.freezes = freezes;
        this.actionHistory = actionHistory;
        this.subscriptionService = subscriptionService;
        this.gymRegistrationOtpService = gymRegistrationOtpService;
        this.businessZone = ZoneId.of(businessZone);
    }

    @Transactional(readOnly = true)
    public List<PlanDto> plans(String email, Role role) {
        UserAccount actor = account(email);
        Long branchId = (role == Role.OWNER || role == Role.PERSONAL_TRAINER || actor.getBranch() == null)
                ? null : actor.getBranch().getId();
        List<MembershipPlan> available = (role == Role.OWNER || branchId == null)
                ? plans.findByOrganizationIdOrderByPriceAsc(requireOrganization(actor).getId())
                : plans.findAvailableForOrganizationAndBranch(requireOrganization(actor).getId(), branchId);
        List<Long> planIds = available.stream().map(MembershipPlan::getId).toList();
        Map<Long, Long> usageByPlan = new LinkedHashMap<>();
        if (!planIds.isEmpty()) {
            memberships.countUsageByPlanIds(planIds)
                    .forEach(usage -> usageByPlan.put(usage.getItemId(), usage.getUsageCount()));
        }
        return available.stream().map(plan -> toPlanDto(plan, usageByPlan.getOrDefault(plan.getId(), 0L))).toList();
    }

    public PlanDto createPlan(String email, PlanRequest request) {
        validatePlanDuration(request);
        validatePtPlanConfig(request);
        if ((request.ptPackageType() != null && request.ptPackageType() != com.gymflow.domain.PtPackageType.NONE)
                || (request.includedPtSessions() != null && request.includedPtSessions() > 0)) {
            subscriptionService.requireFeature(account(email), AppFeature.PERSONAL_TRAINERS);
        }
        GymOrganization organization = requireOrganization(account(email));
        GymBranch branch = request.branchId() == null ? null : activeBranch(request.branchId(), organization.getId());
        MembershipPlan plan = plans.save(request.durationDays() != null
                ? MembershipPlan.forDays(request.name(), request.description(), request.price(), request.durationDays())
                : new MembershipPlan(request.name(), request.description(), request.price(), request.durationMonths()));
        plan.assignLocation(organization, branch);
        plan.updatePt(request.ptPackageType(), request.includedPtSessions(), request.ptFeeIncluded());
        actionHistory.record(account(email), "PACKAGE_CREATED", "PACKAGE", plan.getId(), null, null, toPlanDto(plan));
        return toPlanDto(plan);
    }

    public PlanDto updatePlan(String email, long id, PlanRequest request) {
        validatePlanDuration(request);
        validatePtPlanConfig(request);
        if ((request.ptPackageType() != null && request.ptPackageType() != com.gymflow.domain.PtPackageType.NONE)
                || (request.includedPtSessions() != null && request.includedPtSessions() > 0)) {
            subscriptionService.requireFeature(account(email), AppFeature.PERSONAL_TRAINERS);
        }
        GymOrganization organization = requireOrganization(account(email));
        GymBranch planOrganization = requireOrganization(account(email)).getId() != null ? null : null;
        MembershipPlan plan = plans.findByIdAndOrganizationId(id, organization.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Package not found"));
        PlanDto before = toPlanDto(plan);
        plan.updateDuration(request.name(), request.description(), request.price(), request.durationMonths(),
                request.durationDays(), request.active());
        plan.updatePt(request.ptPackageType(), request.includedPtSessions(), request.ptFeeIncluded());
        if (request.branchId() != null) {
            plan.assignLocation(organization, activeBranch(request.branchId(), organization.getId()));
        } else {
            plan.assignLocation(organization, null);
        }
        actionHistory.record(account(email), before.active() == request.active() ? "PACKAGE_UPDATED"
                : request.active() ? "PACKAGE_RESTORED" : "PACKAGE_ARCHIVED",
                "PACKAGE", plan.getId(), null, before, toPlanDto(plan));
        return toPlanDto(plan);
    }

    @Transactional(readOnly = true)
    public List<BranchDto> branches(String email, Role role) {
        UserAccount actor = account(email);
        GymOrganization organization = requireOrganization(actor);
        List<GymBranch> result = (role == Role.OWNER || role == Role.PERSONAL_TRAINER || actor.getBranch() == null)
                ? branches.findByOrganizationIdOrderByNameAsc(organization.getId())
                : List.of(requireUserBranch(actor));
        List<Long> branchIds = result.stream().map(GymBranch::getId).toList();
        Map<Long, Long> usageByBranch = new LinkedHashMap<>();
        if (!branchIds.isEmpty()) {
            memberships.countUsageByBranchIds(branchIds)
                    .forEach(usage -> usageByBranch.put(usage.getItemId(), usage.getUsageCount()));
        }
        return result.stream().map(branch -> toBranchDto(branch,
                usageByBranch.getOrDefault(branch.getId(), 0L))).toList();
    }

    public BranchDto createBranch(String email, BranchRequest request) {
        GymOrganization organization = requireOrganization(account(email));
        long currentBranches = branches.countByOrganizationId(organization.getId());
        SubscriptionTier tier = organization.getSubscriptionTier() != null ? organization.getSubscriptionTier() : SubscriptionTier.STANDARD;
        if (currentBranches >= tier.maxBranches()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Multi-branch operations require an Enterprise subscription. Standard, Starter, and Pro plans include 1 branch facility. Please upgrade to Enterprise to register additional locations.");
        }
        if (branches.existsByOrganizationIdAndCodeIgnoreCase(organization.getId(), request.code())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A branch with this code already exists");
        }
        GymBranch branch = branches.save(new GymBranch(organization, request.name(), request.code(), request.type(),
                request.address(), request.phoneNumber()));
        if (!request.active())
            branch.update(request.name(), request.code(), request.type(), request.address(),
                    request.phoneNumber(), false);
        return toBranchDto(branch);
    }

    public BranchDto updateBranch(String email, long id, BranchRequest request) {
        UserAccount actor = account(email);
        GymOrganization organization = requireOrganization(account(email));
        GymBranch branch = branches.findByIdAndOrganizationId(id, organization.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Branch not found"));
        if (branches.existsByOrganizationIdAndCodeIgnoreCaseAndIdNot(organization.getId(), request.code(), id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A branch with this code already exists");
        }
        BranchDto before = toBranchDto(branch);
        branch.update(request.name(), request.code(), request.type(), request.address(), request.phoneNumber(),
                request.active());
        actionHistory.record(actor, "BRANCH_UPDATED", "BRANCH", branch.getId(), null, before, toBranchDto(branch));
        return toBranchDto(branch);
    }

    @Transactional(readOnly = true)
    public List<UserDto> staff(String email) {
        GymOrganization organization = requireOrganization(account(email));
        return users.findByOrganizationIdAndRoleOrderByFullNameAsc(organization.getId(), Role.STAFF).stream()
                .map(this::toUserDto).toList();
    }

    public UserDto createStaff(String ownerEmail, StaffRegistrationRequest request) {
        UserAccount owner = account(ownerEmail);
        GymOrganization organization = requireOrganization(owner);
        SubscriptionTier tier = organization.getSubscriptionTier() != null ? organization.getSubscriptionTier() : SubscriptionTier.STANDARD;
        long currentStaff = users.countByRoleAndOrganizationId(Role.STAFF, organization.getId());
        if (currentStaff >= tier.maxStaff()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Staff account limit reached (" + tier.maxStaff() + " accounts) for " + tier.name() + " tier. Upgrade your subscription to add additional team accounts.");
        }
        GymBranch branch = activeBranch(request.branchId(), organization.getId());
        if (users.existsByEmailIgnoreCase(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists");
        }
        UserAccount account = users.save(new UserAccount(request.fullName(), request.email(), request.phoneNumber(),
                passwordEncoder.encode(request.password()), Role.STAFF, organization, branch));
        actionHistory.record(owner, "STAFF_CREATED", "STAFF", account.getId(), null, null, toUserDto(account));
        membershipEmailService.notifyStaffAdded(owner.getEmail(), account.getFullName(), account.getEmail(),
                branch.getName());
        return toUserDto(account);
    }

    @Transactional
    public UserDto updateStaff(String ownerEmail, long id, StaffUpdateRequest request) {
        UserAccount owner = account(ownerEmail);
        Long organizationId = requireOrganization(owner).getId();
        UserAccount staff = users.findById(id)
                .filter(user -> user.getRole() == Role.STAFF && user.getOrganization() != null
                        && user.getOrganization().getId().equals(organizationId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Staff account not found"));
        GymBranch branch = activeBranch(request.branchId(), organizationId);
        UserDto before = toUserDto(staff);
        staff.updateProfile(request.fullName(), staff.getEmail(), request.phoneNumber());
        staff.assignLocation(staff.getOrganization(), branch);
        if (request.password() != null && !request.password().isBlank()) {
            if (request.password().trim().length() < 8) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at least 8 characters");
            }
            staff.changePassword(passwordEncoder.encode(request.password().trim()));
        }
        actionHistory.record(owner, "STAFF_UPDATED", "STAFF", staff.getId(), null, before, toUserDto(staff));
        return toUserDto(staff);
    }

    public synchronized UserDto registerGym(GymRegistrationRequest request) {
        gymRegistrationOtpService.verifyAndConsumeOtp(request.email(), request.verificationCode());
        if (users.existsByEmailIgnoreCase(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists");
        }
        if (organizations.findByCodeIgnoreCase(request.organizationCode()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A gym with code " + request.organizationCode() + " already exists");
        }
        SubscriptionTier tier = SubscriptionTier.STANDARD;
        GymOrganization organization = new GymOrganization(
                request.organizationName(), request.organizationCode(), tier, request.countryCode());
        organization.recordLegalAcceptance("2026.1");
        organization = organizations.save(organization);

        GymBranch mainBranch = branches.save(new GymBranch(organization,
                request.branchName() == null || request.branchName().isBlank() ? "Main branch" : request.branchName(),
                "MAIN", BranchType.COMPANY_OWNED, request.branchAddress(), request.phoneNumber()));

        UserAccount owner = users.save(new UserAccount(request.fullName(), request.email(), request.phoneNumber(),
                passwordEncoder.encode(request.password()), Role.OWNER, organization, null));

        MembershipPlan plan1 = new MembershipPlan("Essential", "Open gym access for one month.", new BigDecimal("49.00"), 1);
        plan1.assignLocation(organization, null);
        plans.save(plan1);

        MembershipPlan plan2 = new MembershipPlan("Strength", "Full access with equipment.", new BigDecimal("129.00"), 3);
        plan2.assignLocation(organization, null);
        plans.save(plan2);

        MembershipPlan plan3 = new MembershipPlan("All access", "Year-round gym access.", new BigDecimal("399.00"), 12);
        plan3.assignLocation(organization, null);
        plans.save(plan3);

        return toUserDto(owner);
    }

    public long organizationId(String email) {
        return requireOrganization(account(email)).getId();
    }

    public MembershipDto membershipDto(Membership membership) {
        return toMembershipDto(membership);
    }

    @Transactional(readOnly = true)
    public UserAccount account(String email) {
        return users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
    }

    @Transactional(readOnly = true)
    public UserDto userDto(String email) {
        return toUserDto(account(email));
    }

    public UserDto updateProfile(String currentEmail, ProfileRequest request) {
        UserAccount user = account(currentEmail);
        users.findByEmailIgnoreCase(request.email()).filter(existing -> !existing.getId().equals(user.getId()))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already in use");
                });
        user.updateProfile(request.fullName(), request.email(), request.phoneNumber());
        return toUserDto(user);
    }

    public UserDto changePassword(String email, PasswordChangeRequest request) {
        UserAccount user = account(email);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }
        user.changePassword(passwordEncoder.encode(request.newPassword()));
        return toUserDto(user);
    }

    public void resetPassword(String email, String newPassword) {
        UserAccount user = account(email);
        user.changePassword(passwordEncoder.encode(newPassword));
    }

    public void invalidateUserTokens(String email) {
        users.findByEmailIgnoreCase(email).ifPresent(UserAccount::invalidateTokens);
    }

    public void updateMemberPin(String email, String pin) {
        UserAccount member = account(email);
        if (member.getRole() != Role.MEMBER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only members have attendance PINs");
        }
        member.updateMemberPin(pin);
    }

    public void deleteStaff(String ownerEmail, long id) {
        UserAccount owner = account(ownerEmail);
        Long organizationId = requireOrganization(owner).getId();
        UserAccount staff = users.findById(id)
                .filter(user -> user.getRole() == Role.STAFF && user.getOrganization() != null
                        && user.getOrganization().getId().equals(organizationId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Staff account not found"));
        UserDto before = toUserDto(staff);
        actionHistory.record(owner, "STAFF_DELETED", "STAFF", staff.getId(), null, before, null);
        users.delete(staff);
    }

    public MembershipDto register(String actorEmail, Role role, MembershipRegistrationRequest request) {
        UserAccount actor = account(actorEmail);
        GymOrganization organization = requireOrganization(actor);
        SubscriptionTier tier = organization.getSubscriptionTier() != null ? organization.getSubscriptionTier() : SubscriptionTier.STANDARD;
        long currentMembers = users.countByRoleAndOrganizationId(Role.MEMBER, organization.getId());
        boolean isNewUser = !users.existsByEmailIgnoreCase(request.email());
        if (isNewUser && currentMembers >= tier.maxMembers()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Member roster limit reached (" + tier.maxMembers() + " members) for " + tier.name() + " tier. Upgrade your subscription to register additional members.");
        }
        GymBranch branch = resolveBranch(actor, role, request.branchId());
        MembershipPlan plan = plans.findByIdAndOrganizationId(request.planId(), organization.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Active package not found"));
        if (!plan.isActive() || (plan.getBranch() != null && !plan.getBranch().getId().equals(branch.getId()))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Active package not found for this branch");
        }
        LocalDate startDate = request.startDate() == null ? businessToday() : request.startDate();
        BigDecimal discount = (role == Role.MEMBER || request.discountPercent() == null) ? BigDecimal.ZERO : request.discountPercent().setScale(2, RoundingMode.HALF_UP);
        BigDecimal amount = plan.getPrice().multiply(BigDecimal.ONE.subtract(discount.movePointLeft(2)))
                .setScale(2, RoundingMode.HALF_UP);
        UserAccount member = users.findByEmailIgnoreCase(request.email()).map(existing -> {
            if (existing.getRole() != Role.MEMBER) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "This email belongs to a non-member account");
            }
            if (existing.getOrganization() == null
                    || !existing.getOrganization().getId().equals(organization.getId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "This email is already registered with another gym organization");
            }
            if (memberships.existsByMemberIdAndStatusAndEndDateGreaterThanEqualAndRenewalStartDateIsNull(
                    existing.getId(), MembershipStatus.ACTIVE, businessToday())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "This member already has an active or scheduled membership");
            }
            if (memberships.existsByMemberId(existing.getId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "This member already has membership history. Renew their existing membership instead");
            }
            existing.updateProfile(request.fullName(), request.email(), request.phoneNumber());
            // Do NOT override the existing member's password during re-registration
            existing.assignLocation(organization, branch);
            return existing;
        }).orElseGet(() -> users.save(new UserAccount(request.fullName(), request.email(), request.phoneNumber(),
                passwordEncoder.encode(request.password()), Role.MEMBER, organization, branch)));
        PersonalTrainer pt = null;
        if (request.personalTrainerId() != null) {
            subscriptionService.requireFeature(actor, AppFeature.PERSONAL_TRAINERS);
            pt = pts.findByIdAndOrganizationId(request.personalTrainerId(), organization.getId())
                    .filter(PersonalTrainer::isActive)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Active personal trainer not found"));
        }

        String paymentMethod = (request.paymentMethod() == null || request.paymentMethod().isBlank())
                ? "CASH"
                : request.paymentMethod().trim().toUpperCase();
        String transactionId = (request.transactionId() == null || request.transactionId().isBlank())
                ? null
                : request.transactionId().trim();

        if (!"CASH".equalsIgnoreCase(paymentMethod) && !"OFFLINE".equalsIgnoreCase(paymentMethod)) {
            if (transactionId == null || transactionId.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Transaction ID is required for online payments (" + paymentMethod + ")");
            }
        }

        Membership membership = memberships.save(new Membership(member, plan, branch, startDate,
                endDate(startDate, plan), plan.getPrice(), amount, discount, null, pt, paymentMethod, transactionId));

        MembershipEmailService.ReceiptAttachment receipt = null;
        if (request.receiptImageData() != null && !request.receiptImageData().isBlank()) {
            subscriptionService.requireFeature(actor, AppFeature.RECEIPT_IMAGE_UPLOAD);
            receipt = receiptAttachment(request.receiptImageData());
            if (receipt != null) {
                receipts.save(new com.gymflow.domain.MembershipReceipt(membership, receipt.content(),
                        receipt.contentType(), receipt.filename()));
            }
        }
        String ownerEmail = users.findByOrganizationIdAndRoleOrderByFullNameAsc(organization.getId(), Role.OWNER)
                .stream().findFirst().map(UserAccount::getEmail).orElse(null);
        if (ownerEmail != null) {
            membershipEmailService.notifyMemberEnrolled(ownerEmail, member.getEmail(), member.getFullName(),
                    branch.getName(), plan.getName(), amount, startDate, membership.getEndDate(), receipt);
        }
        actionHistory.record(actor, "MEMBERSHIP_CREATED", "MEMBERSHIP", membership.getId(), null, null,
                toMembershipDto(membership));
        return toMembershipDto(membership);
    }

    private MembershipEmailService.ReceiptAttachment receiptAttachment(String dataUrl) {
        if (dataUrl == null || dataUrl.isBlank()) {
            return null;
        }
        int separator = dataUrl.indexOf(',');
        if (separator < 0 || !dataUrl.substring(0, separator).startsWith("data:")
                || !dataUrl.substring(0, separator).endsWith(";base64")) {
            throw invalidReceipt();
        }
        String contentType = dataUrl.substring(5, separator - ";base64".length());
        String extension = switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> throw invalidReceipt();
        };
        byte[] content;
        try {
            content = Base64.getDecoder().decode(dataUrl.substring(separator + 1));
        } catch (IllegalArgumentException exception) {
            throw invalidReceipt();
        }
        if (content.length == 0 || content.length > 5 * 1024 * 1024 || !matchesImageSignature(content, contentType)) {
            throw invalidReceipt();
        }
        return new MembershipEmailService.ReceiptAttachment(content, contentType,
                "membership-receipt." + extension);
    }

    private boolean matchesImageSignature(byte[] content, String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> content.length >= 3 && (content[0] & 0xff) == 0xff
                    && (content[1] & 0xff) == 0xd8 && (content[2] & 0xff) == 0xff;
            case "image/png" -> content.length >= 8 && (content[0] & 0xff) == 0x89 && content[1] == 'P'
                    && content[2] == 'N' && content[3] == 'G' && (content[4] & 0xff) == 0x0d
                    && (content[5] & 0xff) == 0x0a && (content[6] & 0xff) == 0x1a
                    && (content[7] & 0xff) == 0x0a;
            case "image/webp" -> content.length >= 12 && content[0] == 'R' && content[1] == 'I'
                    && content[2] == 'F' && content[3] == 'F' && content[8] == 'W'
                    && content[9] == 'E' && content[10] == 'B' && content[11] == 'P';
            default -> false;
        };
    }

    private ResponseStatusException invalidReceipt() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Receipt must be a JPEG, PNG, or WebP image up to 5 MB");
    }

    @Transactional(readOnly = true)
    public List<MembershipDto> memberships(String email, Role role) {
        UserAccount actor = account(email);
        Long organizationId = requireOrganization(actor).getId();
        List<Membership> results;
        if (role == Role.MEMBER) {
            results = memberships.findByMemberEmailIgnoreCaseOrderByCreatedAtDesc(email);
        } else if (role == Role.OWNER) {
            results = memberships.findByMemberOrganizationIdOrderByCreatedAtDesc(organizationId);
        } else if (role == Role.PERSONAL_TRAINER) {
            PersonalTrainer pt = pts.findByUserAccountId(actor.getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PT profile not found"));
            results = memberships.findByPersonalTrainerIdOrderByCreatedAtDesc(pt.getId());
        } else {
            results = memberships.findByBranchIdAndMemberOrganizationIdOrderByCreatedAtDesc(
                    requireUserBranch(actor).getId(), organizationId);
        }
        return toMembershipDtos(results);
    }

    @Transactional(readOnly = true)
    public List<MembershipDto> recentMemberships(String email, Long branchId) {
        UserAccount actor = account(email);
        GymOrganization organization = requireOrganization(actor);
        if (actor.getRole() == Role.STAFF) {
            branchId = requireUserBranch(actor).getId();
        } else if (branchId != null && branches.findByIdAndOrganizationIdAndActiveTrue(branchId, organization.getId()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Active branch not found");
        }
        return toMembershipDtos(memberships.findRecentForOrganization(organization.getId(), branchId,
                PageRequest.of(0, 6)).getContent());
    }

    @Transactional(readOnly = true)
    public MembershipPageDto membershipPage(String email, Role role, int page, int size, String search, String filter) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "page must be non-negative and size must be between 1 and 100");
        }
        String normalizedFilter = filter == null ? "all" : filter.toLowerCase(java.util.Locale.ROOT);
        if (!List.of("all", "active", "expiring", "cancelled").contains(normalizedFilter)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "filter must be all, active, expiring, or cancelled");
        }
        UserAccount actor = account(email);
        Long organizationId = requireOrganization(actor).getId();
        Long memberId = role == Role.MEMBER ? actor.getId() : null;
        Long branchId = role == Role.STAFF ? requireUserBranch(actor).getId() : null;
        String normalizedSearch = search == null ? "" : search.trim();
        LocalDate today = businessToday();
        Page<UserAccount> memberPage = users.findMembershipMembers(organizationId, memberId, branchId,
                normalizedSearch, normalizedFilter, today, today.plusDays(7),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id"))));
        List<Long> memberIds = memberPage.getContent().stream().map(UserAccount::getId).toList();
        List<Membership> terms = memberIds.isEmpty() ? List.of()
                : memberships.findByMemberIdsForGroups(memberIds, branchId);
        List<Long> membershipIds = terms.stream().map(Membership::getId).toList();
        Map<Long, Instant> activityByMembership = actionHistory.latestMembershipActivity(
                organizationId, membershipIds);
        Map<Long, List<com.gymflow.domain.MembershipFreeze>> freezesByMembership = new LinkedHashMap<>();
        if (!membershipIds.isEmpty()) {
            freezes.findByMembershipIdInOrderByMembershipIdAscStartDateAsc(membershipIds)
                    .forEach(freeze -> freezesByMembership.computeIfAbsent(freeze.getMembership().getId(),
                            ignored -> new java.util.ArrayList<>()).add(freeze));
        }
        Map<Long, List<MembershipDto>> termsByMember = new LinkedHashMap<>();
        terms.forEach(term -> termsByMember.computeIfAbsent(term.getMember().getId(),
                ignored -> new java.util.ArrayList<>()).add(toMembershipDto(term,
                        freezesByMembership.getOrDefault(term.getId(), List.of()),
                        activityByMembership.get(term.getId()))));
        termsByMember.values().forEach(memberTerms -> memberTerms.sort(
                Comparator.comparing(MembershipDto::lastActivityAt).reversed()
                        .thenComparing(MembershipDto::id, Comparator.reverseOrder())));
        List<MembershipGroupDto> groups = memberPage.getContent().stream()
                .map(member -> new MembershipGroupDto(member.getId(), member.getFullName(), member.getEmail(),
                        member.getPhoneNumber(), termsByMember.getOrDefault(member.getId(), List.of())))
                .toList();
        return new MembershipPageDto(groups, memberPage.getNumber(), memberPage.getSize(),
                memberPage.getTotalElements(), memberPage.getTotalPages());
    }

    @Transactional(readOnly = true)
    public List<MembershipDto> expiring(String email, Role role, int withinDays) {
        if (withinDays < 1 || withinDays > 90) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "withinDays must be between 1 and 90");
        }
        UserAccount actor = account(email);
        Long organizationId = requireOrganization(actor).getId();
        Long branchId = role == Role.OWNER ? null : requireUserBranch(actor).getId();
        LocalDate today = businessToday();
        return toMembershipDtos(memberships.findExpiringWithoutRenewal(
                today, today.plusDays(withinDays), today, MembershipStatus.ACTIVE, organizationId, branchId));
    }

    public void cancel(String email, Role role, long id, CancellationRequest request) {
        validateCancellationRequest(request);
        Membership observed = memberships.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Membership not found"));
        Membership ancestor = observed.getRenewedFrom();
        if (ancestor != null) {
            ancestor = memberships.findByIdForUpdate(ancestor.getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                            "The renewal history is inconsistent"));
        }
        Membership membership = memberships.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Membership not found"));
        UserAccount actor = account(email);
        verifyMembershipScope(actor, role, membership);
        if (membership.getStatus() == MembershipStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This membership term is already cancelled");
        }
        MembershipDto before = toMembershipDto(membership);
        if (ancestor != null && ancestor.getRenewalStartDate() != null) {
            MembershipDto ancestorBefore = toMembershipDto(ancestor);
            ancestor.clearScheduledRenewal();
            actionHistory.record(actor, "MEMBERSHIP_RENEWAL_CANCELLED", "MEMBERSHIP", ancestor.getId(), null,
                    ancestorBefore, toMembershipDto(ancestor));
        }
        if (membership.getRenewalStartDate() != null) {
            cancelScheduledDescendants(membership, actor, request);
        }
        membership.cancel(cancellationInstant(request.cancellationDate()));
        com.gymflow.domain.MembershipCancellation cancellation =
                recordCancellation(membership, actor, request, before.status().name());
        actionHistory.record(actor, "MEMBERSHIP_CANCELLED", "MEMBERSHIP", membership.getId(),
                request.reason(),
                before, cancellationEvidence(cancellation, toMembershipDto(membership)));
    }

    public void cancelScheduledRenewal(String email, Role role, long id, CancellationRequest request) {
        validateCancellationRequest(request);
        Membership parent = memberships.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Membership not found"));
        UserAccount actor = account(email);
        verifyMembershipScope(actor, role, parent);
        if (parent.getRenewalStartDate() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This membership has no scheduled renewal");
        }
        Membership child = memberships.findByRenewedFromIdAndStartDateAndStatus(
                        id, parent.getRenewalStartDate(), MembershipStatus.ACTIVE)
                .flatMap(candidate -> memberships.findByIdForUpdate(candidate.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "The scheduled renewal could not be found"));
        if (child.getStatus() != MembershipStatus.ACTIVE || !child.getStartDate().isAfter(businessToday())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This renewal has already started; cancel the active membership term instead");
        }
        MembershipDto parentBefore = toMembershipDto(parent);
        MembershipDto childBefore = toMembershipDto(child);
        parent.clearScheduledRenewal();
        child.cancel(cancellationInstant(request.cancellationDate()));
        com.gymflow.domain.MembershipCancellation cancellation =
                recordCancellation(child, actor, request, childBefore.status().name());
        if (child.getRenewalStartDate() != null) {
            cancelScheduledDescendants(child, actor, request);
        }
        actionHistory.record(actor, "MEMBERSHIP_RENEWAL_CANCELLED", "MEMBERSHIP", parent.getId(),
                request.reason(),
                parentBefore, toMembershipDto(parent));
        actionHistory.record(actor, "MEMBERSHIP_CANCELLED", "MEMBERSHIP", child.getId(), request.reason(),
                childBefore, cancellationEvidence(cancellation, toMembershipDto(child)));
    }

    private void cancelScheduledDescendants(Membership parent, UserAccount actor, CancellationRequest request) {
        Membership child = memberships.findByRenewedFromIdAndStartDateAndStatus(
                        parent.getId(), parent.getRenewalStartDate(), MembershipStatus.ACTIVE)
                .flatMap(candidate -> memberships.findByIdForUpdate(candidate.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "The scheduled renewal could not be found"));
        MembershipDto childBefore = toMembershipDto(child);
        if (child.getRenewalStartDate() != null) {
            cancelScheduledDescendants(child, actor, request);
        }
        if (child.getStatus() == MembershipStatus.ACTIVE) {
            child.cancel(cancellationInstant(request.cancellationDate()));
            com.gymflow.domain.MembershipCancellation cancellation =
                    recordCancellation(child, actor, request, childBefore.status().name());
            actionHistory.record(actor, "MEMBERSHIP_CANCELLED", "MEMBERSHIP", child.getId(), request.reason(),
                    childBefore, cancellationEvidence(cancellation, toMembershipDto(child)));
        }
        child.clearScheduledRenewal();
        actionHistory.record(actor, "MEMBERSHIP_RENEWAL_CANCELLED", "MEMBERSHIP", child.getId(),
                request.reason(),
                childBefore, toMembershipDto(child));
        parent.clearScheduledRenewal();
    }

    private void validateCancellationRequest(CancellationRequest request) {
        if (request == null || request.reason() == null
                || !List.of("NO_LONGER_INTERESTED", "RELOCATED", "MEDICAL", "FINANCIAL", "SERVICE_ISSUE", "OTHER")
                        .contains(request.reason().trim())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a valid cancellation reason");
        }
        if (request.remarks() == null || request.remarks().trim().length() < 10
                || request.remarks().trim().length() > 2000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cancellation remarks must contain between 10 and 2000 characters");
        }
        if (request.cancellationDate() == null || request.cancellationDate().isAfter(businessToday())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cancellation date is required and cannot be in the future");
        }
        if ("OTHER".equals(request.reason().trim())
                && (request.otherExplanation() == null || request.otherExplanation().trim().length() < 10
                        || request.otherExplanation().trim().length() > 1000)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Explain the other cancellation reason in at least 10 characters");
        }
    }

    private Instant cancellationInstant(LocalDate date) {
        return date.atStartOfDay(businessZone).toInstant();
    }

    private com.gymflow.domain.MembershipCancellation recordCancellation(Membership membership, UserAccount actor, CancellationRequest request,
            String statusBefore) {
        return cancellations.save(new com.gymflow.domain.MembershipCancellation(membership, actor,
                request.reason().trim(), request.otherExplanation() == null
                        ? null : request.otherExplanation().trim(),
                request.remarks().trim(), request.cancellationDate(), statusBefore));
    }

    private CancellationEvidence cancellationEvidence(com.gymflow.domain.MembershipCancellation cancellation,
            MembershipDto membership) {
        return new CancellationEvidence(cancellation.getId(), cancellation.getMembershipId(),
                cancellation.getMemberId(), cancellation.getReason(), cancellation.getOtherExplanation(),
                cancellation.getRemarks(), cancellation.getCancellationDate(), cancellation.getRequestedById(),
                cancellation.getRequestedByName(), cancellation.getPerformedById(),
                cancellation.getPerformedByName(), cancellation.getApprovedById(),
                cancellation.getApprovalStatus(), cancellation.getRequestedAt(), cancellation.getPerformedAt(),
                cancellation.getStatusBefore(), cancellation.getStatusAfter(), cancellation.getStatus(),
                cancellation.getStatusChangedAt(), membership);
    }

    private record CancellationEvidence(Long cancellationId, Long membershipId, Long memberId, String reason,
            String otherExplanation, String remarks, LocalDate cancellationDate, Long requestedById,
            String requestedByName, Long performedById, String performedByName, Long approvedById,
            String approvalStatus, Instant requestedAt, Instant performedAt, String statusBefore,
            String statusAfter, String status, Instant statusChangedAt, MembershipDto membership) {
    }

    public MembershipDto renew(String email, Role role, long id, MembershipRenewalRequest request) {
        Membership previous = memberships.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Membership not found"));
        UserAccount actor = account(email);
        verifyMembershipScope(actor, role, previous);
        GymOrganization organization = requireOrganization(actor);
        if (previous.getRenewalStartDate() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This membership already has a renewal scheduled");
        }
        GymBranch targetBranch;
        if (role == Role.OWNER) {
            targetBranch = request.branchId() == null ? previous.getBranch()
                    : activeBranch(request.branchId(), organization.getId());
        } else if (role == Role.MEMBER) {
            targetBranch = previous.getBranch();
        } else {
            targetBranch = requireUserBranch(actor);
            if (!targetBranch.getId().equals(previous.getBranch().getId())
                    || (request.branchId() != null && !request.branchId().equals(targetBranch.getId()))) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Staff can only renew memberships at their assigned branch");
            }
        }
        MembershipPlan plan = plans.findByIdAndOrganizationId(request.planId(), organization.getId())
                .filter(MembershipPlan::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Active package not found"));
        if (plan.getBranch() != null && !plan.getBranch().getId().equals(targetBranch.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Active package not found for this branch");
        }
        LocalDate today = businessToday();
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
        LocalDate renewalEndDate = endDate(startDate, plan);
        if (memberships.hasOverlappingActiveMembership(previous.getMember().getId(), previous.getId(),
                MembershipStatus.ACTIVE, startDate, renewalEndDate)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This member already has an active membership during the selected renewal dates");
        }
        BigDecimal discount = request.discountPercent().setScale(2, RoundingMode.HALF_UP);
        BigDecimal amount = plan.getPrice().multiply(BigDecimal.ONE.subtract(discount.movePointLeft(2)))
                .setScale(2, RoundingMode.HALF_UP);
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

        if (!"CASH".equalsIgnoreCase(paymentMethod) && !"OFFLINE".equalsIgnoreCase(paymentMethod)) {
            if (transactionId == null || transactionId.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Transaction ID is required for online payments (" + paymentMethod + ")");
            }
        }

        Membership renewal = memberships.save(new Membership(previous.getMember(), plan, targetBranch, startDate,
                renewalEndDate, plan.getPrice(), amount, discount, previous, pt, paymentMethod, transactionId));
        MembershipDto before = toMembershipDto(previous);
        previous.scheduleRenewal(startDate);
        actionHistory.record(actor, "MEMBERSHIP_RENEWED", "MEMBERSHIP", renewal.getId(), null,
                before, toMembershipDto(renewal));
        return toMembershipDto(renewal);
    }

    @Transactional(readOnly = true)
    public MembershipUpgradeQuoteDto quoteMembershipUpgrade(String email, Role role, long membershipId, long newPlanId) {
        Membership membership = memberships.findById(membershipId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Membership not found"));
        UserAccount actor = account(email);
        verifyMembershipScope(actor, role, membership);
        GymOrganization organization = requireOrganization(actor);

        if (membership.getStatus() != MembershipStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only active memberships can be upgraded");
        }
        LocalDate today = businessToday();
        if (membership.getEndDate().isBefore(today)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Membership has expired - please renew instead");
        }
        if (today.isBefore(membership.getStartDate())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Membership has not started yet");
        }
        if (membership.getRenewalStartDate() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This membership has a scheduled renewal. Please cancel or update the renewal before upgrading the current term.");
        }

        MembershipPlan newPlan = plans.findByIdAndOrganizationId(newPlanId, organization.getId())
                .filter(MembershipPlan::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Active package not found"));

        if (newPlan.getId().equals(membership.getPlan().getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Member is already on this plan");
        }

        int totalDays = (int) (ChronoUnit.DAYS.between(membership.getStartDate(), membership.getEndDate()) + 1);
        if (totalDays < 1) totalDays = 1;
        int remainingDays = (int) (ChronoUnit.DAYS.between(today, membership.getEndDate()) + 1);
        if (remainingDays < 0) remainingDays = 0;

        int oldDuration = membership.getPlanDurationDaysSnapshot() != null ? membership.getPlanDurationDaysSnapshot()
                : (membership.getPlanDurationMonthsSnapshot() != null ? membership.getPlanDurationMonthsSnapshot() * 30 : totalDays);
        int newDuration = newPlan.getDurationDays() != null ? newPlan.getDurationDays()
                : (newPlan.getDurationMonths() != null ? newPlan.getDurationMonths() * 30 : totalDays);
        if (newDuration < oldDuration) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot upgrade to a package of shorter duration (" + newDuration + " days < " + oldDuration + " days)");
        }

        int newTotalDays = (newDuration == oldDuration) ? totalDays : Math.max(1, newDuration);

        BigDecimal oldDailyRate = membership.getListPrice().divide(BigDecimal.valueOf(totalDays), 4, RoundingMode.HALF_UP);
        BigDecimal newDailyRate = newPlan.getPrice().divide(BigDecimal.valueOf(newTotalDays), 4, RoundingMode.HALF_UP);
        BigDecimal creditAmount = oldDailyRate.multiply(BigDecimal.valueOf(remainingDays)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal newPlanProrataCost = newDailyRate.multiply(BigDecimal.valueOf(remainingDays)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal topUpDue = newPlanProrataCost.subtract(creditAmount).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

        boolean hasPt = newPlan.getPtPackageType() != null && newPlan.getPtPackageType() != com.gymflow.domain.PtPackageType.NONE;

        return new MembershipUpgradeQuoteDto(
                totalDays, remainingDays,
                oldDailyRate.setScale(2, RoundingMode.HALF_UP),
                newDailyRate.setScale(2, RoundingMode.HALF_UP),
                creditAmount, newPlanProrataCost, topUpDue,
                membership.getPlanNameSnapshot(), membership.getListPrice(),
                newPlan.getName(), newPlan.getPrice(),
                hasPt);
    }

    @Transactional
    public MembershipDto upgradeMembership(String email, Role role, long membershipId, MembershipUpgradeRequest request) {
        Membership old = memberships.findByIdForUpdate(membershipId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Membership not found"));
        UserAccount actor = account(email);
        verifyMembershipScope(actor, role, old);
        GymOrganization organization = requireOrganization(actor);

        if (old.getStatus() != MembershipStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only active memberships can be upgraded");
        }
        LocalDate today = businessToday();
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
        if (request.newPlanId().equals(old.getPlan().getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Member is already on this plan");
        }

        MembershipPlan newPlan = plans.findByIdAndOrganizationId(request.newPlanId(), organization.getId())
                .filter(MembershipPlan::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Active package not found"));

        int totalDays = (int) (ChronoUnit.DAYS.between(old.getStartDate(), old.getEndDate()) + 1);
        if (totalDays < 1) totalDays = 1;
        int oldDuration = old.getPlanDurationDaysSnapshot() != null ? old.getPlanDurationDaysSnapshot()
                : (old.getPlanDurationMonthsSnapshot() != null ? old.getPlanDurationMonthsSnapshot() * 30 : totalDays);
        int newDuration = newPlan.getDurationDays() != null ? newPlan.getDurationDays()
                : (newPlan.getDurationMonths() != null ? newPlan.getDurationMonths() * 30 : totalDays);
        if (newDuration < oldDuration) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot upgrade to a package of shorter duration");
        }

        // PT assignment
        PersonalTrainer pt = null;
        boolean newHasPt = newPlan.getPtPackageType() != null && newPlan.getPtPackageType() != com.gymflow.domain.PtPackageType.NONE;
        if (request.personalTrainerId() != null) {
            subscriptionService.requireFeature(actor, AppFeature.PERSONAL_TRAINERS);
            pt = pts.findByIdAndOrganizationId(request.personalTrainerId(), organization.getId())
                    .filter(PersonalTrainer::isActive)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Active personal trainer not found"));
        } else if (old.getPersonalTrainer() != null && old.getPersonalTrainer().isActive() && newHasPt) {
            pt = old.getPersonalTrainer();
        }

        String paymentMethod = (request.paymentMethod() == null || request.paymentMethod().isBlank()) ? "CASH"
                : request.paymentMethod().trim().toUpperCase();
        String transactionId = (request.transactionId() == null || request.transactionId().isBlank()) ? null
                : request.transactionId().trim();

        if (!"CASH".equalsIgnoreCase(paymentMethod) && !"OFFLINE".equalsIgnoreCase(paymentMethod)) {
            if (transactionId == null || transactionId.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Transaction ID is required for online payments (" + paymentMethod + ")");
            }
        }

        MembershipDto before = toMembershipDto(old);

        // Mark old as UPGRADED
        old.markUpgraded();

        // Create new membership from today to old.getEndDate()
        Membership upgraded = Membership.createUpgraded(
                old.getMember(),
                newPlan,
                old.getBranch(),
                today,
                old.getEndDate(),
                newPlan.getPrice(),
                request.topUpPaid().setScale(2, RoundingMode.HALF_UP),
                old,
                pt,
                paymentMethod,
                transactionId);

        Membership saved = memberships.save(upgraded);

        actionHistory.record(actor, "MEMBERSHIP_UPGRADED", "MEMBERSHIP", saved.getId(), null,
                before, toMembershipDto(saved));
        return toMembershipDto(saved);
    }

    @Transactional(readOnly = true)
    public DashboardDto dashboard(String email) {
        Long organizationId = requireOrganization(account(email)).getId();
        LocalDate today = businessToday();
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate nextMonth = monthStart.plusMonths(1);
        LocalDate previousMonth = monthStart.minusMonths(1);
        LocalDate previousPeriodEnd = previousMonth.plusDays(
                Math.min(today.getDayOfMonth(), previousMonth.lengthOfMonth()));
        Instant currentFrom = monthStart.atStartOfDay(businessZone).toInstant();
        Instant currentTo = nextMonth.atStartOfDay(businessZone).toInstant();
        Instant previousFrom = previousMonth.atStartOfDay(businessZone).toInstant();
        Instant previousTo = previousPeriodEnd.atStartOfDay(businessZone).toInstant();
        List<MembershipDto> expiring = expiring(email, Role.OWNER, 7);
        List<GymBranch> activeBranches = branches.findByOrganizationIdAndActiveTrueOrderByNameAsc(organizationId);
        Map<Long, BranchMembershipMetricsProjection> metricsByBranch = new LinkedHashMap<>();
        List<Long> branchIds = activeBranches.stream().map(GymBranch::getId).toList();
        if (!branchIds.isEmpty()) {
            memberships.findBranchMetrics(organizationId, branchIds, currentFrom, currentTo, previousFrom,
                    previousTo, MembershipStatus.ACTIVE, today)
                    .forEach(metrics -> metricsByBranch.put(metrics.getBranchId(), metrics));
        }
        Map<Long, List<MembershipDto>> expiringByBranch = new LinkedHashMap<>();
        expiring.stream().filter(item -> item.branchId() != null)
                .forEach(item -> expiringByBranch.computeIfAbsent(item.branchId(),
                        ignored -> new java.util.ArrayList<>()).add(item));
        List<BranchDashboardDto> branchDashboards = activeBranches.stream().map(branch -> {
            BranchMembershipMetricsProjection metrics = metricsByBranch.get(branch.getId());
            List<MembershipDto> branchExpiring = expiringByBranch.getOrDefault(branch.getId(), List.of());
            return new BranchDashboardDto(branch.getId(), branch.getName(), branch.getCode(), branch.getType(),
                    money(metrics == null ? null : metrics.getRevenueThisMonth()),
                    money(metrics == null ? null : metrics.getRevenueLastMonth()),
                    metrics == null ? 0 : metrics.getActiveMembers(),
                    metrics == null ? 0 : metrics.getTotalMembers(),
                    branchExpiring.size(), branchExpiring);
        }).toList();
        return new DashboardDto(
                money(memberships.revenueBetweenForOrganization(organizationId, null, currentFrom, currentTo)),
                money(memberships.revenueBetweenForOrganization(organizationId, null, previousFrom, previousTo)),
                memberships.countActiveMembersByOrganization(organizationId, null, MembershipStatus.ACTIVE, today),
                users.countByRoleAndOrganizationId(Role.MEMBER, organizationId), expiring.size(), expiring,
                branchDashboards);
    }

    private PlanDto toPlanDto(MembershipPlan plan) {
        return toPlanDto(plan, memberships.countByPlanId(plan.getId()));
    }

    private PlanDto toPlanDto(MembershipPlan plan, long membershipCount) {
        return new PlanDto(plan.getId(), plan.getName(), plan.getDescription(), plan.getPrice(),
                plan.getDurationMonths(), plan.getDurationDays(),
                plan.getBranch() == null ? null : plan.getBranch().getId(),
                plan.getBranch() == null ? null : plan.getBranch().getName(), plan.isActive(),
                membershipCount,
                plan.getPtPackageType(), plan.getIncludedPtSessions(), plan.getPtFeeIncluded());
    }

    public MembershipDto toMembershipDto(Membership membership) {
        return toMembershipDto(membership, freezes.findByMembershipIdOrderByStartDateAsc(membership.getId()));
    }

    public List<MembershipDto> toMembershipDtos(List<Membership> items) {
        if (items.isEmpty()) {
            return List.of();
        }
        List<Long> ids = items.stream().map(Membership::getId).toList();
        Map<Long, List<com.gymflow.domain.MembershipFreeze>> freezesByMembership = new LinkedHashMap<>();
        freezes.findByMembershipIdInOrderByMembershipIdAscStartDateAsc(ids)
                .forEach(freeze -> freezesByMembership.computeIfAbsent(freeze.getMembership().getId(),
                        ignored -> new java.util.ArrayList<>()).add(freeze));
        return items.stream().map(membership -> toMembershipDto(membership,
                freezesByMembership.getOrDefault(membership.getId(), List.of()))).toList();
    }

    private MembershipDto toMembershipDto(Membership membership,
            List<com.gymflow.domain.MembershipFreeze> freezesForMembership) {
        return toMembershipDto(membership, freezesForMembership,
                membership.getCancelledAt() == null ? membership.getCreatedAt() : membership.getCancelledAt());
    }

    private MembershipDto toMembershipDto(Membership membership,
            List<com.gymflow.domain.MembershipFreeze> freezesForMembership, Instant lastActivityAt) {
        List<FreezeDto> membershipFreezes = freezesForMembership
                .stream().map(freeze -> new FreezeDto(freeze.getId(), freeze.getStartDate(), freeze.getEndDate(),
                        freeze.getReason(), freeze.getCreatedBy(), freeze.getCreatedAt(),
                        freeze.getCancelledAt(), freeze.getEndedEarlyAt()))
                .toList();
        LocalDate today = businessToday();
        boolean frozen = membershipFreezes.stream()
                .anyMatch(freeze -> freeze.cancelledAt() == null
                        && (freeze.endedEarlyAt() == null || freeze.endedEarlyAt().isAfter(today))
                        && !today.isBefore(freeze.startDate()) && !today.isAfter(freeze.endDate()));
        PersonalTrainer pt = membership.getPersonalTrainer();
        Long ptId = pt == null ? null : pt.getId();
        String ptName = pt == null ? null : pt.getUserAccount().getFullName();
        return new MembershipDto(membership.getId(), membership.getMember().getFullName(),
                membership.getMember().getEmail(), membership.getMember().getPhoneNumber(),
                membership.getPlan().getId(), membership.getPlanNameSnapshot(),
                membership.getBranch() == null ? null : membership.getBranch().getId(),
                membership.getBranch() == null ? null : membership.getBranch().getName(),
                membership.getStartDate(), membership.getEndDate(), membership.getListPrice(),
                membership.getDiscountPercent(), membership.getAmountPaid(), membership.getStatus(),
                membership.getRenewalStartDate(),
                membership.getUpgradedFrom() == null ? null : membership.getUpgradedFrom().getId(),
                membership.getCreatedAt(),
                lastActivityAt == null ? membership.getCreatedAt() : lastActivityAt, membership.getCancelledAt(),
                membership.getPlanDurationMonthsSnapshot(), membership.getPlanDurationDaysSnapshot(), frozen,
                membershipFreezes,
                ptId, ptName, membership.getPtSessionsIncluded(), membership.getPtSessionsUsed(),
                null,
                membership.getPaymentMethod(),
                membership.getTransactionId());
    }

    private BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : value.setScale(2, RoundingMode.HALF_UP);
    }

    public UserDto toUserDto(UserAccount user) {
        // Default: only expose PIN when this user IS a member (their own /me response)
        return toUserDto(user, user.getRole() == Role.MEMBER);
    }

    /**
     * Converts a UserAccount to its DTO representation.
     *
     * @param includeMemberPin true only when the authenticated caller IS the member
     *                         themselves. Prevents STAFF/PT from reading another
     *                         member's attendance PIN.
     */
    public UserDto toUserDto(UserAccount user, boolean includeMemberPin) {
        GymBranch branch = user.getBranch();
        GymOrganization org = user.getOrganization();
        // Only expose the PIN to the member who owns it
        String memberPin = (includeMemberPin && user.getRole() == Role.MEMBER) ? user.getMemberPin() : null;
        return new UserDto(user.getId(), user.getFullName(), user.getEmail(), user.getPhoneNumber(), user.getRole(),
                branch == null ? null : branch.getId(), branch == null ? null : branch.getName(),
                org == null ? null : org.getId(), org == null ? null : org.getName(),
                org == null ? null : org.getLogoData(),
                org == null ? SubscriptionTier.STANDARD : org.getSubscriptionTier(),
                org == null ? List.of("CORE_MANAGEMENT") : org.getEnabledFeatures(),
                org == null ? SubscriptionStatus.ACTIVE : org.getSubscriptionStatus(),
                org == null ? null : org.getSubscriptionExpiresAt(),
                memberPin,
                org == null ? "IN" : org.getCountryCode(),
                org == null ? "INDIA_DPDP" : org.getLegalJurisdiction());
    }

    @Transactional
    public UserDto updateBranding(String ownerEmail, BrandingUpdateRequest request) {
        UserAccount owner = account(ownerEmail);
        GymOrganization org = requireOrganization(owner);
        subscriptionService.requireFeature(owner, AppFeature.CUSTOM_BRANDING);
        Map<String, Object> before = Map.of(
                "name", org.getName(),
                "hasCustomLogo", org.getLogoData() != null
        );
        if (request.name() != null && !request.name().isBlank()) {
            org.rename(request.name().trim());
        }
        if (request.logoData() != null) {
            org.updateLogo(request.logoData().isBlank() ? null : request.logoData().trim());
        }
        organizations.save(org);
        Map<String, Object> after = Map.of(
                "name", org.getName(),
                "hasCustomLogo", org.getLogoData() != null
        );
        actionHistory.record(owner, "ORGANIZATION_BRANDING_UPDATED", "ORGANIZATION", org.getId(), null, before, after);
        return toUserDto(owner);
    }

    @Transactional(readOnly = true)
    public TenantBackupDto exportBackup(String ownerEmail) {
        UserAccount owner = account(ownerEmail);
        GymOrganization org = requireOrganization(owner);
        subscriptionService.requireFeature(owner, AppFeature.ACTION_HISTORY);

        List<BranchDto> branchDtos = branches(ownerEmail, Role.OWNER);
        List<PlanDto> planDtos = plans(ownerEmail, null);
        List<MembershipDto> memberDtos = memberships(ownerEmail, Role.OWNER);
        List<UserDto> staffDtos = staff(ownerEmail);
        List<ActionHistoryDto> auditDtos = actionHistory.list(org.getId());

        actionHistory.record(owner, "TENANT_BACKUP_DOWNLOADED", "ORGANIZATION", org.getId(),
                "Downloaded full JSON data backup archive", null, null);

        return new TenantBackupDto(
                org.getId(),
                org.getName(),
                org.getCode(),
                org.getSubscriptionTier(),
                org.getSubscriptionStatus(),
                Instant.now(),
                owner.getEmail(),
                branchDtos,
                planDtos,
                memberDtos,
                staffDtos,
                auditDtos
        );
    }

    @Transactional
    public BulkMemberImportResultDto bulkImportMembers(String ownerEmail, BulkMemberImportRequest request) {
        UserAccount owner = account(ownerEmail);
        GymOrganization org = requireOrganization(owner);
        subscriptionService.requireFeature(owner, AppFeature.ACTION_HISTORY);

        if (request == null || request.members() == null || request.members().isEmpty()) {
            return new BulkMemberImportResultDto(0, 0, List.of("No member rows provided"));
        }

        SubscriptionTier tier = org.getSubscriptionTier() != null ? org.getSubscriptionTier() : SubscriptionTier.STANDARD;
        long currentMembers = users.countByRoleAndOrganizationId(Role.MEMBER, org.getId());
        if (currentMembers >= tier.maxMembers()) {
            return new BulkMemberImportResultDto(request.members().size(), 0,
                    List.of("Member roster limit reached (" + tier.maxMembers() + ") for " + tier.name() + " tier. Upgrade your subscription to import members."));
        }

        List<String> errors = new java.util.ArrayList<>();
        int imported = 0;
        List<GymBranch> allBranches = branches.findByOrganizationIdOrderByNameAsc(org.getId());
        List<MembershipPlan> allPlans = plans.findByOrganizationIdOrderByPriceAsc(org.getId());

        for (int i = 0; i < request.members().size(); i++) {
            BulkMemberImportRow row = request.members().get(i);
            int rowNum = i + 1;
            try {
                if (row.fullName() == null || row.fullName().isBlank()) {
                    errors.add("Row " + rowNum + ": Member full name is required");
                    continue;
                }
                if (row.email() == null || !row.email().contains("@")) {
                    errors.add("Row " + rowNum + " (" + row.fullName() + "): Invalid email address");
                    continue;
                }
                GymBranch branch = null;
                if (row.branchCode() != null && !row.branchCode().isBlank()) {
                    branch = allBranches.stream()
                            .filter(b -> b.getCode().equalsIgnoreCase(row.branchCode().trim()))
                            .findFirst().orElse(null);
                }
                if (branch == null) {
                    branch = allBranches.stream().findFirst().orElse(null);
                }
                if (branch == null) {
                    errors.add("Row " + rowNum + ": No active gym branch exists to assign member");
                    continue;
                }

                MembershipPlan plan = null;
                if (row.packageName() != null && !row.packageName().isBlank()) {
                    plan = allPlans.stream()
                            .filter(p -> p.getName().equalsIgnoreCase(row.packageName().trim()))
                            .findFirst().orElse(null);
                }
                if (plan == null) {
                    plan = allPlans.stream().findFirst().orElse(null);
                }
                if (plan == null) {
                    errors.add("Row " + rowNum + ": No active membership package found");
                    continue;
                }

                LocalDate startDate = row.startDate() != null ? row.startDate() : businessToday();
                MembershipRegistrationRequest regReq = new MembershipRegistrationRequest(
                        row.fullName().trim(),
                        row.email().trim().toLowerCase(),
                        row.phoneNumber() != null ? row.phoneNumber().trim() : "",
                        "Member123!",
                        plan.getId(),
                        branch.getId(),
                        null,
                        BigDecimal.ZERO,
                        startDate,
                        null,
                        row.paymentMethod() != null && !row.paymentMethod().isBlank() ? row.paymentMethod().trim() : "CASH",
                        null
                );
                register(ownerEmail, Role.OWNER, regReq);
                imported++;
            } catch (Exception ex) {
                errors.add("Row " + rowNum + " (" + (row.fullName() != null ? row.fullName() : "Unknown") + "): " + ex.getMessage());
            }
        }

        actionHistory.record(owner, "BULK_MEMBERS_IMPORTED", "ORGANIZATION", org.getId(),
                "Imported " + imported + " members from CSV batch", null, null);

        return new BulkMemberImportResultDto(request.members().size(), imported, errors);
    }

    private BranchDto toBranchDto(GymBranch branch) {
        return toBranchDto(branch, memberships.countByBranchId(branch.getId()));
    }

    private BranchDto toBranchDto(GymBranch branch, long membershipCount) {
        return new BranchDto(branch.getId(), branch.getName(), branch.getCode(), branch.getType(),
                branch.getAddress(), branch.getPhoneNumber(), branch.isActive(),
                membershipCount);
    }

    private GymOrganization requireOrganization(UserAccount user) {
        if (user.getOrganization() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Account is not assigned to a gym organization");
        }
        return user.getOrganization();
    }

    private GymBranch requireUserBranch(UserAccount user) {
        if (user.getBranch() == null || !user.getBranch().isActive()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account is not assigned to an active branch");
        }
        return user.getBranch();
    }

    private GymBranch activeBranch(long branchId, long organizationId) {
        return branches.findByIdAndOrganizationIdAndActiveTrue(branchId, organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Active branch not found"));
    }

    private GymBranch defaultBranch(long organizationId) {
        return branches.findByOrganizationIdAndActiveTrueOrderByNameAsc(organizationId).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "Create an active branch before enrolling members"));
    }

    private GymBranch resolveBranch(UserAccount actor, Role role, Long requestedBranchId) {
        Long organizationId = requireOrganization(actor).getId();
        if (role == Role.OWNER) {
            return requestedBranchId == null ? defaultBranch(organizationId)
                    : activeBranch(requestedBranchId, organizationId);
        }
        GymBranch assignedBranch = requireUserBranch(actor);
        if (requestedBranchId != null && !requestedBranchId.equals(assignedBranch.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Staff can only register members at their assigned branch");
        }
        return assignedBranch;
    }

    private void verifyMembershipScope(UserAccount actor, Role role, Membership membership) {
        GymOrganization organization = requireOrganization(actor);
        if (membership.getMember().getOrganization() == null
                || !membership.getMember().getOrganization().getId().equals(organization.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Membership not found");
        }
        if (role == Role.MEMBER && !membership.getMember().getEmail().equalsIgnoreCase(actor.getEmail())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Membership not found");
        }
        if (role == Role.STAFF && (membership.getBranch() == null || actor.getBranch() == null
                || !membership.getBranch().getId().equals(actor.getBranch().getId()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Staff can only manage memberships at their assigned branch");
        }
        if (role == Role.PERSONAL_TRAINER && (membership.getPersonalTrainer() == null
                || !membership.getPersonalTrainer().getUserAccount().getId().equals(actor.getId()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Personal trainers can only access memberships assigned to them");
        }
    }

    private LocalDate endDate(LocalDate startDate, MembershipPlan plan) {
        LocalDate exclusiveEnd = plan.getDurationDays() == null
                ? startDate.plusMonths(plan.getDurationMonths())
                : startDate.plusDays(plan.getDurationDays());
        return exclusiveEnd.minusDays(1);
    }

    private LocalDate businessToday() {
        return LocalDate.now(businessZone);
    }

    private void validatePlanDuration(PlanRequest request) {
        if ((request.durationMonths() == null) == (request.durationDays() == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Provide exactly one of durationMonths or durationDays");
        }
    }

    private void validatePtPlanConfig(PlanRequest request) {
        if (request.price() == null || request.price().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Package price must be greater than zero");
        }
        if (request.ptFeeIncluded() != null && request.price() != null
                && request.ptFeeIncluded().compareTo(request.price()) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "PT fee allocation (" + request.ptFeeIncluded() + ") cannot exceed total package price (" + request.price() + ")");
        }
        if (request.includedPtSessions() != null) {
            if (request.includedPtSessions() <= 0 || request.includedPtSessions() > 100) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Included PT sessions must be between 1 and 100");
            }
        }
    }
}