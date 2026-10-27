import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  ActionHistory,
  ActivationCode,
  Analytics,
  AnalyticsPoint,
  Branch,
  BranchDashboard,
  BranchPayload,
  BranchType,
  BulkMemberImportResult,
  BulkMemberImportRow,
  CancellationPayload,
  CancellationReason,
  Dashboard,
  Freeze,
  GymRegistrationOtpRequest,
  GymRegistrationOtpResponse,
  GymRegistrationPayload,
  Membership,
  MembershipAccess,
  MembershipGroup,
  MembershipPage,
  MembershipPayload,
  MembershipUpgradePayload,
  MembershipUpgradeQuote,
  PackageAnalytics,
  PackagePerformance,
  Plan,
  PlanPayload,
  PlatformMetrics,
  PlatformOrganization,
  PlatformGymDetail,
  ProfilePayload,
  PtAnalytics,
  PtAvailability,
  PtAvailabilityPayload,
  PtEarning,
  PtPackageType,
  PtPayout,
  PtPayoutCreatePayload,
  PtProfile,
  PtRegistrationPayload,
  PtSession,
  PtSessionBookPayload,
  PtSessionStatus,
  PtSessionUpdatePayload,
  PtUpdatePayload,
  RenewalPayload,
  RenewalRequest,
  RenewalSubmissionPayload,
  RenewalApprovalPayload,
  RenewalRejectionPayload,
  MemberPackageUpgradeRequest,
  MemberUpgradeSubmissionPayload,
  MemberPackageUpgradeApprovalPayload,
  MemberPackageUpgradeRejectionPayload,
  RenewalReminder,
  RenewingMember,
  Report,
  ResetRequestResponse,
  Role,
  PlatformUpgradeQuote,
  SaasUpgradeQuote,
  Session,
  StaffPayload,
  SubscriptionStatus,
  TaxAnalytics,
  TenantBackup,
  UpgradeRequest,
  UpgradeRequestPayload,
  User
} from './core/models';

export * from './core/models';

