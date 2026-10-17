import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export type Role = 'OWNER' | 'STAFF' | 'MEMBER' | 'PERSONAL_TRAINER' | 'SUPER_ADMIN';
export interface User {
  id: number;
  fullName: string;
  email: string;
  phoneNumber: string | null;
  role: Role;
  branchId: number | null;
  branchName: string | null;
  organizationId?: number | null;
  organizationName?: string | null;
  subscriptionTier?: 'STANDARD' | 'PREMIUM';
  subscriptionStatus?: 'ACTIVE' | 'TRIAL' | 'EXPIRED' | 'CANCELLED' | 'SUSPENDED';
  subscriptionExpiresAt?: string | null;
  enabledFeatures?: string[];
  memberPin?: string | null;
}
export interface GymRegistrationPayload {
  fullName: string;
  email: string;
  phoneNumber?: string;
  password: string;
  organizationName: string;
  organizationCode: string;
  branchName?: string;
  branchAddress?: string;
  subscriptionTier?: 'STANDARD' | 'PREMIUM';
}
export interface SubscriptionStatus {
  organizationId: number;
  organizationName: string;
  organizationCode: string;
  subscriptionTier: 'STANDARD' | 'PREMIUM';
  subscriptionStatus: 'ACTIVE' | 'TRIAL' | 'EXPIRED' | 'CANCELLED' | 'SUSPENDED';
  expiresAt: string | null;
  ptEnabled: boolean;
  historyEnabled: boolean;
  enabledFeatures: string[];
}
export interface ActivationCode {
  id: number;
  code: string;
  tier: 'STANDARD' | 'PREMIUM';
  durationDays: number | null;
  status: 'AVAILABLE' | 'REDEEMED' | 'REVOKED' | 'EXPIRED';
  maxRedemptions: number;
  redemptionCount: number;
  notes: string | null;
  redeemedByOrgId: number | null;
  redeemedByOrgName: string | null;
  redeemedByEmail: string | null;
  redeemedAt: string | null;
  codeExpiresAt: string | null;
  createdAt: string;
  createdBy: string | null;
}
export interface UpgradeRequest {
  id: number;
  organizationId: number;
  organizationName: string;
  organizationCode: string;
  requesterEmail: string;
  requesterName: string | null;
  requesterPhone: string | null;
  targetTier: 'STANDARD' | 'PREMIUM';
  durationDays: number;
  billingPlanName: string;
  paymentMethod: string;
  gstNumber: string | null;
  billingAddress: string | null;
  notes: string | null;
  status: 'PENDING' | 'APPROVED_KEY_ISSUED' | 'APPROVED_DIRECT_UPGRADE' | 'REJECTED';
  issuedActivationCode: string | null;
  reviewedBy: string | null;
  reviewedAt: string | null;
  rejectionReason: string | null;
  createdAt: string;
}
export interface UpgradeRequestPayload {
  billingPlan: string;
  durationDays: number;
  paymentMethod: string;
  gstNumber?: string;
  billingAddress?: string;
  notes?: string;
}
export interface PlatformMetrics {
  totalGyms: number;
  activePremiumGyms: number;
  standardGyms: number;
  totalMembersAcrossGyms: number;
  totalBranches: number;
  availableCodes: number;
}
export interface PlatformOrganization {
  id: number;
  name: string;
  code: string;
  subscriptionTier: 'STANDARD' | 'PREMIUM';
  subscriptionStatus: 'ACTIVE' | 'TRIAL' | 'EXPIRED' | 'CANCELLED' | 'SUSPENDED';
  subscriptionExpiresAt: string | null;
  createdAt: string;
  ownerEmail: string | null;
  ownerName: string | null;
  branchCount: number;
  memberCount: number;
}
export interface Session { token: string; user: User; }
export type BranchType = 'COMPANY_OWNED' | 'FRANCHISE';
export interface Branch { id: number; name: string; code: string; type: BranchType; address: string | null; phoneNumber: string | null; active: boolean; memberCount: number; }
export interface BranchPayload { name: string; code: string; type: BranchType; address: string; phoneNumber: string; active: boolean; }
export type PtPackageType = 'NONE' | 'PT_ONLY' | 'COMBINED';
export interface Plan { id: number; name: string; description: string; price: number; durationMonths: number | null; durationDays: number | null; branchId: number | null; branchName: string | null; active: boolean; usageCount: number; ptPackageType?: PtPackageType; includedPtSessions?: number | null; ptFeeIncluded?: number | null; }
export interface Freeze { id: number; startDate: string; endDate: string; reason: string; createdBy: string; createdAt: string; cancelledAt: string | null; endedEarlyAt: string | null; }
export interface Membership { id: number; memberName: string; email: string; phoneNumber: string | null; planId: number; planName: string; branchId: number | null; branchName: string | null; startDate: string; endDate: string; listPrice: number; discountPercent: number; amountPaid: number; status: 'ACTIVE' | 'CANCELLED'; renewalStartDate: string | null; createdAt: string; lastActivityAt: string; cancelledAt: string | null; planDurationMonths: number | null; planDurationDays: number | null; frozen: boolean; freezes: Freeze[]; personalTrainerId?: number | null; personalTrainerName?: string | null; ptSessionsIncluded?: number | null; ptSessionsUsed?: number; memberPin?: string | null; }
export interface MembershipGroup { memberId: number; memberName: string; email: string; phoneNumber: string | null; terms: Membership[]; }
export interface MembershipPage { members: MembershipGroup[]; page: number; size: number; totalMembers: number; totalPages: number; }
export interface MembershipAccess { allowed: boolean; status: string; checkedOn: string; message: string; }
export interface BranchDashboard { branchId: number; branchName: string; branchCode: string; branchType: BranchType; revenueThisMonth: number; revenueLastMonth: number; activeMembers: number; totalMembers: number; expiringWithinSevenDays: number; expiringMembers: Membership[]; }
export interface Dashboard { revenueThisMonth: number; revenueLastMonth: number; activeMembers: number; totalMembers: number; expiringWithinSevenDays: number; expiringMembers: Membership[]; branches: BranchDashboard[]; }
export interface PlanPayload { name: string; description: string; price: number; durationMonths: number | null; durationDays: number | null; branchId: number | null; active: boolean; ptPackageType?: PtPackageType; includedPtSessions?: number | null; ptFeeIncluded?: number | null; }
export interface MembershipPayload { fullName: string; email: string; phoneNumber: string; password: string; planId: number; branchId: number | null; personalTrainerId?: number | null; discountPercent: number; startDate: string; receiptImageData?: string | null; }
export interface RenewalPayload { planId: number; branchId: number | null; personalTrainerId?: number | null; discountPercent: number; startDate: string; }
export interface StaffPayload { fullName: string; email: string; phoneNumber: string; password: string; branchId: number; }
export interface ProfilePayload { fullName: string; email: string; phoneNumber: string; }
export interface OwnerSetupPayload { fullName: string; email: string; phoneNumber: string; password: string; setupKey: string; organizationName?: string; branchName?: string; branchAddress?: string; }
export interface ResetRequestResponse { message: string; developmentCode: string | null; }
export interface ActionHistory { id: number; actorId: number; actorName: string; actorEmail: string; action: string; entityType: string; entityId: number; reason: string | null; beforeData: string | null; afterData: string | null; occurredAt: string; }
export interface RenewalReminder { id: number; membershipId: number; memberName: string; recipientEmail: string; daysBefore: number; status: string; attempts: number; lastAttemptAt: string | null; nextAttemptAt: string; sentAt: string | null; lastError: string | null; }
export interface PackagePerformance { packageId: number; packageName: string; memberships: number; revenue: number; }
export interface Report { from: string; to: string; memberships: number; cancellations: number; revenue: number; packages: PackagePerformance[]; }
export interface AnalyticsPoint { month: string; memberships: number; newMembers: number; renewals: number; revenue: number; newRevenue: number; renewalRevenue: number; discountsGiven: number; recordedPriceVariance: number; }
export interface PackageAnalytics { packageId: number; packageName: string; memberships: number; newMembers: number; renewals: number; revenue: number; salesSharePercent: number; averageSale: number; discountsGiven: number; recordedPriceVariance: number; averageDiscountPercent: number; }
export interface RenewingMember { memberId: number; memberName: string; memberEmail: string; terms: number; renewals: number; renewalYears: number; firstRenewalYear: number | null; lastRenewalYear: number | null; renewedInEveryObservedCalendarYear: boolean; lifetimeRevenue: number; latestRenewalInPeriod: string; }
export interface TaxAnalytics { grossRecordedSales: number; estimatedTaxableSales: number; estimatedGstIncluded: number; estimatedCgst: number; estimatedSgst: number; estimatedIgst: number; recordedSalesExcludingEstimatedGst: number; estimatedOutputGst: number; cgstRate: number; sgstRate: number; igstRate: number; estimated: boolean; }
export interface Analytics { from: string; to: string; totalMembershipsSold: number; newMembers: number; membershipRenewals: number; renewingMembers: number; activeMemberships: number; activeMembers: number; expiredOrCancelledMemberships: number; membersLeft: number; openingMembers: number; retentionRate: number; churnRate: number; membershipGrowthPercent: number | null; newMemberSalesGrowthPercent: number | null; totalRevenue: number; newMembershipRevenue: number; renewalRevenue: number; averageSale: number; averageRenewalSale: number; totalListPrice: number; discountsGiven: number; recordedPriceVariance: number; renewalRevenueSharePercent: number; revenueGrowthPercent: number | null; tax: TaxAnalytics; trend: AnalyticsPoint[]; packagePerformance: PackageAnalytics[]; renewingMemberDetails: RenewingMember[]; revenueIsRecordedMembershipAmounts: boolean; }
export type CancellationReason = 'NO_LONGER_INTERESTED' | 'RELOCATED' | 'MEDICAL' | 'FINANCIAL' | 'SERVICE_ISSUE' | 'OTHER';
export interface CancellationPayload { reason: CancellationReason; remarks: string; cancellationDate: string; otherExplanation: string; }

