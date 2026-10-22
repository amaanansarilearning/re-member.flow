package com.gymflow.web;

import com.gymflow.domain.MembershipStatus;
import com.gymflow.domain.BranchType;
import com.gymflow.domain.Role;
import com.gymflow.domain.PtPackageType;
import com.gymflow.domain.SubscriptionTier;
import com.gymflow.domain.SubscriptionStatus;
import com.gymflow.domain.ActivationCodeStatus;
import com.gymflow.domain.UpgradeRequestStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class ApiDtos {
        private ApiDtos() {
        }

        public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
        }

        public record UserDto(Long id, String fullName, String email, String phoneNumber, Role role,
                        Long branchId, String branchName, Long organizationId, String organizationName,
                        String organizationLogo,
                        SubscriptionTier subscriptionTier, List<String> enabledFeatures,
                        SubscriptionStatus subscriptionStatus, Instant subscriptionExpiresAt,
                        String memberPin,
                        String countryCode,
                        String legalJurisdiction) {
                public UserDto(Long id, String fullName, String email, String phoneNumber, Role role,
                                Long branchId, String branchName, Long organizationId, String organizationName,
                                String organizationLogo,
                                SubscriptionTier subscriptionTier, List<String> enabledFeatures,
                                SubscriptionStatus subscriptionStatus, Instant subscriptionExpiresAt,
                                String memberPin) {
                        this(id, fullName, email, phoneNumber, role, branchId, branchName, organizationId,
                                organizationName, organizationLogo, subscriptionTier, enabledFeatures,
                                subscriptionStatus, subscriptionExpiresAt, memberPin, "IN", "INDIA_DPDP");
                }
        }

        public record LoginResponse(String token, UserDto user) {
        }

        public record GymRegistrationRequest(
                        @NotBlank @Size(max = 100) String fullName,
                        @NotBlank @Email String email,
                        @Size(max = 30) @Pattern(regexp = "^$|^\\+?[0-9][0-9 ()-]{6,19}$") String phoneNumber,
                        @NotBlank @Size(min = 8, max = 72) String password,
                        @NotBlank @Size(max = 120) String organizationName,
                        @NotBlank @Size(max = 40) @Pattern(regexp = "^[A-Za-z0-9_-]+$") String organizationCode,
                        @Size(max = 100) String branchName,
                        @Size(max = 500) String branchAddress,
                        SubscriptionTier subscriptionTier,
                        @Size(max = 2) String countryCode) {
                public GymRegistrationRequest(
                                String fullName,
                                String email,
                                String phoneNumber,
                                String password,
                                String organizationName,
                                String organizationCode,
                                String branchName,
                                String branchAddress,
                                SubscriptionTier subscriptionTier) {
                        this(fullName, email, phoneNumber, password, organizationName, organizationCode, branchName, branchAddress, subscriptionTier, "IN");
                }
        }

        public record SubscriptionStatusDto(
                        Long organizationId,
                        String organizationName,
                        String organizationCode,
                        String organizationLogo,
                        SubscriptionTier subscriptionTier,
                        SubscriptionStatus subscriptionStatus,
                        Instant expiresAt,
                        boolean brandingEnabled,
                        boolean receiptUploadEnabled,
                        boolean historyEnabled,
                        boolean ptEnabled,
                        boolean analyticsEnabled,
                        List<String> enabledFeatures,
                        Integer maxBranches,
                        Integer maxStaff,
                        Integer maxMembers,
                        String countryCode,
                        String legalJurisdiction,
                        String grievanceOfficerEmail,
                        String dpoEmail) {
                public SubscriptionStatusDto(
                                Long organizationId,
                                String organizationName,
                                String organizationCode,
                                String organizationLogo,
                                SubscriptionTier subscriptionTier,
                                SubscriptionStatus subscriptionStatus,
                                Instant expiresAt,
                                boolean brandingEnabled,
                                boolean receiptUploadEnabled,
                                boolean historyEnabled,
                                boolean ptEnabled,
                                boolean analyticsEnabled,
                                List<String> enabledFeatures,
                                Integer maxBranches,
                                Integer maxStaff,
                                Integer maxMembers) {
                        this(organizationId, organizationName, organizationCode, organizationLogo, subscriptionTier,
                                subscriptionStatus, expiresAt, brandingEnabled, receiptUploadEnabled, historyEnabled,
                                ptEnabled, analyticsEnabled, enabledFeatures, maxBranches, maxStaff, maxMembers,
                                "IN", "INDIA_DPDP", "grievance@rememberflow.com", null);
                }
        }

        public record BrandingUpdateRequest(
                        @NotBlank @Size(max = 120) String name,
                        @Size(max = 2_000_000) String logoData) {
        }

        public record SubscriptionUpgradeRequest(
                        SubscriptionTier tier) {
        }

        public record RedeemCodeRequest(
                        @NotBlank @Size(max = 64) String code) {
        }

        public record CancelSubscriptionRequest(
                        boolean immediate,
                        @Size(max = 500) String reason) {
        }

        public record SubmitUpgradeRequestDto(
                        @NotBlank @Size(max = 100) String billingPlan,
                        @NotNull @Min(1) @Max(3650) Integer durationDays,
                        @NotBlank @Size(max = 50) String paymentMethod,
                        @Size(max = 50) String gstNumber,
                        @Size(max = 255) String billingAddress,
                        @Size(max = 500) String notes,
                        String targetTier,
                        BigDecimal estimatedTopUp) {
        }

        public record UpgradeRequestDto(
                        Long id,
                        Long organizationId,
                        String organizationName,
                        String organizationCode,
                        String requesterEmail,
                        String requesterName,
                        String requesterPhone,
                        SubscriptionTier targetTier,
                        Integer durationDays,
                        String billingPlanName,
                        String paymentMethod,
                        String gstNumber,
                        String billingAddress,
                        String notes,
                        UpgradeRequestStatus status,
                        String issuedActivationCode,
                        String reviewedBy,
                        Instant reviewedAt,
                        String rejectionReason,
                        Instant createdAt,
                        BigDecimal estimatedTopUp) {
        }

        public record SaasUpgradeQuoteDto(
                        SubscriptionTier currentTier,
                        SubscriptionTier targetTier,
                        BigDecimal currentMonthlyPrice,
                        BigDecimal targetMonthlyPrice,
                        int remainingDays,
                        BigDecimal estimatedTopUp,
                        LocalDate effectiveDate) {
        }

        public record ApproveUpgradeDirectDto(
                        @Min(1) @Max(3650) Integer durationDays,
                        @Size(max = 500) String notes) {
        }

        public record RejectUpgradeDto(
                        @NotBlank @Size(max = 255) String reason) {
        }

        public record ActivationCodeDto(
                        Long id,
                        String code,
                        SubscriptionTier tier,
                        Integer durationDays,
                        ActivationCodeStatus status,
                        int maxRedemptions,
                        int redemptionCount,
                        String notes,
                        Long redeemedByOrgId,
                        String redeemedByOrgName,
                        String redeemedByEmail,
                        Instant redeemedAt,
                        Instant codeExpiresAt,
                        Instant createdAt,
                        String createdBy) {
        }

        public record GenerateCodeRequest(
                        @NotNull SubscriptionTier tier,
                        @NotNull @Min(1) @Max(3650) Integer durationDays,
                        @Size(max = 255) String notes) {
        }

        public record PlatformMetricsDto(
                        long totalGyms,
                        long activePremiumGyms,
                        long standardGyms,
                        long totalMembersAcrossGyms,
                        long totalBranches,
                        long availableCodes) {
        }

        public record PlatformOrganizationDto(
                        Long id,
                        String name,
                        String code,
                        String logoData,
                        SubscriptionTier subscriptionTier,
                        SubscriptionStatus subscriptionStatus,
                        Instant subscriptionExpiresAt,
                        Instant createdAt,
                        String ownerEmail,
                        String ownerName,
                        long branchCount,
                        long memberCount,
                        String countryCode,
                        String legalJurisdiction) {
                public PlatformOrganizationDto(Long id, String name, String code, String logoData,
                                SubscriptionTier subscriptionTier, SubscriptionStatus subscriptionStatus,
                                Instant subscriptionExpiresAt, Instant createdAt, String ownerEmail, String ownerName,
                                long branchCount, long memberCount) {
                        this(id, name, code, logoData, subscriptionTier, subscriptionStatus, subscriptionExpiresAt, createdAt,
                                ownerEmail, ownerName, branchCount, memberCount, "IN", "INDIA_DPDP");
                }
        }

        public record ExtendSubscriptionRequest(
                        @NotNull @Min(1) @Max(3650) Integer days) {
        }

        public record UpdateSubscriptionAdminRequest(
                        @NotNull SubscriptionTier tier,
                        @NotNull SubscriptionStatus status,
                        Instant expiresAt) {
        }

        public record BranchRequest(@NotBlank @Size(max = 100) String name,
                        @NotBlank @Size(max = 30) @Pattern(regexp = "^[A-Za-z0-9_-]+$") String code,
                        @NotNull BranchType type, @Size(max = 500) String address,
                        @Size(max = 30) @Pattern(regexp = "^$|^\\+?[0-9][0-9 ()-]{6,19}$") String phoneNumber,
                        boolean active) {
        }

        public record BranchDto(Long id, String name, String code, BranchType type, String address,
                        String phoneNumber, boolean active, long memberCount) {
        }

        public record PasswordResetRequest(@NotBlank @Email String email) {
        }

        public record PasswordResetResponse(String message, String developmentCode) {
        }

        public record PasswordResetConfirmRequest(@NotBlank @Email String email,
                        @NotBlank @Size(min = 6, max = 6) String code,
                        @NotBlank @Size(min = 8, max = 72) String newPassword) {
        }

        public record PasswordChangeRequest(@NotBlank String currentPassword,
                        @NotBlank @Size(min = 8, max = 72) String newPassword) {
        }

        public record ProfileRequest(@NotBlank @Size(max = 100) String fullName,
                        @NotBlank @Email String email,
                        @Size(max = 30) @Pattern(regexp = "^$|^\\+?[0-9][0-9 ()-]{6,19}$") String phoneNumber) {
        }

        public record PlanRequest(@NotBlank @Size(max = 80) String name, @Size(max = 1000) String description,
                        @NotNull @DecimalMin("0.01") BigDecimal price,
                        @Min(1) @Max(120) Integer durationMonths,
                        @Min(1) @Max(3650) Integer durationDays,
                        Long branchId,
                        boolean active,
                        PtPackageType ptPackageType,
                        Integer includedPtSessions,
                        BigDecimal ptFeeIncluded) {
        }

        public record PlanDto(Long id, String name, String description, BigDecimal price, Integer durationMonths,
                        Integer durationDays, Long branchId, String branchName, boolean active, long usageCount,
                        PtPackageType ptPackageType, Integer includedPtSessions, BigDecimal ptFeeIncluded) {
        }

        public record MembershipRegistrationRequest(@NotBlank @Size(max = 100) String fullName,
                        @NotBlank @Email String email,
                        @Size(max = 30) @Pattern(regexp = "^$|^\\+?[0-9][0-9 ()-]{6,19}$") String phoneNumber,
                        @NotBlank @Size(min = 8, max = 72) String password,
                        @NotNull Long planId,
                        Long branchId,
                        Long personalTrainerId,
                        @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal discountPercent,
                        LocalDate startDate,
                        @Size(max = 7_000_100) String receiptImageData,
                        @Size(max = 40) String paymentMethod,
                        @Size(max = 120) String transactionId) {
        }

        public record MembershipUpgradeQuoteDto(
                        int totalDays,
                        int remainingDays,
                        BigDecimal oldDailyRate,
                        BigDecimal newDailyRate,
                        BigDecimal creditAmount,
                        BigDecimal newPlanProrataCost,
                        BigDecimal topUpDue,
                        String currentPlanName,
                        BigDecimal currentPlanPrice,
                        String newPlanName,
                        BigDecimal newPlanPrice,
                        boolean hasPt) {
        }

        public record MembershipUpgradeRequest(
                        @NotNull Long newPlanId,
                        Long personalTrainerId,
                        @NotNull @DecimalMin("0") BigDecimal topUpPaid,
                        @Size(max = 40) String paymentMethod,
                        @Size(max = 120) String transactionId) {
        }

        public record MembershipRenewalRequest(@NotNull Long planId,
                        Long branchId,
                        Long personalTrainerId,
                        @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal discountPercent,
                        LocalDate startDate,
                        @Size(max = 40) String paymentMethod,
                        @Size(max = 120) String transactionId) {
        }

        public record StaffRegistrationRequest(@NotBlank @Size(max = 100) String fullName,
                        @NotBlank @Email String email,
                        @Size(max = 30) @Pattern(regexp = "^$|^\\+?[0-9][0-9 ()-]{6,19}$") String phoneNumber,
                        @NotNull Long branchId,
                        @NotBlank @Size(min = 8, max = 72) String password) {
        }

        public record StaffUpdateRequest(@NotBlank @Size(max = 100) String fullName,
                        @Size(max = 30) @Pattern(regexp = "^$|^\\+?[0-9][0-9 ()-]{6,19}$") String phoneNumber,
                        @NotNull Long branchId,
                        @Size(max = 72) String password) {
        }

        public record MembershipDto(Long id, String memberName, String email, String phoneNumber, Long planId,
                        String planName, Long branchId, String branchName,
                        LocalDate startDate, LocalDate endDate, BigDecimal listPrice,
                        BigDecimal discountPercent, BigDecimal amountPaid, MembershipStatus status,
                        LocalDate renewalStartDate, Long upgradedFromId, Instant createdAt, Instant lastActivityAt, Instant cancelledAt,
                        Integer planDurationMonths, Integer planDurationDays, boolean frozen,
                        List<FreezeDto> freezes,
                        Long personalTrainerId, String personalTrainerName,
                        Integer ptSessionsIncluded, Integer ptSessionsUsed,
                        String memberPin,
                        String paymentMethod,
                        String transactionId) {
        }

        public record MembershipGroupDto(Long memberId, String memberName, String email, String phoneNumber,
                        List<MembershipDto> terms) {
        }

        public record MembershipPageDto(List<MembershipGroupDto> members, int page, int size,
                        long totalMembers, int totalPages) {
        }

        public record FreezeDto(Long id, LocalDate startDate, LocalDate endDate, String reason,
                        String createdBy, Instant createdAt, Instant cancelledAt, LocalDate endedEarlyAt) {
        }

        public record MembershipAccessDto(boolean allowed, String status, LocalDate checkedOn, String message) {
        }

        public record MembershipFreezeRequest(@NotNull LocalDate startDate, @NotNull LocalDate endDate,
                        @NotBlank @Size(max = 500) String reason) {
        }

        public record MembershipFreezeChangeRequest(@NotBlank @Size(max = 500) String reason) {
        }

        public record MembershipTransferRequest(@NotNull Long branchId,
                        @NotBlank @Size(max = 500) String reason) {
        }

        public record MembershipCorrectionRequest(@NotNull Long planId, @NotNull LocalDate startDate,
                        @NotNull LocalDate endDate, @NotNull @DecimalMin("0.01") BigDecimal listPrice,
                        @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal discountPercent,
                        @NotNull @DecimalMin("0") BigDecimal amountPaid,
                        @NotBlank @Size(max = 500) String reason) {
        }

        public record ActionHistoryDto(Long id, Long actorId, String actorName, String actorEmail, String action,
                        String entityType, Long entityId, String reason, String beforeData, String afterData,
                        Instant occurredAt) {
        }

        public record RenewalReminderDto(Long id, Long membershipId, String memberName, String recipientEmail,
                        int daysBefore, String status, int attempts, Instant lastAttemptAt, Instant nextAttemptAt,
                        Instant sentAt, String lastError) {
        }

        public record PackagePerformanceDto(Long packageId, String packageName, long memberships,
                        BigDecimal revenue) {
        }

        public record ReportDto(LocalDate from, LocalDate to, long memberships, long cancellations,
                        BigDecimal revenue, List<PackagePerformanceDto> packages) {
        }

        public record AnalyticsPointDto(LocalDate month, long memberships, long newMembers, long renewals,
                        BigDecimal revenue, BigDecimal newRevenue, BigDecimal renewalRevenue,
                        BigDecimal discountsGiven, BigDecimal recordedPriceVariance) {
        }

        public record PackageAnalyticsDto(Long packageId, String packageName, long memberships, long newMembers,
                        long renewals, BigDecimal revenue, BigDecimal salesSharePercent,
                        BigDecimal averageSale, BigDecimal discountsGiven, BigDecimal recordedPriceVariance,
                        BigDecimal averageDiscountPercent) {
        }

        public record RenewingMemberDto(Long memberId, String memberName, String memberEmail, long terms,
                        long renewals, long renewalYears, Integer firstRenewalYear, Integer lastRenewalYear,
                        boolean renewedInEveryObservedCalendarYear, BigDecimal lifetimeRevenue,
                        Instant latestRenewalInPeriod) {
        }

        public record TaxAnalyticsDto(BigDecimal grossRecordedSales, BigDecimal estimatedTaxableSales,
                        BigDecimal estimatedGstIncluded, BigDecimal estimatedCgst, BigDecimal estimatedSgst,
                        BigDecimal estimatedIgst, BigDecimal recordedSalesExcludingEstimatedGst,
                        BigDecimal estimatedOutputGst, BigDecimal cgstRate, BigDecimal sgstRate,
                        BigDecimal igstRate, boolean estimated) {
        }

        public record AnalyticsDto(LocalDate from, LocalDate to, long totalMembershipsSold, long newMembers,
                        long membershipRenewals, long renewingMembers, long activeMemberships, long activeMembers,
                        long expiredOrCancelledMemberships, long membersLeft, long openingMembers,
                        BigDecimal retentionRate, BigDecimal churnRate, BigDecimal membershipGrowthPercent,
                        BigDecimal newMemberSalesGrowthPercent, BigDecimal totalRevenue,
                        BigDecimal newMembershipRevenue, BigDecimal renewalRevenue,
                        BigDecimal averageSale, BigDecimal averageRenewalSale, BigDecimal totalListPrice,
                        BigDecimal discountsGiven, BigDecimal recordedPriceVariance,
                        BigDecimal renewalRevenueSharePercent,
                        BigDecimal revenueGrowthPercent, TaxAnalyticsDto tax,
                        List<AnalyticsPointDto> trend, List<PackageAnalyticsDto> packagePerformance,
                        List<RenewingMemberDto> renewingMemberDetails, boolean revenueIsRecordedMembershipAmounts) {
        }

        public record CancellationRequest(@NotBlank @Size(max = 40) String reason,
                        @NotBlank @Size(min = 10, max = 2000) String remarks,
                        @NotNull LocalDate cancellationDate,
                        @Size(max = 1000) String otherExplanation) {
        }

        public record BranchDashboardDto(Long branchId, String branchName, String branchCode, BranchType branchType,
                        BigDecimal revenueThisMonth, BigDecimal revenueLastMonth, long activeMembers, long totalMembers,
                        long expiringWithinSevenDays, List<MembershipDto> expiringMembers) {
        }

        public record DashboardDto(BigDecimal revenueThisMonth, BigDecimal revenueLastMonth, long activeMembers,
                        long totalMembers, long expiringWithinSevenDays, List<MembershipDto> expiringMembers,
                        List<BranchDashboardDto> branches) {
        }

        // ─── Personal Trainer DTOs ────────────────────────────────────────────────

        public record PtProfileDto(Long id, Long userId, String fullName, String email,
                        String phoneNumber, Long branchId, String branchName,
                        String specializations, String bio,
                        BigDecimal sessionFeePerHour, BigDecimal commissionPercent,
                        boolean active, long activeMembers, Instant createdAt) {
        }

        public record PtRegistrationRequest(
                        @NotBlank @Size(max = 100) String fullName,
                        @NotBlank @Email String email,
                        @Size(max = 30) @Pattern(regexp = "^$|^\\+?[0-9][0-9 ()-]{6,19}$") String phoneNumber,
                        @NotBlank @Size(min = 8, max = 72) String password,
                        Long branchId,
                        @Size(max = 500) String specializations,
                        @Size(max = 1000) String bio,
                        @NotNull @DecimalMin("0") BigDecimal sessionFeePerHour,
                        @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal commissionPercent) {
        }

        public record PtUpdateRequest(
                        Long branchId,
                        @Size(max = 500) String specializations,
                        @Size(max = 1000) String bio,
                        @NotNull @DecimalMin("0") BigDecimal sessionFeePerHour,
                        @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal commissionPercent) {
        }

        public record PtAvailabilityDto(Long id, java.time.DayOfWeek dayOfWeek,
                        java.time.LocalTime startTime, java.time.LocalTime endTime,
                        LocalDate effectiveFrom, LocalDate effectiveTo) {
        }

        public record PtAvailabilityRequest(
                        @NotNull java.time.DayOfWeek dayOfWeek,
                        @NotNull java.time.LocalTime startTime,
                        @NotNull java.time.LocalTime endTime,
                        LocalDate effectiveFrom, LocalDate effectiveTo) {
        }

        public record PtSessionDto(Long id, Long ptId, String ptName,
                        Long membershipId, Long memberId, String memberName,
                        LocalDate scheduledDate, java.time.LocalTime startTime, java.time.LocalTime endTime,
                        String status, String notes, String cancelReason,
                        Instant completedAt, BigDecimal sessionFeeSnapshot,
                        BigDecimal commissionRateSnapshot, Instant createdAt,
                        String verificationMethod) {
        }

        public record PtSessionBookRequest(
                        @NotNull Long membershipId,
                        @NotNull LocalDate scheduledDate,
                        @NotNull java.time.LocalTime startTime,
                        @NotNull java.time.LocalTime endTime,
                        @Size(max = 1000) String notes) {
        }

        public record PtSessionCancelRequest(
                        @Size(max = 500) String reason) {
        }

        public record PtSessionCompleteRequest(
                        @Size(max = 1000) String notes,
                        @Pattern(regexp = "^$|^\\d{4}$") String pin) {
        }

        public record UpdatePinRequest(
                        @NotBlank @Pattern(regexp = "^\\d{4}$") String pin) {
        }

        public record PtSessionUpdateRequest(
                        @NotNull LocalDate scheduledDate,
                        @NotNull java.time.LocalTime startTime,
                        @NotNull java.time.LocalTime endTime,
                        @Size(max = 1000) String notes) {
        }

        public record PtEarningDto(Long id, Long sessionId, LocalDate sessionDate,
                        String memberName, BigDecimal grossRevenue, BigDecimal ptCommission,
                        BigDecimal gymRetained, BigDecimal commissionRate,
                        Long payoutId, Instant createdAt) {
        }

        public record PtPayoutDto(Long id, Long ptId, String ptName,
                        LocalDate periodFrom, LocalDate periodTo,
                        BigDecimal totalEarnings, String status,
                        Instant paidAt, String paidByName, String notes,
                        Instant createdAt) {
        }

        public record PtPayoutCreateRequest(
                        @NotNull LocalDate periodFrom,
                        @NotNull LocalDate periodTo,
                        @Size(max = 1000) String notes) {
        }

        public record PtAnalyticsDto(long activePts, long totalPts,
                        BigDecimal totalPtGrossRevenue, BigDecimal totalPtCommission,
                        BigDecimal totalGymRetained, BigDecimal pendingPayouts,
                        long sessionsCompleted, long sessionsCancelled, long sessionsMissed,
                        long activeTrainees, BigDecimal contractedPtRevenue,
                        long allocatedSessions, long remainingSessions) {
        }

        public record TenantBackupDto(
                        Long organizationId,
                        String organizationName,
                        String organizationCode,
                        SubscriptionTier subscriptionTier,
                        SubscriptionStatus subscriptionStatus,
                        Instant exportedAt,
                        String exportedBy,
                        List<BranchDto> branches,
                        List<PlanDto> packages,
                        List<MembershipDto> memberships,
                        List<UserDto> staff,
                        List<ActionHistoryDto> auditLogs) {
        }

        public record BulkMemberImportRow(
                        String fullName,
                        String email,
                        String phoneNumber,
                        String packageName,
                        String branchCode,
                        LocalDate startDate,
                        String paymentMethod) {
        }

        public record BulkMemberImportRequest(
                        List<BulkMemberImportRow> members) {
        }

        public record BulkMemberImportResultDto(
                        int totalRequested,
                        int importedCount,
                        List<String> errors) {
        }
}