@Injectable({ providedIn: 'root' })
export class GymApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api';
  me(): Observable<User> { return this.http.get<User>(`${this.baseUrl}/auth/me`); }
  login(email: string, password: string): Observable<Session> { return this.http.post<Session>(`${this.baseUrl}/auth/login`, { email, password }); }
  sendGymRegistrationOtp(payload: GymRegistrationOtpRequest): Observable<GymRegistrationOtpResponse> { return this.http.post<GymRegistrationOtpResponse>(`${this.baseUrl}/auth/register-gym/send-otp`, payload); }
  registerGym(payload: GymRegistrationPayload): Observable<Session> { return this.http.post<Session>(`${this.baseUrl}/auth/register-gym`, payload); }
  currentSubscription(): Observable<SubscriptionStatus> { return this.http.get<SubscriptionStatus>(`${this.baseUrl}/subscription/current`); }
  upgradeSubscription(tier: 'PREMIUM' = 'PREMIUM'): Observable<SubscriptionStatus> { return this.http.post<SubscriptionStatus>(`${this.baseUrl}/subscription/upgrade`, { tier }); }
  redeemActivationCode(code: string): Observable<SubscriptionStatus> { return this.http.post<SubscriptionStatus>(`${this.baseUrl}/subscription/redeem`, { code }); }
  requestUpgrade(payload: UpgradeRequestPayload): Observable<UpgradeRequest> { return this.http.post<UpgradeRequest>(`${this.baseUrl}/subscription/request-upgrade`, payload); }
  myUpgradeRequests(): Observable<UpgradeRequest[]> { return this.http.get<UpgradeRequest[]>(`${this.baseUrl}/subscription/my-requests`); }
  cancelSubscription(immediate: boolean, reason?: string): Observable<SubscriptionStatus> { return this.http.post<SubscriptionStatus>(`${this.baseUrl}/subscription/cancel`, { immediate, reason: reason || '' }); }
  reactivateSubscription(): Observable<SubscriptionStatus> { return this.http.post<SubscriptionStatus>(`${this.baseUrl}/subscription/reactivate`, {}); }
  platformMetrics(): Observable<PlatformMetrics> { return this.http.get<PlatformMetrics>(`${this.baseUrl}/platform/metrics`); }
  platformOrganizationDetails(id: number): Observable<PlatformGymDetail> {
    return this.http.get<PlatformGymDetail>(`${this.baseUrl}/platform/organizations/${id}/details`);
  }
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
  updateBranding(payload: { name: string; logoData: string | null }): Observable<User> { return this.http.put<User>(`${this.baseUrl}/organization/branding`, payload); }
  logout(): Observable<void> { return this.http.post<void>(`${this.baseUrl}/auth/logout`, {}); }
  plans(): Observable<Plan[]> { return this.http.get<Plan[]>(`${this.baseUrl}/plans`); }
  branches(): Observable<Branch[]> { return this.http.get<Branch[]>(`${this.baseUrl}/branches`); }
  createBranch(payload: BranchPayload): Observable<Branch> { return this.http.post<Branch>(`${this.baseUrl}/branches`, payload); }
  updateBranch(id: number, payload: BranchPayload): Observable<Branch> { return this.http.put<Branch>(`${this.baseUrl}/branches/${id}`, payload); }
  createPlan(payload: PlanPayload): Observable<Plan> { return this.http.post<Plan>(`${this.baseUrl}/plans`, payload); }
  updatePlan(id: number, payload: PlanPayload): Observable<Plan> { return this.http.put<Plan>(`${this.baseUrl}/plans/${id}`, payload); }
  staff(): Observable<User[]> { return this.http.get<User[]>(`${this.baseUrl}/staff`); }
  createStaff(payload: StaffPayload): Observable<User> { return this.http.post<User>(`${this.baseUrl}/staff`, payload); }
  updateStaff(id: number, payload: { fullName: string; phoneNumber?: string; branchId: number; password?: string }): Observable<User> {
    return this.http.put<User>(`${this.baseUrl}/staff/${id}`, payload);
  }
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
    quoteMembershipUpgrade(membershipId: number, newPlanId: number): Observable<MembershipUpgradeQuote> {
    const params = new HttpParams().set('newPlanId', newPlanId);
    return this.http.get<MembershipUpgradeQuote>(`${this.baseUrl}/memberships/${membershipId}/upgrade-quote`, { params });
  }

  upgradeMembership(membershipId: number, payload: MembershipUpgradePayload): Observable<Membership> {
    return this.http.post<Membership>(`${this.baseUrl}/memberships/${membershipId}/upgrade`, payload);
  }

  quotePlatformUpgrade(targetTier?: string): Observable<PlatformUpgradeQuote> {
    return this.quoteSaasUpgrade(targetTier);
  }

  quoteSaasUpgrade(targetTier?: string): Observable<SaasUpgradeQuote> {
    let params = new HttpParams();
    if (targetTier) params = params.set('targetTier', targetTier);
    return this.http.get<SaasUpgradeQuote>(`${this.baseUrl}/subscription/upgrade-quote`, { params });
  }

  renewMembership(id: number, payload: RenewalPayload): Observable<Membership> { return this.http.post<Membership>(`${this.baseUrl}/memberships/${id}/renew`, payload); }
  submitRenewalRequest(id: number, payload: RenewalSubmissionPayload): Observable<RenewalRequest> { return this.http.post<RenewalRequest>(`${this.baseUrl}/memberships/${id}/renewal-requests`, payload); }
  renewalRequests(status?: string): Observable<RenewalRequest[]> {
    let params = new HttpParams();
    if (status) params = params.set('status', status);
    return this.http.get<RenewalRequest[]>(`${this.baseUrl}/membership-renewal-requests`, { params });
  }
  approveRenewalRequest(id: number, payload: RenewalApprovalPayload): Observable<RenewalRequest> { return this.http.post<RenewalRequest>(`${this.baseUrl}/membership-renewal-requests/${id}/approve`, payload); }
  rejectRenewalRequest(id: number, payload: RenewalRejectionPayload): Observable<RenewalRequest> { return this.http.post<RenewalRequest>(`${this.baseUrl}/membership-renewal-requests/${id}/reject`, payload); }
  submitMemberUpgradeRequest(id: number, payload: MemberUpgradeSubmissionPayload): Observable<MemberPackageUpgradeRequest> {
    return this.http.post<MemberPackageUpgradeRequest>(`${this.baseUrl}/memberships/${id}/upgrade-requests`, payload);
  }
  memberUpgradeRequests(status?: string): Observable<MemberPackageUpgradeRequest[]> {
    let params = new HttpParams();
    if (status) params = params.set('status', status);
    return this.http.get<MemberPackageUpgradeRequest[]>(`${this.baseUrl}/membership-upgrade-requests`, { params });
  }
  approveMemberUpgradeRequest(id: number, payload?: MemberPackageUpgradeApprovalPayload): Observable<MemberPackageUpgradeRequest> {
    return this.http.post<MemberPackageUpgradeRequest>(`${this.baseUrl}/membership-upgrade-requests/${id}/approve`, payload || {});
  }
  rejectMemberUpgradeRequest(id: number, payload?: MemberPackageUpgradeRejectionPayload): Observable<MemberPackageUpgradeRequest> {
    return this.http.post<MemberPackageUpgradeRequest>(`${this.baseUrl}/membership-upgrade-requests/${id}/reject`, payload || {});
  }
  cancelMembership(id: number, payload: CancellationPayload): Observable<void> { return this.http.post<void>(`${this.baseUrl}/memberships/${id}/cancel`, payload); }
  cancelRenewal(id: number, payload: CancellationPayload): Observable<void> { return this.http.post<void>(`${this.baseUrl}/memberships/${id}/cancel-renewal`, payload); }
  freezeMembership(id: number, payload: { startDate: string; endDate: string; reason: string }): Observable<Membership> { return this.http.post<Membership>(`${this.baseUrl}/memberships/${id}/freeze`, payload); }
  cancelFreeze(id: number, freezeId: number, reason: string): Observable<Membership> { return this.http.post<Membership>(`${this.baseUrl}/memberships/${id}/freezes/${freezeId}/cancel`, { reason }); }
  endFreezeEarly(id: number, freezeId: number, reason: string): Observable<Membership> { return this.http.post<Membership>(`${this.baseUrl}/memberships/${id}/freezes/${freezeId}/end-early`, { reason }); }
  membershipAccess(id: number): Observable<MembershipAccess> { return this.http.get<MembershipAccess>(`${this.baseUrl}/memberships/${id}/access`); }
  updateMyPin(pin: string): Observable<void> { return this.http.put<void>(`${this.baseUrl}/memberships/my-pin`, { pin }); }
  transferMembership(id: number, payload: { branchId: number; reason: string }): Observable<Membership> { return this.http.post<Membership>(`${this.baseUrl}/memberships/${id}/transfer`, payload); }
  correctMembership(id: number, payload: { planId: number; startDate: string; endDate: string; listPrice: number; discountPercent: number; amountPaid: number; reason: string }): Observable<Membership> { return this.http.put<Membership>(`${this.baseUrl}/memberships/${id}/correction`, payload); }
  history(): Observable<ActionHistory[]> { return this.http.get<ActionHistory[]>(`${this.baseUrl}/history`); }
  sendRenewalReminder(id: number): Observable<RenewalReminder> { return this.http.post<RenewalReminder>(`${this.baseUrl}/memberships/${id}/send-reminder`, {}); }
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
  downloadTenantBackup(): Observable<TenantBackup> {
    return this.http.get<TenantBackup>(`${this.baseUrl}/backup.json`);
  }
  bulkImportMembers(members: BulkMemberImportRow[]): Observable<BulkMemberImportResult> {
    return this.http.post<BulkMemberImportResult>(`${this.baseUrl}/memberships/bulk-import`, { members });
  }

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