// ─── PT Interfaces ────────────────────────────────────────────────────────
export interface PtProfile {
  id: number;
  userId: number;
  fullName: string;
  email: string;
  phoneNumber: string | null;
  branchId: number | null;
  branchName: string | null;
  specializations: string | null;
  bio: string | null;
  sessionFeePerHour: number;
  commissionPercent: number;
  active: boolean;
  activeMembers: number;
  createdAt: string;
}

export interface PtRegistrationPayload {
  fullName: string;
  email: string;
  phoneNumber: string;
  password: string;
  branchId: number | null;
  specializations: string;
  bio: string;
  sessionFeePerHour: number;
  commissionPercent: number;
}

export interface PtUpdatePayload {
  branchId: number | null;
  specializations: string;
  bio: string;
  sessionFeePerHour: number;
  commissionPercent: number;
}

export interface PtAvailability {
  id: number;
  dayOfWeek: string;
  startTime: string;
  endTime: string;
  effectiveFrom: string | null;
  effectiveTo: string | null;
}

export interface PtAvailabilityPayload {
  dayOfWeek: string;
  startTime: string;
  endTime: string;
  effectiveFrom?: string | null;
  effectiveTo?: string | null;
}

export type PtSessionStatus = 'TENTATIVE' | 'BOOKED' | 'COMPLETED' | 'CANCELLED' | 'MISSED';

export interface PtSession {
  id: number;
  ptId: number;
  ptName: string;
  membershipId: number;
  memberId: number;
  memberName: string;
  scheduledDate: string;
  startTime: string;
  endTime: string;
  status: PtSessionStatus;
  notes: string | null;
  cancelReason: string | null;
  completedAt: string | null;
  sessionFeeSnapshot: number;
  commissionRateSnapshot: number;
  createdAt: string;
  verificationMethod?: string | null;
}

export interface PtSessionBookPayload {
  membershipId: number;
  scheduledDate: string;
  startTime: string;
  endTime: string;
  notes?: string;
}

export interface PtSessionUpdatePayload {
  scheduledDate: string;
  startTime: string;
  endTime: string;
  notes?: string;
}

export interface PtEarning {
  id: number;
  sessionId: number;
  sessionDate: string;
  memberName: string;
  grossRevenue: number;
  ptCommission: number;
  gymRetained: number;
  commissionRate: number;
  payoutId: number | null;
  createdAt: string;
}

export interface PtPayout {
  id: number;
  ptId: number;
  ptName: string;
  periodFrom: string;
  periodTo: string;
  totalEarnings: number;
  status: 'PENDING' | 'PAID';
  paidAt: string | null;
  paidByName: string | null;
  notes: string | null;
  createdAt: string;
}

export interface PtPayoutCreatePayload {
  periodFrom: string;
  periodTo: string;
  notes?: string;
}

export interface PtAnalytics {
  activePts: number;
  totalPts: number;
  totalPtGrossRevenue: number;
  totalPtCommission: number;
  totalGymRetained: number;
  pendingPayouts: number;
  sessionsCompleted: number;
  sessionsCancelled: number;
  sessionsMissed: number;
  activeTrainees: number;
  contractedPtRevenue: number;
  allocatedSessions: number;
  remainingSessions: number;
}

@Injectable({ providedIn: 'root' })
export class GymApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api';
  login(email: string, password: string): Observable<Session> { return this.http.post<Session>(`${this.baseUrl}/auth/login`, { email, password }); }
  setupOwner(payload: OwnerSetupPayload): Observable<Session> { return this.http.post<Session>(`${this.baseUrl}/auth/setup-owner`, payload); }
  registerGym(payload: GymRegistrationPayload): Observable<Session> { return this.http.post<Session>(`${this.baseUrl}/auth/register-gym`, payload); }
  currentSubscription(): Observable<SubscriptionStatus> { return this.http.get<SubscriptionStatus>(`${this.baseUrl}/subscription/current`); }
  upgradeSubscription(tier: 'PREMIUM' = 'PREMIUM'): Observable<SubscriptionStatus> { return this.http.post<SubscriptionStatus>(`${this.baseUrl}/subscription/upgrade`, { tier }); }
  redeemActivationCode(code: string): Observable<SubscriptionStatus> { return this.http.post<SubscriptionStatus>(`${this.baseUrl}/subscription/redeem`, { code }); }
  requestUpgrade(payload: UpgradeRequestPayload): Observable<UpgradeRequest> { return this.http.post<UpgradeRequest>(`${this.baseUrl}/subscription/request-upgrade`, payload); }
  myUpgradeRequests(): Observable<UpgradeRequest[]> { return this.http.get<UpgradeRequest[]>(`${this.baseUrl}/subscription/my-requests`); }
  cancelSubscription(immediate: boolean, reason?: string): Observable<SubscriptionStatus> { return this.http.post<SubscriptionStatus>(`${this.baseUrl}/subscription/cancel`, { immediate, reason: reason || '' }); }
  reactivateSubscription(): Observable<SubscriptionStatus> { return this.http.post<SubscriptionStatus>(`${this.baseUrl}/subscription/reactivate`, {}); }
  platformMetrics(): Observable<PlatformMetrics> { return this.http.get<PlatformMetrics>(`${this.baseUrl}/platform/metrics`); }
  platformOrganizations(search?: string): Observable<PlatformOrganization[]> {
    let params = new HttpParams();
    if (search) params = params.set('search', search);
    return this.http.get<PlatformOrganization[]>(`${this.baseUrl}/platform/organizations`, { params });
  }
  platformUpgradeRequests(status?: string): Observable<UpgradeRequest[]> {
    let params = new HttpParams();
    if (status && status !== 'ALL') params = params.set('status', status);
    return this.http.get<UpgradeRequest[]>(`${this.baseUrl}/platform/upgrade-requests`, { params });
  }
  approveUpgradeWithKey(id: number): Observable<UpgradeRequest> {
    return this.http.post<UpgradeRequest>(`${this.baseUrl}/platform/upgrade-requests/${id}/approve-key`, {});
  }
  approveUpgradeDirect(id: number, durationDays?: number): Observable<UpgradeRequest> {
    return this.http.post<UpgradeRequest>(`${this.baseUrl}/platform/upgrade-requests/${id}/approve-direct`, { durationDays });
  }
  rejectUpgradeRequest(id: number, reason: string): Observable<UpgradeRequest> {
    return this.http.post<UpgradeRequest>(`${this.baseUrl}/platform/upgrade-requests/${id}/reject`, { reason });
  }
  updatePlatformOrgSubscription(id: number, payload: { tier?: string; status?: string; expiresAt?: string | null }): Observable<PlatformOrganization> {
    return this.http.put<PlatformOrganization>(`${this.baseUrl}/platform/organizations/${id}/subscription`, payload);
  }
  extendPlatformOrgSubscription(id: number, days: number): Observable<PlatformOrganization> {
    return this.http.post<PlatformOrganization>(`${this.baseUrl}/platform/organizations/${id}/extend`, { days });
  }
  suspendPlatformOrg(id: number, suspend: boolean): Observable<PlatformOrganization> {
    return this.http.post<PlatformOrganization>(`${this.baseUrl}/platform/organizations/${id}/suspend?suspend=${suspend}`, {});
  }
  platformActivationCodes(): Observable<ActivationCode[]> { return this.http.get<ActivationCode[]>(`${this.baseUrl}/platform/activation-codes`); }
  generateActivationCode(payload: { tier?: string; durationDays?: number | null; notes?: string }): Observable<ActivationCode> {
    return this.http.post<ActivationCode>(`${this.baseUrl}/platform/activation-codes`, payload);
  }
  revokeActivationCode(id: number): Observable<ActivationCode> { return this.http.delete<ActivationCode>(`${this.baseUrl}/platform/activation-codes/${id}`); }
  requestPasswordReset(email: string): Observable<ResetRequestResponse> { return this.http.post<ResetRequestResponse>(`${this.baseUrl}/auth/password-reset/request`, { email }); }
  confirmPasswordReset(payload: { email: string; code: string; newPassword: string }): Observable<string> { return this.http.post(`${this.baseUrl}/auth/password-reset/confirm`, payload, { responseType: 'text' }); }
  changePassword(payload: { currentPassword: string; newPassword: string }): Observable<Session> { return this.http.post<Session>(`${this.baseUrl}/auth/change-password`, payload); }
  updateProfile(payload: ProfilePayload): Observable<Session> { return this.http.put<Session>(`${this.baseUrl}/auth/profile`, payload); }
  logout(): Observable<void> { return this.http.post<void>(`${this.baseUrl}/auth/logout`, {}); }
  plans(): Observable<Plan[]> { return this.http.get<Plan[]>(`${this.baseUrl}/plans`); }
  branches(): Observable<Branch[]> { return this.http.get<Branch[]>(`${this.baseUrl}/branches`); }
  createBranch(payload: BranchPayload): Observable<Branch> { return this.http.post<Branch>(`${this.baseUrl}/branches`, payload); }
  updateBranch(id: number, payload: BranchPayload): Observable<Branch> { return this.http.put<Branch>(`${this.baseUrl}/branches/${id}`, payload); }
  createPlan(payload: PlanPayload): Observable<Plan> { return this.http.post<Plan>(`${this.baseUrl}/plans`, payload); }
  updatePlan(id: number, payload: PlanPayload): Observable<Plan> { return this.http.put<Plan>(`${this.baseUrl}/plans/${id}`, payload); }
  staff(): Observable<User[]> { return this.http.get<User[]>(`${this.baseUrl}/staff`); }
  createStaff(payload: StaffPayload): Observable<User> { return this.http.post<User>(`${this.baseUrl}/staff`, payload); }
  deleteStaff(id: number): Observable<void> { return this.http.delete<void>(`${this.baseUrl}/staff/${id}`); }
  memberships(): Observable<Membership[]> { return this.http.get<Membership[]>(`${this.baseUrl}/memberships`); }
  recentMemberships(branchId: number | null): Observable<Membership[]> {
    let params = new HttpParams();
    if (branchId !== null) params = params.set('branchId', branchId);
    return this.http.get<Membership[]>(`${this.baseUrl}/memberships/recent`, { params });
  }
  expiringMemberships(withinDays = 7): Observable<Membership[]> {
    const params = new HttpParams().set('withinDays', withinDays);
    return this.http.get<Membership[]>(`${this.baseUrl}/memberships/expiring`, { params });
  }
  membershipPage(page: number, size: number, search: string, filter: string): Observable<MembershipPage> {
    const params = new HttpParams().set('page', page).set('size', size)
      .set('search', search).set('filter', filter);
    return this.http.get<MembershipPage>(`${this.baseUrl}/memberships/page`, { params });
  }
  registerMembership(payload: MembershipPayload): Observable<Membership> { return this.http.post<Membership>(`${this.baseUrl}/memberships`, payload); }
  renewMembership(id: number, payload: RenewalPayload): Observable<Membership> { return this.http.post<Membership>(`${this.baseUrl}/memberships/${id}/renew`, payload); }
  cancelMembership(id: number, payload: CancellationPayload): Observable<void> { return this.http.post<void>(`${this.baseUrl}/memberships/${id}/cancel`, payload); }
  cancelRenewal(id: number, payload: CancellationPayload): Observable<void> { return this.http.post<void>(`${this.baseUrl}/memberships/${id}/cancel-renewal`, payload); }
  freezeMembership(id: number, payload: { startDate: string; endDate: string; reason: string }): Observable<Membership> { return this.http.post<Membership>(`${this.baseUrl}/memberships/${id}/freeze`, payload); }
  cancelFreeze(id: number, freezeId: number, reason: string): Observable<Membership> { return this.http.post<Membership>(`${this.baseUrl}/memberships/${id}/freezes/${freezeId}/cancel`, { reason }); }
  endFreezeEarly(id: number, freezeId: number, reason: string): Observable<Membership> { return this.http.post<Membership>(`${this.baseUrl}/memberships/${id}/freezes/${freezeId}/end-early`, { reason }); }
  membershipAccess(id: number): Observable<MembershipAccess> { return this.http.get<MembershipAccess>(`${this.baseUrl}/memberships/${id}/access`); }
  transferMembership(id: number, payload: { branchId: number; reason: string }): Observable<Membership> { return this.http.post<Membership>(`${this.baseUrl}/memberships/${id}/transfer`, payload); }
  correctMembership(id: number, payload: { planId: number; startDate: string; endDate: string; listPrice: number; discountPercent: number; amountPaid: number; reason: string }): Observable<Membership> { return this.http.put<Membership>(`${this.baseUrl}/memberships/${id}/correction`, payload); }
  history(): Observable<ActionHistory[]> { return this.http.get<ActionHistory[]>(`${this.baseUrl}/history`); }
  renewalReminders(): Observable<RenewalReminder[]> { return this.http.get<RenewalReminder[]>(`${this.baseUrl}/renewal-reminders`); }
  report(from: string, to: string, branchId: number | null): Observable<Report> {
    let params = new HttpParams().set('from', from).set('to', to);
    if (branchId !== null) params = params.set('branchId', branchId);
    return this.http.get<Report>(`${this.baseUrl}/reports`, { params });
  }
  analytics(from: string, to: string, branchId: number | null): Observable<Analytics> {
    let params = new HttpParams().set('from', from).set('to', to);
    if (branchId !== null) params = params.set('branchId', branchId);
    return this.http.get<Analytics>(`${this.baseUrl}/analytics`, { params });
  }
  exportReport(from: string, to: string, type: string, branchId: number | null): Observable<Blob> {
    let params = new HttpParams().set('from', from).set('to', to).set('type', type);
    if (branchId !== null) params = params.set('branchId', branchId);
    return this.http.get(`${this.baseUrl}/reports/export.csv`, { params, responseType: 'blob' });
  }
  dashboard(): Observable<Dashboard> { return this.http.get<Dashboard>(`${this.baseUrl}/dashboard`); }

  // ─── PT Methods ───────────────────────────────────────────────────────────
  personalTrainers(): Observable<PtProfile[]> { return this.http.get<PtProfile[]>(`${this.baseUrl}/pt`); }
  createPt(payload: PtRegistrationPayload): Observable<PtProfile> { return this.http.post<PtProfile>(`${this.baseUrl}/pt`, payload); }
  updatePt(id: number, payload: PtUpdatePayload): Observable<PtProfile> { return this.http.put<PtProfile>(`${this.baseUrl}/pt/${id}`, payload); }
  activatePt(id: number): Observable<PtProfile> { return this.http.post<PtProfile>(`${this.baseUrl}/pt/${id}/activate`, {}); }
  deactivatePt(id: number): Observable<PtProfile> { return this.http.post<PtProfile>(`${this.baseUrl}/pt/${id}/deactivate`, {}); }
  myPtProfile(): Observable<PtProfile> { return this.http.get<PtProfile>(`${this.baseUrl}/pt/me`); }
  myPtAvailability(): Observable<PtAvailability[]> { return this.http.get<PtAvailability[]>(`${this.baseUrl}/pt/me/availability`); }
  setPtAvailability(payload: PtAvailabilityPayload[]): Observable<PtAvailability[]> { return this.http.put<PtAvailability[]>(`${this.baseUrl}/pt/me/availability`, payload); }
  getPtAvailability(ptId: number): Observable<PtAvailability[]> { return this.http.get<PtAvailability[]>(`${this.baseUrl}/pt/${ptId}/availability`); }
  myPtSessions(from?: string, to?: string): Observable<PtSession[]> {
    let params = new HttpParams();
    if (from) params = params.set('from', from);
    if (to) params = params.set('to', to);
    return this.http.get<PtSession[]>(`${this.baseUrl}/pt/me/sessions`, { params });
  }
  myPtTrainees(): Observable<Membership[]> { return this.http.get<Membership[]>(`${this.baseUrl}/pt/me/trainees`); }
  ptSessions(ptId: number): Observable<PtSession[]> { return this.http.get<PtSession[]>(`${this.baseUrl}/pt/${ptId}/sessions`); }
  memberPtSessions(): Observable<PtSession[]> { return this.http.get<PtSession[]>(`${this.baseUrl}/pt/member/sessions`); }
  bookPtSession(payload: PtSessionBookPayload): Observable<PtSession> { return this.http.post<PtSession>(`${this.baseUrl}/pt/sessions`, payload); }
  reschedulePtSession(sessionId: number, payload: PtSessionUpdatePayload): Observable<PtSession> { return this.http.put<PtSession>(`${this.baseUrl}/pt/sessions/${sessionId}`, payload); }
  cancelPtSession(sessionId: number, reason: string): Observable<PtSession> { return this.http.post<PtSession>(`${this.baseUrl}/pt/sessions/${sessionId}/cancel`, { reason }); }
  completePtSession(sessionId: number, notes?: string, pin?: string): Observable<PtSession> { return this.http.post<PtSession>(`${this.baseUrl}/pt/sessions/${sessionId}/complete`, { notes: notes || '', pin: pin || '' }); }
  markPtSessionMissed(sessionId: number): Observable<PtSession> { return this.http.post<PtSession>(`${this.baseUrl}/pt/sessions/${sessionId}/missed`, {}); }
  myPtEarnings(): Observable<PtEarning[]> { return this.http.get<PtEarning[]>(`${this.baseUrl}/pt/me/earnings`); }
  ptEarnings(ptId: number): Observable<PtEarning[]> { return this.http.get<PtEarning[]>(`${this.baseUrl}/pt/${ptId}/earnings`); }
  myPtPayouts(): Observable<PtPayout[]> { return this.http.get<PtPayout[]>(`${this.baseUrl}/pt/me/payouts`); }
  allPtPayouts(): Observable<PtPayout[]> { return this.http.get<PtPayout[]>(`${this.baseUrl}/pt/payouts`); }
  createPtPayout(ptId: number, payload: PtPayoutCreatePayload): Observable<PtPayout> { return this.http.post<PtPayout>(`${this.baseUrl}/pt/${ptId}/payouts`, payload); }
  markPtPayoutPaid(payoutId: number): Observable<PtPayout> { return this.http.post<PtPayout>(`${this.baseUrl}/pt/payouts/${payoutId}/mark-paid`, {}); }
  ptAnalytics(from: string, to: string): Observable<PtAnalytics> {
    const params = new HttpParams().set('from', from).set('to', to);
    return this.http.get<PtAnalytics>(`${this.baseUrl}/pt/analytics`, { params });
  }
  storeSession(token: string, user: User): void { sessionStorage.setItem('gym.user', JSON.stringify(user)); sessionStorage.removeItem('gym.session-expired'); }
  clearSession(): void { sessionStorage.removeItem('gym.user'); sessionStorage.removeItem('gym.session-expired'); }
  message(error: unknown, fallback: string): string {
    if (!(error instanceof HttpErrorResponse)) return fallback;
    const body = error.error && typeof error.error === 'object'
      ? error.error as { message?: string; error?: string; detail?: string; errors?: Array<{ field?: string; defaultMessage?: string }> }
      : null;
    if (error.status === 0) return 'Cannot reach the API. Start the Spring Boot server and try again.';
    if (error.status === 401 && error.url?.endsWith('/auth/login')) {
      return 'Email or password is incorrect.';
    }
    if (body?.errors?.length) {
      return body.errors.map((item) => item.field && item.defaultMessage
        ? `${item.field}: ${item.defaultMessage}` : item.defaultMessage ?? '').filter(Boolean).join(' ');
    }
    if (body?.message || body?.detail) return body.message ?? body.detail!;
    if (body?.error && !['bad request', 'forbidden', 'internal server error']
      .includes(body.error.toLowerCase())) return body.error;
    if (typeof error.error === 'string' && !error.error.trimStart().startsWith('<')) return error.error;
    return fallback;
  }
}