import { Component, computed, effect, signal, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { GymApiService, ActionHistory, Analytics, Branch, BranchPayload, BranchType, CancellationReason, Dashboard, Freeze, Membership, MembershipAccess, MembershipGroup, Plan, RenewalReminder, User, PtProfile, PtSession, PtEarning, PtPayout, PtAnalytics, PtPackageType, ActivationCode, PlatformMetrics, PlatformOrganization, UpgradeRequest, UpgradeRequestPayload } from './gym-api.service';
import { CsrfTokenService } from './csrf-token.service';

type View = 'overview' | 'members' | 'packages' | 'register' | 'team' | 'account' | 'branches' | 'reports' | 'history' | 'pt-management' | 'pt-dashboard' | 'pt-earnings' | 'platform-admin';
type AuthMode = 'home' | 'login' | 'setup-owner' | 'forgot-password' | 'register-gym';
type MemberFilter = 'all' | 'active' | 'expiring' | 'cancelled';
type PlanFilter = 'active' | 'archived' | 'all';
type AdjustmentMode = 'freeze' | 'transfer' | 'correct' | 'cancel-freeze' | 'end-freeze';
type AuditChange = { field: string; before: string; after: string };

function hasExpiredSession(): boolean {
  return typeof sessionStorage !== 'undefined' && sessionStorage.getItem('gym.session-expired') === '1';
}

@Component({
  selector: 'app-root',
  imports: [FormsModule, CommonModule],
  templateUrl: './dashboard.html',
  styleUrl: './app.scss'
})
export class App {
  readonly Math = Math;
  private readonly businessTimeZone = 'Asia/Kolkata';
  private readonly currencyFormatter = new Intl.NumberFormat('en-IN', {
    style: 'currency', currency: 'INR', maximumFractionDigits: 0
  });
  private readonly preciseCurrencyFormatter = new Intl.NumberFormat('en-IN', {
    style: 'currency', currency: 'INR', minimumFractionDigits: 2, maximumFractionDigits: 2
  });
  private readonly dateFormatter = new Intl.DateTimeFormat('en-IN', {
    day: 'numeric', month: 'short', year: 'numeric', timeZone: 'UTC'
  });
  private readonly businessDateFormatter = new Intl.DateTimeFormat('en-CA', {
    year: 'numeric', month: '2-digit', day: '2-digit', timeZone: 'Asia/Kolkata'
  });
  readonly user = signal<User | null>(this.readStoredUser());
  readonly view = signal<View>('overview');
  readonly authMode = signal<AuthMode>(hasExpiredSession() ? 'login' : 'home');
  readonly resetStep = signal<'request' | 'confirm'>('request');
  readonly developmentResetCode = signal('');
  readonly plans = signal<Plan[]>([]);
  readonly branches = signal<Branch[]>([]);
  readonly staff = signal<User[]>([]);
  readonly memberships = signal<Membership[]>([]);
  readonly overviewRecentMemberships = signal<Membership[]>([]);
  readonly membershipGroups = signal<MembershipGroup[]>([]);
  readonly expandedMemberIds = signal<number[]>([]);
  readonly accessResults = signal<Record<number, MembershipAccess>>({});
  readonly membershipPageIndex = signal(0);
  readonly membershipTotal = signal(0);
  readonly membershipTotalPages = signal(0);
  readonly membershipPageSize = 25;
  readonly dashboard = signal<Dashboard | null>(null);
  readonly actionHistory = signal<ActionHistory[]>([]);
  readonly renewalReminders = signal<RenewalReminder[]>([]);
  readonly analyticsData = signal<Analytics | null>(null);
  readonly analyticsPeriod = signal<'month' | 'quarter' | 'six-months' | 'twelve-months' | 'custom'>('quarter');
  readonly analyticsTab = signal<'executive' | 'pt' | 'packages' | 'retention' | 'tax'>('executive');
  readonly exportMenuOpen = signal(false);
  readonly planTypeFilter = signal<'ALL' | 'STANDARD' | 'COMBINED' | 'PT_ONLY'>('ALL');
  readonly staffSearchTerm = signal('');
  readonly branchFilter = signal<'ALL' | 'COMPANY_OWNED' | 'FRANCHISE' | 'ACTIVE'>('ALL');
  readonly quickAccessMember = signal<Membership | null>(null);
  quickAccessQuery = '';
  readonly cancellationTarget = signal<Membership | null>(null);
  readonly cancellationMode = signal<'term' | 'renewal'>('term');
  readonly adjustmentTarget = signal<Membership | null>(null);
  readonly adjustmentFreezeTarget = signal<Freeze | null>(null);
  readonly adjustmentMode = signal<AdjustmentMode | null>(null);
  readonly planFilter = signal<PlanFilter>('active');
  readonly dashboardBranchId = signal<number | null>(null);
  readonly editingPlan = signal(false);
  readonly editingBranch = signal(false);
  readonly memberFilter = signal<MemberFilter>('all');
  readonly busy = signal(false);
  readonly error = signal('');
  readonly notice = signal(hasExpiredSession() ? 'Your session expired. Please sign in again.' : '');
  readonly refreshedAt = signal(new Date());
  readonly historyTab = signal<'audit' | 'email'>('audit');
  readonly auditFilter = signal<'ALL' | 'MEMBERSHIP' | 'PACKAGE' | 'BRANCH' | 'STAFF' | 'PT'>('ALL');
  readonly auditSearchTerm = signal('');
  readonly reminderStatusFilter = signal<'ALL' | 'SENT' | 'PENDING' | 'FAILED'>('ALL');
  readonly reminderSearchTerm = signal('');

  // ─── PT Signals ───────────────────────────────────────────────────────────
  readonly personalTrainers = signal<PtProfile[]>([]);
  readonly ptSessions = signal<PtSession[]>([]);
  readonly ptEarnings = signal<PtEarning[]>([]);
  readonly ptPayouts = signal<PtPayout[]>([]);
  readonly ptAnalyticsData = signal<PtAnalytics | null>(null);
  readonly myPtProfile = signal<PtProfile | null>(null);
  readonly ptTrainees = signal<Membership[]>([]);
  readonly bookingPtSession = signal<Membership | null>(null);
  bookingSessionDate = '';
  bookingSessionStartTime = '10:00';
  bookingSessionEndTime = '11:00';
  bookingSessionNotes = '';
  readonly editingPt = signal(false);
  readonly ptPayoutModalOpen = signal(false);
  readonly ptSessionStatusFilter = signal<string>('all');
  readonly ptSearchTerm = signal('');
  readonly ptStatusFilter = signal<'ALL' | 'ACTIVE' | 'INACTIVE'>('ALL');
  readonly ptBranchFilter = signal<string>('ALL');
  readonly ptPayoutFilter = signal<'ALL' | 'PENDING' | 'PAID'>('ALL');
  readonly ptSessionDateFilter = signal<'ALL' | 'TODAY' | 'UPCOMING'>('ALL');
  readonly ptEarningFilter = signal<'ALL' | 'PENDING' | 'SETTLED'>('ALL');
  readonly ptSessionsModalOpen = signal(false);
  readonly selectedPtForSessions = signal<PtProfile | null>(null);
  readonly ptSessionsModalList = signal<PtSession[]>([]);
  readonly ptSessionsModalLoading = signal(false);
  readonly ptSessionsModalFilter = signal<'ALL' | 'BOOKED' | 'COMPLETED' | 'CANCELLED' | 'MISSED'>('ALL');
  readonly filteredPtSessionsModal = computed(() => {
    const filter = this.ptSessionsModalFilter();
    const list = this.ptSessionsModalList();
    if (filter === 'ALL') return list;
    return list.filter((s) => s.status === filter);
  });

  // ─── Subscription & Tier Signals ──────────────────────────────────────────
  readonly isPremium = computed(() => this.user()?.subscriptionTier === 'PREMIUM');
  readonly hasPtAccess = computed(() => this.isPremium());
  readonly hasHistoryAccess = computed(() => this.isPremium());
  readonly upgradeModalOpen = signal(false);
  readonly upgradeFeatureTarget = signal<'pt' | 'history' | 'general'>('general');
  readonly upgradeLoading = signal(false);
  readonly upgradeModalTab = signal<'request' | 'redeem'>('request');
  readonly upgradeBillingPlan = signal<'ANNUAL' | 'HALF_YEAR' | 'MONTHLY'>('ANNUAL');
  readonly upgradePaymentMethod = signal<'BANK_WIRE' | 'UPI_QR' | 'CORPORATE_INVOICE'>('BANK_WIRE');
  readonly upgradeGstNumber = signal('');
  readonly upgradeBillingAddress = signal('');
  readonly upgradeNotes = signal('');
  readonly submittingUpgradeRequest = signal(false);
  readonly myUpgradeRequests = signal<UpgradeRequest[]>([]);
  readonly myUpgradeRequestsLoading = signal(false);
  readonly pendingMyUpgradeRequest = computed(() => {
    return this.myUpgradeRequests().find(r => r.status === 'PENDING') || null;
  });

  readonly copiedSettlementField = signal<string>('');

  readonly quotePlanAmount = computed(() => {
    switch (this.upgradeBillingPlan()) {
      case 'ANNUAL': return 24000;
      case 'HALF_YEAR': return 14000;
      case 'MONTHLY': return 2999;
    }
  });

  readonly quotePlanDurationDays = computed(() => {
    switch (this.upgradeBillingPlan()) {
      case 'ANNUAL': return 365;
      case 'HALF_YEAR': return 180;
      case 'MONTHLY': return 30;
    }
  });

  readonly quotePlanMonthlyEquivalent = computed(() => {
    switch (this.upgradeBillingPlan()) {
      case 'ANNUAL': return '₹2,000 / mo';
      case 'HALF_YEAR': return '₹2,333 / mo';
      case 'MONTHLY': return '₹2,999 / mo';
    }
  });

  readonly quotePlanEstimatedExpiry = computed(() => {
    const days = this.quotePlanDurationDays();
    const d = new Date();
    d.setDate(d.getDate() + days);
    return d.toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' });
  });

  readonly isSuperAdmin = computed(() => this.user()?.role === 'SUPER_ADMIN');
  readonly redeemModalOpen = signal(false);
  readonly redeemCodeInput = signal('');
  readonly redeemingCode = signal(false);
  readonly cancelModalOpen = signal(false);
  readonly cancelImmediate = signal(false);
  readonly cancelReason = signal('');
  readonly cancellingSubscription = signal(false);

  // ─── Super-Admin Platform Signals ─────────────────────────────────────────
  readonly platformTab = signal<'metrics' | 'organizations' | 'codes' | 'upgrade-requests'>('metrics');
  readonly platformMetrics = signal<PlatformMetrics | null>(null);
  readonly platformOrganizations = signal<PlatformOrganization[]>([]);
  readonly platformOrgSearch = signal('');
  readonly platformCodes = signal<ActivationCode[]>([]);
  readonly platformUpgradeRequests = signal<UpgradeRequest[]>([]);
  readonly platformUpgradeRequestFilter = signal<string>('ALL');
  readonly platformUpgradeViewMode = signal<'cards' | 'table'>('cards');
  readonly platformUpgradeSearch = signal<string>('');
  readonly copiedPlatformKey = signal<string>('');
  readonly platformUpgradeLoading = signal(false);
  readonly issuingKeyId = signal<number | null>(null);
  readonly directUpgradingId = signal<number | null>(null);
  readonly rejectingRequestId = signal<number | null>(null);
  readonly rejectReasonInput = signal('');
  readonly rejectingLoading = signal(false);

  readonly filteredPlatformUpgradeRequests = computed(() => {
    const filter = this.platformUpgradeRequestFilter();
    const query = this.platformUpgradeSearch().trim().toLowerCase();
    let list = this.platformUpgradeRequests();
    if (filter !== 'ALL') {
      list = list.filter((r) => r.status === filter);
    }
    if (query) {
      list = list.filter((r) =>
        r.organizationName?.toLowerCase().includes(query) ||
        r.organizationCode?.toLowerCase().includes(query) ||
        r.requesterEmail?.toLowerCase().includes(query) ||
        r.requesterName?.toLowerCase().includes(query) ||
        ('req-' + r.id).toLowerCase().includes(query) ||
        r.billingPlanName?.toLowerCase().includes(query) ||
        r.paymentMethod?.toLowerCase().includes(query) ||
        (r.issuedActivationCode && r.issuedActivationCode.toLowerCase().includes(query))
      );
    }
    return list;
  });

  readonly pendingUpgradeRequestsCount = computed(() => {
    return this.platformUpgradeRequests().filter((r) => r.status === 'PENDING').length;
  });

  readonly generateCodeModalOpen = signal(false);
  generateCodeDraft = {
    tier: 'PREMIUM' as 'PREMIUM' | 'STANDARD',
    durationDays: 365 as number | null,
    notes: ''
  };
  readonly generatingCode = signal(false);
  readonly copiedCode = signal<string | null>(null);

  registerGymDraft = {
    fullName: '',
    email: '',
    phoneNumber: '',
    password: '',
    organizationName: '',
    organizationCode: '',
    branchName: '',
    branchAddress: '',
    subscriptionTier: 'STANDARD' as 'STANDARD' | 'PREMIUM'
  };

  reportFrom = this.addDays(this.todayDate(), -30);
  reportTo = this.todayDate();
  reportBranchId = '';
  adjustmentDraft = { startDate: '', endDate: '', branchId: '', planId: '', listPrice: 0, discountPercent: 0, amountPaid: 0, reason: '' };
  cancellationDraft: { reason: CancellationReason; remarks: string; cancellationDate: string; otherExplanation: string } =
    { reason: 'NO_LONGER_INTERESTED', remarks: '', cancellationDate: this.todayDate(), otherExplanation: '' };
  searchTerm = '';
  private membershipSearchTimer: ReturnType<typeof setTimeout> | null = null;
  private readonly auditDiffCache = new WeakMap<ActionHistory, AuditChange[]>();
  loginEmail = '';
  loginPassword = '';
  planDraft = { id: 0, name: '', description: '', price: 49, durationMode: 'months' as 'months' | 'days', durationMonths: 1, durationDays: 30, branchId: '', active: true, ptPackageType: 'NONE' as PtPackageType, includedPtSessions: 0, ptFeeIncluded: 0 };
  branchDraft = { id: 0, name: '', code: '', type: 'COMPANY_OWNED' as BranchType, address: '', phoneNumber: '', active: true };
  readonly renewalTarget = signal<Membership | null>(null);
  renewalDraft = { planId: '', branchId: '', personalTrainerId: '', discountPercent: 0, startDate: '' };
  staffDraft = { fullName: '', email: '', phoneNumber: '', password: '', branchId: '' };
  memberDraft = { fullName: '', email: '', phoneNumber: '', password: '', planId: '', branchId: '', personalTrainerId: '', discountPercent: 0, startDate: this.todayDate() };
  ptDraft = { id: 0, fullName: '', email: '', phoneNumber: '', password: '', branchId: '', specializations: '', bio: '', sessionFeePerHour: 500, commissionPercent: 70 };
  ptPayoutDraft = { ptId: 0, ptName: '', periodFrom: this.addDays(this.todayDate(), -30), periodTo: this.todayDate(), notes: '' };
  receiptFile: File | null = null;
  ownerDraft = { fullName: '', email: '', phoneNumber: '', password: '', setupKey: '' };
  resetDraft = { email: '', code: '', newPassword: '' };
  profileDraft = { fullName: '', email: '', phoneNumber: '' };
  passwordDraft = { currentPassword: '', newPassword: '' };

  readonly staffExpiringMembersList = signal<Membership[]>([]);
  readonly staffRecentMembersList = signal<Membership[]>([]);

  readonly activePlans = computed(() => this.plans().filter((plan) => plan.active));
  readonly viewTitle = computed(() => ({
    overview: this.user()?.role === 'STAFF' ? 'Front Desk Hub' : 'Overview',
    members: 'Members',
    packages: 'Packages',
    register: 'New membership',
    team: 'Team',
    account: 'My account',
    branches: 'Branches',
    reports: 'Analytics',
    history: 'Action history',
    'pt-management': 'Trainer Management',
    'pt-dashboard': 'Trainer Dashboard',
    'pt-earnings': 'My Earnings & Payouts',
    'platform-admin': 'SaaS Platform Command Center'
  })[this.view()]);
  readonly dashboardMetrics = computed(() => {
    const dashboard = this.dashboard();
    const branchId = this.dashboardBranchId();
    return !dashboard || branchId === null
      ? dashboard
      : dashboard.branches.find((branch) => branch.branchId === branchId) ?? dashboard;
  });

  readonly activeMembersCount = computed(() => {
    const dashboard = this.dashboardMetrics();
    if (dashboard && dashboard.activeMembers !== undefined && dashboard.activeMembers > 0) {
      return dashboard.activeMembers;
    }
    const mems = this.memberships();
    if (mems.length > 0) {
      return mems.filter((m) => m.status === 'ACTIVE' && !this.isExpired(m.endDate)).length;
    }
    return dashboard?.activeMembers ?? 0;
  });

  readonly expiringMembersCount = computed(() => {
    const dashboard = this.dashboardMetrics();
    if (dashboard && dashboard.expiringWithinSevenDays !== undefined && dashboard.expiringWithinSevenDays > 0) {
      return dashboard.expiringWithinSevenDays;
    }
    if (this.staffExpiringMembersList().length > 0) {
      return this.staffExpiringMembersList().length;
    }
    const mems = this.memberships();
    if (mems.length > 0) {
      return mems.filter((m) => this.isMemberExpiring(m)).length;
    }
    return dashboard?.expiringWithinSevenDays ?? 0;
  });

  readonly cancelledMembersCount = computed(() => {
    return this.memberships().filter((m) => m.status === 'CANCELLED').length;
  });

  readonly isAnyModalOpen = computed(() => !!(
    this.sessionCompleting() ||
    this.bookingPtSession() ||
    this.ptPayoutModalOpen() ||
    this.ptSessionsModalOpen() ||
    this.upgradeModalOpen() ||
    this.redeemModalOpen() ||
    this.cancelModalOpen() ||
    this.generateCodeModalOpen() ||
    this.rejectingRequestId() !== null ||
    this.renewalTarget() ||
    this.cancellationTarget() ||
    this.adjustmentTarget()
  ));

  @HostListener('window:keydown.escape')
  onEscapePress(): void {
    if (this.sessionCompleting()) { this.closeSessionCompletionModal(); return; }
    if (this.bookingPtSession()) { this.closeBookSessionModal(); return; }
    if (this.ptPayoutModalOpen()) { this.ptPayoutModalOpen.set(false); return; }
    if (this.ptSessionsModalOpen()) { this.closePtSessionsModal(); return; }
    if (this.upgradeModalOpen()) { this.closeUpgradeModal(); return; }
    if (this.redeemModalOpen()) { this.closeRedeemModal(); return; }
    if (this.cancelModalOpen()) { this.closeCancelModal(); return; }
    if (this.generateCodeModalOpen()) { this.closeGenerateCodeModal(); return; }
    if (this.rejectingRequestId() !== null) { this.closeRejectModal(); return; }
    if (this.renewalTarget()) { this.closeRenewal(); return; }
    if (this.cancellationTarget()) { this.closeCancellation(); return; }
    if (this.adjustmentTarget()) { this.closeAdjustment(); return; }
    if (this.exportMenuOpen()) { this.closeExportMenu(); return; }
    if (this.editingPlan()) { this.editingPlan.set(false); return; }
    if (this.editingBranch()) { this.editingBranch.set(false); return; }
    if (this.editingPt()) { this.cancelPtEdit(); return; }
  }

  copyToClipboard(text: string, label = 'Copied to clipboard!'): void {
    if (typeof navigator !== 'undefined' && navigator.clipboard) {
      void navigator.clipboard.writeText(text);
      this.notice.set(label);
      window.setTimeout(() => {
        if (this.notice() === label) this.notice.set('');
      }, 2500);
    }
  }
  readonly dashboardMembers = computed(() => {
    const branchId = this.dashboardBranchId();
    return branchId === null
      ? this.overviewRecentMemberships()
      : this.overviewRecentMemberships().filter((membership) => membership.branchId === branchId);
  });
  readonly revenueDelta = computed(() => {
    const current = this.dashboardMetrics()?.revenueThisMonth ?? 0;
    const previous = this.dashboardMetrics()?.revenueLastMonth ?? 0;
    return previous === 0 ? (current > 0 ? null : 0) : Math.round(((current - previous) / previous) * 100);
  });
  readonly revenueTrendLabel = computed(() => {
    const delta = this.revenueDelta();
    if (delta === null) return (this.dashboardMetrics()?.revenueThisMonth ?? 0) > 0 ? 'NEW' : '—';
    return `${delta >= 0 ? '↗' : '↘'} ${Math.abs(delta)}%`;
  });
  readonly revenueTrendDown = computed(() => (this.revenueDelta() ?? 0) < 0);
  readonly activeBranches = computed(() => this.branches().filter((branch) => branch.active));
  readonly filteredStaff = computed(() => {
    const term = this.staffSearchTerm().toLowerCase().trim();
    if (!term) return this.staff();
    return this.staff().filter((s) =>
      s.fullName.toLowerCase().includes(term) ||
      s.email.toLowerCase().includes(term) ||
      (s.branchName && s.branchName.toLowerCase().includes(term)) ||
      (s.phoneNumber && s.phoneNumber.toLowerCase().includes(term))
    );
  });
  readonly filteredBranches = computed(() => {
    const filter = this.branchFilter();
    const list = this.branches();
    if (filter === 'COMPANY_OWNED') return list.filter((b) => b.type === 'COMPANY_OWNED');
    if (filter === 'FRANCHISE') return list.filter((b) => b.type === 'FRANCHISE');
    if (filter === 'ACTIVE') return list.filter((b) => b.active);
    return list;
  });
  readonly totalBranchMembers = computed(() => this.branches().reduce((sum, b) => sum + (b.memberCount || 0), 0));
  readonly companyOwnedCount = computed(() => this.branches().filter((b) => b.type === 'COMPANY_OWNED').length);
  readonly franchiseCount = computed(() => this.branches().filter((b) => b.type === 'FRANCHISE').length);
  readonly filteredAuditHistory = computed(() => {
    const filter = this.auditFilter();
    const term = this.auditSearchTerm().toLowerCase().trim();
    let list = this.actionHistory();

    if (filter === 'MEMBERSHIP') {
      list = list.filter((e) => (e.entityType ?? '').toUpperCase().includes('MEMBER'));
    } else if (filter === 'PACKAGE') {
      list = list.filter((e) => {
        const type = (e.entityType ?? '').toUpperCase();
        return type.includes('PACKAGE') || type.includes('PLAN');
      });
    } else if (filter === 'BRANCH') {
      list = list.filter((e) => (e.entityType ?? '').toUpperCase().includes('BRANCH'));
    } else if (filter === 'STAFF') {
      list = list.filter((e) => {
        const type = (e.entityType ?? '').toUpperCase();
        return type === 'STAFF' || type === 'USER' || type === 'USER_ACCOUNT';
      });
    } else if (filter === 'PT') {
      list = list.filter((e) => {
        const type = (e.entityType ?? '').toUpperCase();
        const action = (e.action ?? '').toUpperCase();
        return type.includes('TRAINER') || type.includes('PT') || action.startsWith('PT_');
      });
    }

    if (term) {
      list = list.filter((e) =>
        (e.actorName ?? '').toLowerCase().includes(term) ||
        (e.actorEmail ?? '').toLowerCase().includes(term) ||
        (e.action ?? '').toLowerCase().includes(term) ||
        (e.reason ?? '').toLowerCase().includes(term) ||
        (e.entityType ?? '').toLowerCase().includes(term) ||
        String(e.entityId ?? '').includes(term)
      );
    }

    return list;
  });

  readonly auditMembershipCount = computed(() =>
    this.actionHistory().filter((e) => (e.entityType ?? '').toUpperCase().includes('MEMBER')).length
  );
  readonly auditPackageCount = computed(() =>
    this.actionHistory().filter((e) => {
      const type = (e.entityType ?? '').toUpperCase();
      return type.includes('PACKAGE') || type.includes('PLAN');
    }).length
  );
  readonly auditBranchCount = computed(() =>
    this.actionHistory().filter((e) => (e.entityType ?? '').toUpperCase().includes('BRANCH')).length
  );
  readonly auditStaffCount = computed(() =>
    this.actionHistory().filter((e) => {
      const type = (e.entityType ?? '').toUpperCase();
      return type === 'STAFF' || type === 'USER' || type === 'USER_ACCOUNT';
    }).length
  );
  readonly auditPtCount = computed(() =>
    this.actionHistory().filter((e) => {
      const type = (e.entityType ?? '').toUpperCase();
      const action = (e.action ?? '').toUpperCase();
      return type.includes('TRAINER') || type.includes('PT') || action.startsWith('PT_');
    }).length
  );

  readonly filteredRenewalReminders = computed(() => {
    const status = this.reminderStatusFilter();
    const term = this.reminderSearchTerm().toLowerCase().trim();
    let list = this.renewalReminders();

    if (status === 'SENT') {
      list = list.filter((r) => r.status === 'SENT');
    } else if (status === 'PENDING') {
      list = list.filter((r) => r.status === 'PENDING');
    } else if (status === 'FAILED') {
      list = list.filter((r) => r.status === 'FAILED' || r.status === 'SUPPRESSED');
    }

    if (term) {
      list = list.filter((r) =>
        (r.memberName ?? '').toLowerCase().includes(term) ||
        (r.recipientEmail ?? '').toLowerCase().includes(term) ||
        String(r.membershipId ?? '').includes(term)
      );
    }

    return list;
  });

  readonly sentRemindersCount = computed(() =>
    this.renewalReminders().filter((r) => r.status === 'SENT').length
  );
  readonly pendingRemindersCount = computed(() =>
    this.renewalReminders().filter((r) => r.status === 'PENDING').length
  );
  readonly failedRemindersCount = computed(() =>
    this.renewalReminders().filter((r) => r.status === 'FAILED' || r.status === 'SUPPRESSED').length
  );
  readonly dashboardLocationLabel = computed(() => {
    const selectedBranchId = this.dashboardBranchId();
    if (selectedBranchId === null) return 'All active locations';
    return this.dashboard()?.branches.find((branch) => branch.branchId === selectedBranchId)?.branchName
      ?? 'Selected location';
  });

  readonly filteredPersonalTrainers = computed(() => {
    const term = this.ptSearchTerm().toLowerCase().trim();
    const status = this.ptStatusFilter();
    const branch = this.ptBranchFilter();

    return this.personalTrainers().filter((pt) => {
      if (status === 'ACTIVE' && !pt.active) return false;
      if (status === 'INACTIVE' && pt.active) return false;
      if (branch !== 'ALL' && String(pt.branchId ?? '') !== branch) return false;
      if (term) {
        const matchesName = pt.fullName.toLowerCase().includes(term);
        const matchesEmail = pt.email.toLowerCase().includes(term);
        const matchesPhone = (pt.phoneNumber ?? '').toLowerCase().includes(term);
        const matchesSpec = (pt.specializations ?? '').toLowerCase().includes(term);
        const matchesBranch = (pt.branchName ?? '').toLowerCase().includes(term);
        return matchesName || matchesEmail || matchesPhone || matchesSpec || matchesBranch;
      }
      return true;
    });
  });

  readonly activePtCount = computed(() => this.personalTrainers().filter((p) => p.active).length);
  readonly inactivePtCount = computed(() => this.personalTrainers().filter((p) => !p.active).length);
  readonly totalPtClients = computed(() => this.personalTrainers().reduce((sum, p) => sum + (p.activeMembers || 0), 0));

  readonly filteredPtPayouts = computed(() => {
    const filter = this.ptPayoutFilter();
    const list = this.ptPayouts();
    if (filter === 'PENDING') return list.filter((p) => p.status === 'PENDING');
    if (filter === 'PAID') return list.filter((p) => p.status === 'PAID');
    return list;
  });

  readonly totalPayoutsPending = computed(() =>
    this.ptPayouts().filter((p) => p.status === 'PENDING').reduce((sum, p) => sum + p.totalEarnings, 0)
  );

  readonly totalPayoutsSettled = computed(() =>
    this.ptPayouts().filter((p) => p.status === 'PAID').reduce((sum, p) => sum + p.totalEarnings, 0)
  );

  readonly filteredPtSessionsWithDate = computed(() => {
    const status = this.ptSessionStatusFilter();
    const dateFilter = this.ptSessionDateFilter();
    const today = this.todayDate();
    let list = this.ptSessions();

    if (status !== 'all') {
      list = list.filter((s) => s.status === status);
    }
    if (dateFilter === 'TODAY') {
      list = list.filter((s) => s.scheduledDate === today);
    } else if (dateFilter === 'UPCOMING') {
      list = list.filter((s) => s.scheduledDate >= today && (s.status === 'BOOKED' || s.status === 'TENTATIVE'));
    }
    return list;
  });

  readonly filteredPtEarnings = computed(() => {
    const filter = this.ptEarningFilter();
    const list = this.ptEarnings();
    if (filter === 'PENDING') return list.filter((e) => !e.payoutId);
    if (filter === 'SETTLED') return list.filter((e) => !!e.payoutId);
    return list;
  });

  constructor(private readonly api: GymApiService, private readonly csrfTokens: CsrfTokenService) {
    if (hasExpiredSession()) sessionStorage.removeItem('gym.session-expired');
    effect(() => {
      const open = this.isAnyModalOpen();
      if (typeof document !== 'undefined') {
        if (open) {
          document.body.classList.add('modal-open');
        } else {
          document.body.classList.remove('modal-open');
        }
      }
    });
    if (this.user()) {
      if (this.user()?.role === 'SUPER_ADMIN') {
        this.view.set('platform-admin');
        void this.loadPlatformData();
      } else {
        void this.loadData();
      }
    }
    if (typeof window !== 'undefined' && window.location.hash) {
      this.checkUrlHash();
    }
  }

  checkUrlHash(): void {
    if (typeof window === 'undefined') return;
    const hash = window.location.hash;
    if (hash.startsWith('#redeem')) {
      const queryIdx = hash.indexOf('?');
      if (queryIdx !== -1) {
        const params = new URLSearchParams(hash.substring(queryIdx + 1));
        const code = params.get('code');
        if (code) {
          this.redeemCodeInput.set(code.trim().toUpperCase());
          this.upgradeModalTab.set('redeem');
          this.upgradeModalOpen.set(true);
        }
      }
    }
  }

  async login(): Promise<void> {
    this.error.set('');
    this.notice.set('');
    this.busy.set(true);
    try {
      const session = await firstValueFrom(this.api.login(this.loginEmail.trim(), this.loginPassword));
      this.api.storeSession(session.token, session.user);
      this.user.set(session.user);
      if (session.user.role === 'SUPER_ADMIN') {
        this.view.set('platform-admin');
        await this.loadPlatformData();
      } else {
        this.view.set(session.user.role === 'PERSONAL_TRAINER' ? 'pt-dashboard' : session.user.role === 'OWNER' ? 'overview' : 'members');
        await this.loadData();
      }
    } catch (error) {
      this.error.set(this.api.message(error, 'Sign in failed. Check your email and password.'));
    } finally {
      this.busy.set(false);
    }
  }

  showLogin(): void {
    this.error.set('');
    this.notice.set('');
    this.authMode.set('login');
  }

  async setupOwner(): Promise<void> {
    this.error.set('');
    this.busy.set(true);
    try {
      const session = await firstValueFrom(this.api.setupOwner(this.ownerDraft));
      this.startSession(session);
    } catch (error) {
      this.error.set(this.api.message(error, 'Owner setup failed. Check the bootstrap key and account details.'));
    } finally {
      this.busy.set(false);
    }
  }

  async registerGym(): Promise<void> {
    this.error.set('');
    this.busy.set(true);
    try {
      const session = await firstValueFrom(this.api.registerGym({
        fullName: this.registerGymDraft.fullName.trim(),
        email: this.registerGymDraft.email.trim(),
        phoneNumber: this.registerGymDraft.phoneNumber.trim(),
        password: this.registerGymDraft.password,
        organizationName: this.registerGymDraft.organizationName.trim(),
        organizationCode: this.registerGymDraft.organizationCode.trim().toUpperCase(),
        branchName: this.registerGymDraft.branchName.trim(),
        branchAddress: this.registerGymDraft.branchAddress.trim(),
        subscriptionTier: this.registerGymDraft.subscriptionTier
      }));
      this.startSession(session);
    } catch (error) {
      this.error.set(this.api.message(error, 'Gym registration failed. Please check your inputs.'));
    } finally {
      this.busy.set(false);
    }
  }

  async requestPasswordReset(): Promise<void> {
    this.error.set('');
    this.busy.set(true);
    try {
      const response = await firstValueFrom(this.api.requestPasswordReset(this.resetDraft.email.trim()));
      this.developmentResetCode.set(response.developmentCode ?? '');
      this.resetStep.set('confirm');
      this.notice.set(response.message);
    } catch (error) {
      this.error.set(this.api.message(error, 'Could not request a reset code.'));
    } finally {
      this.busy.set(false);
    }
  }

  async confirmPasswordReset(): Promise<void> {
    this.error.set('');
    this.busy.set(true);
    try {
      await firstValueFrom(this.api.confirmPasswordReset({ ...this.resetDraft, email: this.resetDraft.email.trim() }));
      this.authMode.set('login');
      this.loginEmail = this.resetDraft.email.trim();
      this.loginPassword = '';
      this.resetDraft = { email: '', code: '', newPassword: '' };
      this.developmentResetCode.set('');
      this.notice.set('Password updated. Sign in with your new password.');
    } catch (error) {
      this.error.set(this.api.message(error, 'The code is invalid or expired. Request a new one.'));
    } finally {
      this.busy.set(false);
    }
  }

  private startSession(session: { token: string; user: User }): void {
    this.api.storeSession(session.token, session.user);
    this.user.set(session.user);
    if (session.user.role === 'SUPER_ADMIN') {
      this.view.set('platform-admin');
      void this.loadPlatformData();
    } else {
      this.view.set(session.user.role === 'PERSONAL_TRAINER' ? 'pt-dashboard' : session.user.role === 'OWNER' ? 'overview' : 'members');
      void this.loadData();
    }
  }

  logout(): void {
    this.csrfTokens.clear();
    const clearState = () => {
      this.api.clearSession();
      this.user.set(null);
      this.authMode.set('home');
      this.dashboard.set(null);
      this.memberships.set([]);
      this.membershipGroups.set([]);
      this.membershipTotal.set(0);
      this.membershipTotalPages.set(0);
      this.plans.set([]);
      this.staff.set([]);
      this.branches.set([]);
      this.personalTrainers.set([]);
      this.ptSessions.set([]);
      this.ptEarnings.set([]);
      this.ptPayouts.set([]);
      this.ptAnalyticsData.set(null);
      this.myPtProfile.set(null);
      this.ptTrainees.set([]);
      this.bookingPtSession.set(null);
      this.view.set('overview');
      this.error.set('');
      this.notice.set('');
    };
    clearState();
    this.api.logout().subscribe({
      error: () => this.api.clearSession()
    });
  }

  openUpgradeModal(target: 'pt' | 'history' | 'general' = 'general'): void {
    this.upgradeFeatureTarget.set(target);
    this.upgradeModalTab.set('request');
    this.upgradeModalOpen.set(true);
    void this.loadMyUpgradeRequests();
  }

  closeUpgradeModal(): void {
    this.upgradeModalOpen.set(false);
  }

  setUpgradeModalTab(tab: 'request' | 'redeem'): void {
    this.upgradeModalTab.set(tab);
    this.error.set('');
  }

  setUpgradeBillingPlan(plan: 'ANNUAL' | 'HALF_YEAR' | 'MONTHLY'): void {
    this.upgradeBillingPlan.set(plan);
  }

  setUpgradePaymentMethod(method: 'BANK_WIRE' | 'UPI_QR' | 'CORPORATE_INVOICE'): void {
    this.upgradePaymentMethod.set(method);
  }

  async loadMyUpgradeRequests(): Promise<void> {
    if (this.user()?.role !== 'OWNER') return;
    this.myUpgradeRequestsLoading.set(true);
    try {
      const list = await firstValueFrom(this.api.myUpgradeRequests());
      this.myUpgradeRequests.set(list);
    } catch {
      // ignore
    } finally {
      this.myUpgradeRequestsLoading.set(false);
    }
  }

  async submitUpgradeRequest(): Promise<void> {
    if (this.user()?.role !== 'OWNER') {
      this.error.set('Only gym owners are authorized to submit upgrade requests.');
      return;
    }
    this.submittingUpgradeRequest.set(true);
    this.error.set('');
    try {
      const planName = this.upgradeBillingPlan() === 'ANNUAL'
        ? 'Annual PRO (365 Days)'
        : this.upgradeBillingPlan() === 'HALF_YEAR'
        ? 'Half-Year PRO (180 Days)'
        : 'Monthly PRO (30 Days)';
      const durationDays = this.upgradeBillingPlan() === 'ANNUAL'
        ? 365
        : this.upgradeBillingPlan() === 'HALF_YEAR'
        ? 180
        : 30;

      await firstValueFrom(this.api.requestUpgrade({
        billingPlan: planName,
        durationDays,
        paymentMethod: this.upgradePaymentMethod(),
        gstNumber: this.upgradeGstNumber().trim() || undefined,
        billingAddress: this.upgradeBillingAddress().trim() || undefined,
        notes: this.upgradeNotes().trim() || undefined
      }));

      await this.loadMyUpgradeRequests();
      this.notice.set('Your PRO upgrade request has been submitted! Our accounts team will review payment and issue your activation key.');
    } catch (err) {
      this.error.set(this.api.message(err, 'Failed to submit upgrade request.'));
    } finally {
      this.submittingUpgradeRequest.set(false);
    }
  }

  async upgradeToPremium(): Promise<void> {
    this.openUpgradeModal(this.upgradeFeatureTarget() || 'general');
  }

  // ─── SaaS Subscription & Activation Code Methods ──────────────────────────
  openRedeemModal(): void {
    this.redeemCodeInput.set('');
    this.error.set('');
    this.upgradeModalTab.set('redeem');
    this.upgradeModalOpen.set(true);
    this.redeemModalOpen.set(true);
  }

  closeRedeemModal(): void {
    this.redeemModalOpen.set(false);
    this.upgradeModalOpen.set(false);
  }

  copySettlementText(field: string, text: string): void {
    if (typeof navigator !== 'undefined' && navigator.clipboard) {
      void navigator.clipboard.writeText(text);
    }
    this.copiedSettlementField.set(field);
    setTimeout(() => {
      if (this.copiedSettlementField() === field) {
        this.copiedSettlementField.set('');
      }
    }, 2000);
  }

  async pasteRedeemCode(): Promise<void> {
    if (typeof navigator !== 'undefined' && navigator.clipboard && navigator.clipboard.readText) {
      try {
        const text = await navigator.clipboard.readText();
        if (text) {
          this.redeemCodeInput.set(text.trim().toUpperCase());
        }
      } catch {
        // clipboard access restricted
      }
    }
  }

  async submitRedeemCode(): Promise<void> {
    const code = this.redeemCodeInput().trim();
    if (!code) return;
    this.redeemingCode.set(true);
    this.error.set('');
    try {
      const sub = await firstValueFrom(this.api.redeemActivationCode(code));
      const u = this.user();
      if (u) {
        const updated: User = {
          ...u,
          subscriptionTier: sub.subscriptionTier,
          enabledFeatures: sub.enabledFeatures
        };
        this.user.set(updated);
        try { sessionStorage.setItem('gym.user', JSON.stringify(updated)); } catch {}
      }
      this.redeemModalOpen.set(false);
      this.upgradeModalOpen.set(false);
      this.notice.set(`Success! Activation code redeemed. Your gym is now on ${sub.subscriptionTier} tier.`);
      if (this.upgradeFeatureTarget() === 'pt') {
        this.navigate('pt-management');
      } else if (this.upgradeFeatureTarget() === 'history') {
        this.navigate('history');
      }
    } catch (err) {
      this.error.set(this.api.message(err, 'Failed to redeem activation code.'));
    } finally {
      this.redeemingCode.set(false);
    }
  }

  openCancelModal(): void {
    this.cancelImmediate.set(false);
    this.cancelReason.set('');
    this.error.set('');
    this.cancelModalOpen.set(true);
  }

  closeCancelModal(): void {
    this.cancelModalOpen.set(false);
  }

  async submitCancelSubscription(): Promise<void> {
    this.cancellingSubscription.set(true);
    this.error.set('');
    try {
      const sub = await firstValueFrom(this.api.cancelSubscription(this.cancelImmediate(), this.cancelReason()));
      const u = this.user();
      if (u) {
        const updated: User = {
          ...u,
          subscriptionTier: sub.subscriptionTier,
          enabledFeatures: sub.enabledFeatures
        };
        this.user.set(updated);
        try { sessionStorage.setItem('gym.user', JSON.stringify(updated)); } catch {}
      }
      this.cancelModalOpen.set(false);
      if (this.cancelImmediate()) {
        this.notice.set('Your subscription has been downgraded to Standard immediately.');
        this.navigate('account');
      } else {
        this.notice.set('Cancellation scheduled. You will continue to have Premium access until your current billing period ends.');
      }
    } catch (err) {
      this.error.set(this.api.message(err, 'Failed to cancel subscription.'));
    } finally {
      this.cancellingSubscription.set(false);
    }
  }

  async reactivateSubscription(): Promise<void> {
    this.busy.set(true);
    this.error.set('');
    try {
      const sub = await firstValueFrom(this.api.reactivateSubscription());
      const u = this.user();
      if (u) {
        const updated: User = {
          ...u,
          subscriptionTier: sub.subscriptionTier,
          enabledFeatures: sub.enabledFeatures
        };
        this.user.set(updated);
        try { sessionStorage.setItem('gym.user', JSON.stringify(updated)); } catch {}
      }
      this.notice.set('Subscription reactivated! Premium PRO access continues seamlessly.');
    } catch (err) {
      this.error.set(this.api.message(err, 'Failed to reactivate subscription.'));
    } finally {
      this.busy.set(false);
    }
  }

  // ─── Super-Admin Platform Operations ──────────────────────────────────────
  async loadPlatformData(): Promise<void> {
    this.busy.set(true);
    this.error.set('');
    try {
      const [metrics, orgs, codes, upgradeRequests] = await Promise.all([
        firstValueFrom(this.api.platformMetrics()),
        firstValueFrom(this.api.platformOrganizations(this.platformOrgSearch())),
        firstValueFrom(this.api.platformActivationCodes()),
        firstValueFrom(this.api.platformUpgradeRequests())
      ]);
      this.platformMetrics.set(metrics);
      this.platformOrganizations.set(orgs);
      this.platformCodes.set(codes);
      this.platformUpgradeRequests.set(upgradeRequests);
    } catch (err) {
      this.error.set(this.api.message(err, 'Could not load platform administration data.'));
    } finally {
      this.busy.set(false);
    }
  }

  setPlatformUpgradeFilter(filter: string): void {
    this.platformUpgradeRequestFilter.set(filter);
  }

  setPlatformUpgradeViewMode(mode: 'cards' | 'table'): void {
    this.platformUpgradeViewMode.set(mode);
  }

  copyPlatformKey(key: string): void {
    if (typeof navigator !== 'undefined' && navigator.clipboard) {
      void navigator.clipboard.writeText(key);
    }
    this.copiedPlatformKey.set(key);
    setTimeout(() => {
      if (this.copiedPlatformKey() === key) {
        this.copiedPlatformKey.set('');
      }
    }, 2000);
  }

  async approveRequestWithKey(req: UpgradeRequest): Promise<void> {
    this.issuingKeyId.set(req.id);
    this.error.set('');
    try {
      const updated = await firstValueFrom(this.api.approveUpgradeWithKey(req.id));
      this.platformUpgradeRequests.update((list) => list.map((r) => r.id === req.id ? updated : r));
      this.notice.set(`Success! Cryptographic PRO activation key issued and emailed to ${req.requesterEmail}.`);
      await this.loadPlatformData();
    } catch (err) {
      this.error.set(this.api.message(err, 'Failed to issue activation key.'));
    } finally {
      this.issuingKeyId.set(null);
    }
  }

  async approveRequestDirect(req: UpgradeRequest, durationDays?: number): Promise<void> {
    this.directUpgradingId.set(req.id);
    this.error.set('');
    try {
      const updated = await firstValueFrom(this.api.approveUpgradeDirect(req.id, durationDays || req.durationDays));
      this.platformUpgradeRequests.update((list) => list.map((r) => r.id === req.id ? updated : r));
      this.notice.set(`Success! ${req.organizationName} has been directly upgraded in cloud to PREMIUM.`);
      await this.loadPlatformData();
    } catch (err) {
      this.error.set(this.api.message(err, 'Failed to directly upgrade organization.'));
    } finally {
      this.directUpgradingId.set(null);
    }
  }

  openRejectModal(req: UpgradeRequest): void {
    this.rejectingRequestId.set(req.id);
    this.rejectReasonInput.set('');
  }

  closeRejectModal(): void {
    this.rejectingRequestId.set(null);
    this.rejectReasonInput.set('');
  }

  async submitRejectUpgradeRequest(): Promise<void> {
    const id = this.rejectingRequestId();
    if (!id) return;
    const reason = this.rejectReasonInput().trim() || 'Payment unverified or request declined.';
    this.rejectingLoading.set(true);
    this.error.set('');
    try {
      const updated = await firstValueFrom(this.api.rejectUpgradeRequest(id, reason));
      this.platformUpgradeRequests.update((list) => list.map((r) => r.id === id ? updated : r));
      this.notice.set(`Upgrade request REQ-${id} has been marked as rejected.`);
      this.closeRejectModal();
      await this.loadPlatformData();
    } catch (err) {
      this.error.set(this.api.message(err, 'Failed to reject upgrade request.'));
    } finally {
      this.rejectingLoading.set(false);
    }
  }

  openGenerateCodeModal(): void {
    this.generateCodeDraft = {
      tier: 'PREMIUM',
      durationDays: 365,
      notes: ''
    };
    this.error.set('');
    this.generateCodeModalOpen.set(true);
  }

  closeGenerateCodeModal(): void {
    this.generateCodeModalOpen.set(false);
  }

  setGenerateDurationPreset(days: number | null): void {
    this.generateCodeDraft.durationDays = days;
  }

  async createLicenseKey(): Promise<void> {
    this.generatingCode.set(true);
    this.error.set('');
    try {
      const code = await firstValueFrom(this.api.generateActivationCode({
        tier: this.generateCodeDraft.tier,
        durationDays: this.generateCodeDraft.durationDays,
        notes: this.generateCodeDraft.notes
      }));
      this.generateCodeModalOpen.set(false);
      this.notice.set(`New license key generated: ${code.code}`);
      await this.loadPlatformData();
    } catch (err) {
      this.error.set(this.api.message(err, 'Failed to generate license key.'));
    } finally {
      this.generatingCode.set(false);
    }
  }

  async revokeLicenseKey(id: number): Promise<void> {
    if (!confirm('Are you sure you want to revoke this license key? It will no longer be redeemable.')) return;
    this.busy.set(true);
    this.error.set('');
    try {
      await firstValueFrom(this.api.revokeActivationCode(id));
      this.notice.set('License key revoked.');
      await this.loadPlatformData();
    } catch (err) {
      this.error.set(this.api.message(err, 'Failed to revoke key.'));
    } finally {
      this.busy.set(false);
    }
  }

  async extendOrgSubscription(id: number, days: number): Promise<void> {
    this.busy.set(true);
    this.error.set('');
    try {
      await firstValueFrom(this.api.extendPlatformOrgSubscription(id, days));
      this.notice.set(`Extended organization subscription by ${days} days.`);
      await this.loadPlatformData();
    } catch (err) {
      this.error.set(this.api.message(err, 'Failed to extend subscription.'));
    } finally {
      this.busy.set(false);
    }
  }

  async toggleOrgSuspension(org: PlatformOrganization): Promise<void> {
    const isSuspended = org.subscriptionStatus === 'SUSPENDED';
    const action = isSuspended ? 'unsuspend' : 'suspend';
    if (!confirm(`Are you sure you want to ${action} ${org.name}?`)) return;
    this.busy.set(true);
    this.error.set('');
    try {
      await firstValueFrom(this.api.suspendPlatformOrg(org.id, !isSuspended));
      this.notice.set(`Organization ${isSuspended ? 'unsuspended' : 'suspended'} successfully.`);
      await this.loadPlatformData();
    } catch (err) {
      this.error.set(this.api.message(err, 'Failed to update organization status.'));
    } finally {
      this.busy.set(false);
    }
  }

  async updateOrgTier(id: number, tier: 'STANDARD' | 'PREMIUM'): Promise<void> {
    this.busy.set(true);
    this.error.set('');
    try {
      await firstValueFrom(this.api.updatePlatformOrgSubscription(id, { tier }));
      this.notice.set(`Organization tier updated to ${tier}.`);
      await this.loadPlatformData();
    } catch (err) {
      this.error.set(this.api.message(err, 'Failed to update tier.'));
    } finally {
      this.busy.set(false);
    }
  }

  copyActivationCode(code: string): void {
    try {
      navigator.clipboard.writeText(code);
      this.copiedCode.set(code);
      setTimeout(() => this.copiedCode.set(null), 2000);
    } catch {}
  }

  isViewAllowed(view: View): boolean {
    const role = this.user()?.role;
    if (!role) return false;
    switch (role) {
      case 'SUPER_ADMIN':
        return view === 'platform-admin' || view === 'account';
      case 'OWNER':
        return ['overview', 'members', 'packages', 'register', 'team', 'account', 'branches', 'reports', 'history', 'pt-management'].includes(view);
      case 'STAFF':
        return ['overview', 'members', 'packages', 'register', 'account'].includes(view);
      case 'PERSONAL_TRAINER':
        return ['pt-dashboard', 'pt-earnings', 'members', 'packages', 'account'].includes(view);
      case 'MEMBER':
        return ['overview', 'members', 'account'].includes(view);
      default:
        return false;
    }
  }

  navigate(view: View): void {
    if (!this.isViewAllowed(view)) {
      this.error.set('Access denied: You do not have permission to view that section.');
      return;
    }
    if ((view === 'pt-management' || view === 'pt-dashboard' || view === 'pt-earnings') && !this.isPremium()) {
      this.openUpgradeModal('pt');
      return;
    }
    if (view === 'history' && !this.isPremium()) {
      this.openUpgradeModal('history');
      return;
    }
    this.error.set('');
    this.notice.set('');
    this.view.set(view);
    if (view === 'platform-admin' && this.user()?.role === 'SUPER_ADMIN') {
      void this.loadPlatformData();
    }
    if (view === 'reports' && this.user()?.role === 'OWNER') {
      if (this.analyticsPeriod() === 'custom') void this.loadReport();
      else this.selectAnalyticsPeriod(this.analyticsPeriod());
    }
    if (view === 'overview' && (this.user()?.role === 'OWNER' || this.user()?.role === 'STAFF')) void this.loadOverviewData();
    if (view === 'team' && this.user()?.role === 'OWNER') void this.loadStaffData();
    if (view === 'history' && this.user()?.role === 'OWNER') void this.loadHistoryData();
    if (view === 'pt-management' && this.user()?.role === 'OWNER') void this.loadPtManagementData();
    if (view === 'pt-dashboard' && this.user()?.role === 'PERSONAL_TRAINER') void this.loadMyPtDashboardData();
    if (view === 'pt-earnings' && this.user()?.role === 'PERSONAL_TRAINER') void this.loadMyPtEarningsData();
    if (view === 'register') { this.memberDraft = { ...this.memberDraft, fullName: '', email: '', phoneNumber: '', password: '', planId: '', branchId: this.user()?.role === 'OWNER' ? String(this.activeBranches()[0]?.id ?? '') : String(this.user()?.branchId ?? ''), personalTrainerId: '', discountPercent: 0, startDate: this.todayDate() }; this.receiptFile = null; }
    if (view === 'account' && this.user()) this.profileDraft = { fullName: this.user()!.fullName, email: this.user()!.email, phoneNumber: this.user()!.phoneNumber ?? '' };
    if (view === 'register' && this.user()?.role === 'OWNER' && !this.memberDraft.branchId) this.memberDraft.branchId = String(this.activeBranches()[0]?.id ?? '');
    if (view === 'team' && !this.staffDraft.branchId) this.staffDraft.branchId = String(this.activeBranches()[0]?.id ?? '');
  }

  selectDashboardBranch(value: string | number): void {
    this.dashboardBranchId.set(value === 'all' ? null : Number(value));
    void this.loadOverviewRecentMemberships();
  }

  visiblePlans(): Plan[] {
    return this.plans().filter((plan) => {
      const statusMatch = this.planFilter() === 'all'
        || (this.planFilter() === 'active' ? plan.active : !plan.active);
      if (!statusMatch) return false;
      if (this.planTypeFilter() === 'ALL') return true;
      if (this.planTypeFilter() === 'STANDARD') return !plan.ptPackageType || plan.ptPackageType === 'NONE';
      return plan.ptPackageType === this.planTypeFilter();
    });
  }

  toggleExportMenu(): void {
    this.exportMenuOpen.update((v) => !v);
  }

  closeExportMenu(): void {
    this.exportMenuOpen.set(false);
  }

  focusQuickAccess(): void {
    const input = document.querySelector<HTMLInputElement>('.checkin-search-box input');
    if (input) {
      input.focus();
      input.scrollIntoView({ behavior: 'smooth', block: 'center' });
    }
  }

  enrollWithPlan(plan: Plan): void {
    this.memberDraft.planId = String(plan.id);
    if (plan.branchId) this.memberDraft.branchId = String(plan.branchId);
    this.navigate('register');
  }

  monthlyEquivalent(plan: Plan): number | null {
    if (plan.durationMonths && plan.durationMonths > 1) {
      return Math.round(plan.price / plan.durationMonths);
    }
    return null;
  }

  searchMembers(): void {
    if (this.membershipSearchTimer !== null) clearTimeout(this.membershipSearchTimer);
    this.membershipSearchTimer = setTimeout(() => {
      this.membershipSearchTimer = null;
      void this.loadMembershipPage(0);
    }, 250);
  }

  selectMemberFilter(filter: MemberFilter): void {
    this.memberFilter.set(filter);
    void this.loadMembershipPage(0);
  }

  moveMembershipPage(offset: number): void {
    const page = this.membershipPageIndex() + offset;
    if (page >= 0 && page < this.membershipTotalPages()) void this.loadMembershipPage(page);
  }

  async refreshMembersDirectory(): Promise<void> {
    this.busy.set(true);
    try {
      await this.loadMembershipPage(this.membershipPageIndex());
      this.notice.set('Member directory refreshed.');
    } catch (error) {
      this.error.set(this.api.message(error, 'Could not refresh member directory.'));
    } finally {
      this.busy.set(false);
    }
  }

  openAdjustment(mode: AdjustmentMode, member: Membership, freeze: Freeze | null = null): void {
    this.error.set('');
    this.adjustmentMode.set(mode);
    this.adjustmentTarget.set(member);
    this.adjustmentFreezeTarget.set(freeze);
    const freezeDate = mode === 'freeze' ? this.nextFreezeDate(member) : this.todayDate();
    this.adjustmentDraft = {
      startDate: mode === 'freeze' ? freezeDate : member.startDate,
      endDate: mode === 'freeze' ? freezeDate : member.endDate,
      branchId: mode === 'transfer' ? '' : String(member.branchId ?? ''),
      planId: String(member.planId),
      listPrice: member.listPrice,
      discountPercent: member.discountPercent,
      amountPaid: member.amountPaid,
      reason: ''
    };
  }

  closeAdjustment(): void {
    this.adjustmentMode.set(null);
    this.adjustmentTarget.set(null);
    this.adjustmentFreezeTarget.set(null);
  }

  async submitAdjustment(): Promise<void> {
    const member = this.adjustmentTarget();
    const mode = this.adjustmentMode();
    if (!member || !mode || !this.adjustmentDraft.reason.trim()) return;
    this.busy.set(true);
    this.error.set('');
    try {
      if (mode === 'freeze') {
        await firstValueFrom(this.api.freezeMembership(member.id, {
          startDate: this.adjustmentDraft.startDate,
          endDate: this.adjustmentDraft.endDate,
          reason: this.adjustmentDraft.reason.trim()
        }));
      } else if (mode === 'cancel-freeze' || mode === 'end-freeze') {
        const freeze = this.adjustmentFreezeTarget();
        if (!freeze) return;
        const request = mode === 'cancel-freeze'
          ? this.api.cancelFreeze(member.id, freeze.id, this.adjustmentDraft.reason.trim())
          : this.api.endFreezeEarly(member.id, freeze.id, this.adjustmentDraft.reason.trim());
        await firstValueFrom(request);
      } else if (mode === 'transfer') {
        await firstValueFrom(this.api.transferMembership(member.id, {
          branchId: Number(this.adjustmentDraft.branchId),
          reason: this.adjustmentDraft.reason.trim()
        }));
      } else {
        await firstValueFrom(this.api.correctMembership(member.id, {
          planId: Number(this.adjustmentDraft.planId),
          startDate: this.adjustmentDraft.startDate,
          endDate: this.adjustmentDraft.endDate,
          listPrice: Number(this.adjustmentDraft.listPrice),
          discountPercent: Number(this.adjustmentDraft.discountPercent),
          amountPaid: Number(this.adjustmentDraft.amountPaid),
          reason: this.adjustmentDraft.reason.trim()
        }));
      }
      this.notice.set(mode === 'freeze' ? 'Membership freeze recorded and dates extended.'
        : mode === 'cancel-freeze' ? 'Scheduled freeze cancelled; unused days were removed from the extension.'
          : mode === 'end-freeze' ? 'Freeze ended early; unused days were removed from the extension.'
        : mode === 'transfer' ? 'Membership transferred; dates and payment history were preserved.'
          : 'Membership correction recorded in the audit history.');
      this.accessResults.update((results) => {
        const updated = { ...results };
        delete updated[member.id];
        return updated;
      });
      this.closeAdjustment();
      await this.refreshMembershipViews();
    } catch (error) {
      this.error.set(this.api.message(error, 'Could not save this membership adjustment.'));
    } finally {
      this.busy.set(false);
    }
  }

  async loadReport(): Promise<void> {
    this.busy.set(true);
    this.error.set('');
    try {
      const branchId = this.reportBranchId ? Number(this.reportBranchId) : null;
      const [analytics, ptAnalytics] = await Promise.all([
        firstValueFrom(this.api.analytics(this.reportFrom, this.reportTo, branchId)),
        firstValueFrom(this.api.ptAnalytics(this.reportFrom, this.reportTo)).catch(() => null)
      ]);
      this.analyticsData.set(analytics);
      if (ptAnalytics) this.ptAnalyticsData.set(ptAnalytics);
    } catch (error) {
      this.error.set(this.api.message(error, 'Could not load the selected report.'));
    } finally {
      this.busy.set(false);
    }
  }

  selectAnalyticsPeriod(period: 'month' | 'quarter' | 'six-months' | 'twelve-months' | 'custom'): void {
    this.analyticsPeriod.set(period);
    if (period === 'custom') return;
    const today = this.todayDate();
    const current = new Date(`${today}T00:00:00`);
    if (period === 'month') {
      current.setDate(1);
    } else if (period === 'quarter') {
      const quarterMonth = Math.floor(current.getMonth() / 3) * 3;
      current.setMonth(quarterMonth, 1);
    } else {
      current.setDate(1);
      current.setMonth(current.getMonth() - (period === 'six-months' ? 5 : 11));
    }
    this.reportFrom = `${current.getFullYear()}-${String(current.getMonth() + 1).padStart(2, '0')}-01`;
    this.reportTo = today;
    void this.loadReport();
  }

  checkQuickAccess(): void {
    const q = this.quickAccessQuery.trim().toLowerCase();
    if (!q) {
      this.quickAccessMember.set(null);
      return;
    }
    const match = this.memberships().find((m) =>
      m.memberName.toLowerCase().includes(q) || (m.email && m.email.toLowerCase().includes(q))
    );
    if (!match) {
      this.quickAccessMember.set(null);
      this.notice.set(`No member found matching "${this.quickAccessQuery}".`);
      return;
    }
    this.quickAccessMember.set(match);
    void this.checkMembershipAccess(match);
  }

  clearQuickAccess(): void {
    this.quickAccessQuery = '';
    this.quickAccessMember.set(null);
  }

  markCustomAnalyticsPeriod(): void {
    this.analyticsPeriod.set('custom');
  }

  chartBarHeight(value: number, maximum: number): number {
    return maximum <= 0 ? 0 : Math.max(value > 0 ? 6 : 0, Math.round((value / maximum) * 100));
  }

  maxTrendMemberships(): number {
    return Math.max(1, ...(this.analyticsData()?.trend.map((point) => point.memberships) ?? []));
  }

  maxTrendRenewalRevenue(): number {
    return Math.max(1, ...(this.analyticsData()?.trend.flatMap((point) => [
      point.newRevenue, point.renewalRevenue
    ]) ?? []));
  }

  percentLabel(value: number | null): string {
    return value === null ? 'New / no baseline' : `${value > 0 ? '+' : ''}${value}%`;
  }

  async exportReport(type: string): Promise<void> {
    try {
      const file = await firstValueFrom(this.api.exportReport(
        this.reportFrom, this.reportTo, type, this.reportBranchId ? Number(this.reportBranchId) : null));
      const url = URL.createObjectURL(file);
      const link = document.createElement('a');
      link.href = url;
      link.download = `gym-${type}-${this.reportFrom}-to-${this.reportTo}.csv`;
      link.click();
      window.setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (error) {
      this.error.set(this.api.message(error, 'Could not export this report.'));
    }
  }

  planPtSessionRate(): number {
    const draft = this.planDraft;
    if (draft.ptPackageType === 'NONE') return 0;
    const sessions = Number(draft.includedPtSessions) || 0;
    if (sessions <= 0) return 0;
    const ptTotal = Number(draft.ptFeeIncluded) > 0 ? Number(draft.ptFeeIncluded) : Number(draft.price) || 0;
    return Math.round((ptTotal / sessions) * 100) / 100;
  }

  planPtValidationError(): string {
    const draft = this.planDraft;
    if (draft.ptPackageType === 'NONE') return '';
    const price = Number(draft.price) || 0;
    const ptFee = Number(draft.ptFeeIncluded) || 0;
    const sessions = Number(draft.includedPtSessions) || 0;
    if (ptFee > price) {
      return `PT fee component (${this.money(ptFee)}) cannot exceed total package price (${this.money(price)}).`;
    }
    if (sessions > 100) {
      return 'Included PT sessions cannot exceed 100 sessions per package.';
    }
    if (sessions <= 0) {
      return 'Please specify at least 1 included PT session.';
    }
    return '';
  }

  async savePlan(): Promise<void> {
    if (this.planPtValidationError()) {
      this.error.set(this.planPtValidationError());
      return;
    }
    this.busy.set(true);
    this.error.set('');
    try {
      const request = {
        name: this.planDraft.name.trim(),
        description: this.planDraft.description.trim(),
        price: Number(this.planDraft.price),
        durationMonths: this.planDraft.durationMode === 'months' ? Number(this.planDraft.durationMonths) : null,
        durationDays: this.planDraft.durationMode === 'days' ? Number(this.planDraft.durationDays) : null,
        branchId: this.planDraft.branchId ? Number(this.planDraft.branchId) : null,
        active: this.planDraft.active,
        ptPackageType: this.planDraft.ptPackageType,
        includedPtSessions: this.planDraft.includedPtSessions ? Number(this.planDraft.includedPtSessions) : null,
        ptFeeIncluded: this.planDraft.ptFeeIncluded ? Number(this.planDraft.ptFeeIncluded) : null
      };
      if (this.planDraft.id) await firstValueFrom(this.api.updatePlan(this.planDraft.id, request));
      else await firstValueFrom(this.api.createPlan(request));
      this.editingPlan.set(false);
      this.notice.set(this.planDraft.id ? 'Package updated.' : 'Package created.');
      await this.loadPlans();
    } catch (error) {
      this.error.set(this.api.message(error, 'Could not save this package.'));
    } finally {
      this.busy.set(false);
    }
  }

  startNewPlan(): void { this.planDraft = { id: 0, name: '', description: '', price: 49, durationMode: 'months', durationMonths: 1, durationDays: 30, branchId: '', active: true, ptPackageType: 'NONE' as PtPackageType, includedPtSessions: 0, ptFeeIncluded: 0 }; this.editingPlan.set(true); }
  editPlan(plan: Plan): void { this.planDraft = { id: plan.id, name: plan.name, description: plan.description ?? '', price: plan.price, durationMode: plan.durationDays !== null ? 'days' : 'months', durationMonths: plan.durationMonths ?? 1, durationDays: plan.durationDays ?? 30, branchId: plan.branchId === null ? '' : String(plan.branchId), active: plan.active, ptPackageType: plan.ptPackageType ?? 'NONE', includedPtSessions: plan.includedPtSessions ?? 0, ptFeeIncluded: plan.ptFeeIncluded ?? 0 }; this.editingPlan.set(true); }

  async registerMember(): Promise<void> {
    if (!this.selectedPlan()) return;
    this.busy.set(true);
    this.error.set('');
    try {
      const receiptImageData = this.receiptFile ? await this.readReceiptFile(this.receiptFile) : null;
      const result = await firstValueFrom(this.api.registerMembership({
        ...this.memberDraft,
        planId: Number(this.memberDraft.planId),
        branchId: this.memberDraft.branchId ? Number(this.memberDraft.branchId) : null,
        personalTrainerId: this.memberDraft.personalTrainerId ? Number(this.memberDraft.personalTrainerId) : null,
        discountPercent: Number(this.memberDraft.discountPercent),
        receiptImageData
      }));
      const pinMsg = result.memberPin ? ` · Member Verification PIN: ${result.memberPin}` : '';
      this.notice.set(`${result.memberName} is now on the ${result.planName} package${pinMsg}.`);
      this.searchTerm = '';
      this.memberFilter.set('all');
      await this.refreshMembershipViews(0);
      this.navigate('members');
    } catch (error) {
      this.error.set(this.api.message(error, 'Could not create this membership.'));
    } finally {
      this.busy.set(false);
    }
  }

  selectReceipt(event: Event): void {
    const input = event.currentTarget as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    this.error.set('');
    if (!file) {
      this.receiptFile = null;
      return;
    }
    if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type) || file.size > 5 * 1024 * 1024) {
      this.receiptFile = null;
      input.value = '';
      this.error.set('Choose a JPEG, PNG, or WebP receipt image up to 5 MB.');
      return;
    }
    this.receiptFile = file;
  }

  removeReceipt(input: HTMLInputElement): void {
    this.receiptFile = null;
    input.value = '';
  }

  private readReceiptFile(file: File): Promise<string> {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => typeof reader.result === 'string' ? resolve(reader.result) : reject(new Error('Could not read receipt image'));
      reader.onerror = () => reject(new Error('Could not read receipt image'));
      reader.readAsDataURL(file);
    });
  }

  async cancelMembership(member: Membership): Promise<void> {
    this.error.set('');
    this.cancellationMode.set('term');
    this.cancellationDraft = {
      reason: 'NO_LONGER_INTERESTED',
      remarks: '',
      cancellationDate: this.todayDate(),
      otherExplanation: ''
    };
    this.cancellationTarget.set(member);
  }

  closeCancellation(): void {
    this.cancellationTarget.set(null);
  }

  async confirmCancellation(): Promise<void> {
    const member = this.cancellationTarget();
    const draft = this.cancellationDraft;
    if (!member || !draft.remarks.trim() || draft.remarks.trim().length < 10
        || (draft.reason === 'OTHER' && !draft.otherExplanation.trim())) return;
    this.busy.set(true);
    try {
      const payload = {
        reason: draft.reason,
        remarks: draft.remarks.trim(),
        cancellationDate: draft.cancellationDate,
        otherExplanation: draft.reason === 'OTHER' ? draft.otherExplanation.trim() : ''
      };
      if (this.cancellationMode() === 'renewal') {
        await firstValueFrom(this.api.cancelRenewal(member.id, payload));
      } else {
        await firstValueFrom(this.api.cancelMembership(member.id, payload));
      }
      this.closeCancellation();
      this.notice.set(this.cancellationMode() === 'renewal'
        ? `${member.memberName}'s scheduled renewal was cancelled. The current term was kept.`
        : `${member.memberName}'s membership was cancelled.`);
      await this.refreshMembershipViews();
    } catch (error) {
      this.error.set(this.api.message(error, 'Could not cancel this membership.'));
    } finally {
      this.busy.set(false);
    }
  }

  async cancelScheduledRenewal(member: Membership): Promise<void> {
    this.error.set('');
    this.cancellationMode.set('renewal');
    this.cancellationDraft = {
      reason: 'NO_LONGER_INTERESTED',
      remarks: '',
      cancellationDate: this.todayDate(),
      otherExplanation: ''
    };
    this.cancellationTarget.set(member);
  }

  async checkMembershipAccess(member: Membership): Promise<void> {
    try {
      const access = await firstValueFrom(this.api.membershipAccess(member.id));
      this.accessResults.update((results) => ({ ...results, [member.id]: access }));
      this.notice.set(`${member.memberName}: ${access.message}`);
    } catch (error) {
      this.error.set(this.api.message(error, 'Could not check membership access.'));
    }
  }

  openRenewal(member: Membership): void {
    if (!this.canRenewMembership(member)) return;
    this.error.set('');
    this.renewalDraft = {
      planId: String(member.planId),
      branchId: String(member.branchId ?? ''),
      personalTrainerId: member.personalTrainerId ? String(member.personalTrainerId) : '',
      discountPercent: member.discountPercent,
      startDate: this.renewalMinimumStartDate(member)
    };
    this.renewalTarget.set(member);
  }

  closeRenewal(): void { this.renewalTarget.set(null); }

  async confirmRenewal(): Promise<void> {
    const member = this.renewalTarget();
    const plan = this.selectedRenewalPlan();
    if (!member || !plan || !this.renewalDraft.startDate) return;
    this.busy.set(true);
    this.error.set('');
    try {
      await firstValueFrom(this.api.renewMembership(member.id, {
        planId: plan.id,
        branchId: this.renewalDraft.branchId ? Number(this.renewalDraft.branchId) : null,
        personalTrainerId: this.renewalDraft.personalTrainerId ? Number(this.renewalDraft.personalTrainerId) : null,
        discountPercent: Number(this.renewalDraft.discountPercent),
        startDate: this.renewalDraft.startDate
      }));
      this.closeRenewal();
      this.notice.set(`${member.memberName}'s renewal starts ${this.dateLabel(this.renewalDraft.startDate)}.`);
      await this.refreshMembershipViews();
    } catch (error) {
      this.error.set(this.api.message(error, 'Could not renew this membership.'));
    } finally {
      this.busy.set(false);
    }
  }

  async createStaff(): Promise<void> {
    this.busy.set(true);
    this.error.set('');
    try {
      const created = await firstValueFrom(this.api.createStaff({
        fullName: this.staffDraft.fullName.trim(),
        email: this.staffDraft.email.trim(),
        phoneNumber: this.staffDraft.phoneNumber.trim(),
        password: this.staffDraft.password,
        branchId: Number(this.staffDraft.branchId)
      }));
      this.staffDraft = { fullName: '', email: '', phoneNumber: '', password: '', branchId: String(this.activeBranches()[0]?.id ?? '') };
      this.notice.set(`${created.fullName} can now sign in as staff.`);
      await this.loadStaffData();
    } catch (error) {
      this.error.set(this.api.message(error, 'Could not create this staff account.'));
    } finally {
      this.busy.set(false);
    }
  }

  async deleteStaff(person: User): Promise<void> {
    if (!window.confirm(`Permanently remove ${person.fullName}'s staff login?`)) return;
    try {
      await firstValueFrom(this.api.deleteStaff(person.id));
      this.notice.set(`${person.fullName}'s staff login was removed.`);
      await this.loadStaffData();
    } catch (error) {
      this.error.set(this.api.message(error, 'Could not remove this staff account.'));
    }
  }

  startNewBranch(): void { this.branchDraft = { id: 0, name: '', code: '', type: 'COMPANY_OWNED', address: '', phoneNumber: '', active: true }; this.editingBranch.set(true); }
  editBranch(branch: Branch): void { this.branchDraft = { id: branch.id, name: branch.name, code: branch.code, type: branch.type, address: branch.address ?? '', phoneNumber: branch.phoneNumber ?? '', active: branch.active }; this.editingBranch.set(true); }
  viewBranchInOverview(branchId: number): void { this.selectDashboardBranch(branchId); this.navigate('overview'); }

  async saveBranch(): Promise<void> {
    this.busy.set(true);
    this.error.set('');
    try {
      const payload: BranchPayload = { ...this.branchDraft, name: this.branchDraft.name.trim(), code: this.branchDraft.code.trim().toUpperCase(), address: this.branchDraft.address.trim(), phoneNumber: this.branchDraft.phoneNumber.trim() };
      if (this.branchDraft.id) await firstValueFrom(this.api.updateBranch(this.branchDraft.id, payload));
      else await firstValueFrom(this.api.createBranch(payload));
      this.editingBranch.set(false);
      this.notice.set(this.branchDraft.id ? 'Branch updated.' : 'Branch created.');
      await this.refreshBranchesAndOverview();
    } catch (error) {
      this.error.set(this.api.message(error, 'Could not save this branch.'));
    } finally {
      this.busy.set(false);
    }
  }

  async saveProfile(): Promise<void> {
    this.busy.set(true);
    this.error.set('');
    try {
      const session = await firstValueFrom(this.api.updateProfile({ ...this.profileDraft, fullName: this.profileDraft.fullName.trim(), email: this.profileDraft.email.trim(), phoneNumber: this.profileDraft.phoneNumber.trim() }));
      this.api.storeSession(session.token, session.user);
      this.user.set(session.user);
      this.notice.set('Account details updated.');
    } catch (error) {
      this.error.set(this.api.message(error, 'Could not update account details.'));
    } finally {
      this.busy.set(false);
    }
  }

  async changePassword(): Promise<void> {
    this.busy.set(true);
    this.error.set('');
    try {
      const session = await firstValueFrom(this.api.changePassword(this.passwordDraft));
      this.api.storeSession(session.token, session.user);
      this.user.set(session.user);
      this.passwordDraft = { currentPassword: '', newPassword: '' };
      this.notice.set('Password changed.');
    } catch (error) {
      this.error.set(this.api.message(error, 'Could not change password.'));
    } finally {
      this.busy.set(false);
    }
  }

  selectedPlan(): Plan | null { return this.activePlans().find((plan) => String(plan.id) === String(this.memberDraft.planId)) ?? null; }
  selectedRenewalPlan(): Plan | null { return this.activePlans().find((plan) => String(plan.id) === String(this.renewalDraft.planId)) ?? null; }
  selectedPt(): PtProfile | null { return this.personalTrainers().find((pt) => String(pt.id) === String(this.memberDraft.personalTrainerId)) ?? null; }
  filteredMembers(): Membership[] {
    return this.memberships();
  }
  initials(name: string): string { return name.split(/\s+/).slice(0, 2).map((part) => part.charAt(0)).join('').toUpperCase(); }
  firstName(): string { return this.user()?.fullName.split(' ')[0] ?? 'there'; }
  money(amount: number): string { return this.currencyFormatter.format(amount); }
  moneyPrecise(amount: number): string { return this.preciseCurrencyFormatter.format(amount); }
  dateLabel(value: string | null | undefined): string {
    if (!value) return '—';
    try {
      if (value.includes('T') || value.includes(':')) {
        const parsed = new Date(value);
        if (!isNaN(parsed.getTime())) {
          return this.dateFormatter.format(parsed);
        }
      }
      const parts = value.split('-');
      if (parts.length >= 3) {
        const year = Number(parts[0]);
        const month = Number(parts[1]);
        const day = Number(parts[2].substring(0, 2));
        if (!isNaN(year) && !isNaN(month) && !isNaN(day)) {
          return this.dateFormatter.format(new Date(Date.UTC(year, month - 1, day)));
        }
      }
    } catch {
      // Graceful fallback
    }
    return value;
  }
  todayLabel(): string { return new Intl.DateTimeFormat('en-IN', { weekday: 'short', day: 'numeric', month: 'short', year: 'numeric', timeZone: this.businessTimeZone }).format(new Date()).toUpperCase(); }
  refreshedLabel(): string { return this.refreshedAt().toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit', timeZone: this.businessTimeZone }); }
  todayDate(): string { const parts = this.businessDateFormatter.formatToParts(new Date()); const part = (type: string) => parts.find((value) => value.type === type)?.value ?? ''; return `${part('year')}-${part('month')}-${part('day')}`; }
  daysUntil(value: string): number { return Math.max(0, Math.ceil((Date.parse(`${value}T00:00:00Z`) - Date.parse(`${this.todayDate()}T00:00:00Z`)) / 86400000)); }
  isExpired(value: string): boolean { return value < this.todayDate(); }
  isUpcoming(value: string): boolean { return value > this.todayDate(); }
  isExpiring(value: string): boolean { return !this.isExpired(value) && this.daysUntil(value) <= 7; }
  isMemberExpiring(member: Membership): boolean { return !member.renewalStartDate && this.isExpiring(member.endDate); }
  memberStatus(member: Membership): string {
    if (member.status === 'CANCELLED') return 'Cancelled';
    if (this.isUpcoming(member.startDate)) return 'Upcoming';
    if (this.isExpired(member.endDate)) return 'Expired';
    if (member.frozen) return 'Frozen';
    if (member.freezes.some((freeze) => this.canCancelFreeze(freeze))) return 'Freeze scheduled';
    if (member.renewalStartDate && this.isUpcoming(member.renewalStartDate)) return 'Active · renewal scheduled';
    return this.isExpiring(member.endDate) ? 'Expiring' : 'Active';
  }
  currentTerm(group: MembershipGroup): Membership {
    const today = this.todayDate();
    const activeTerms = group.terms.filter((term) => term.status === 'ACTIVE');
    return activeTerms.filter((term) => term.startDate <= today && term.endDate >= today)
      .sort((first, second) => second.startDate.localeCompare(first.startDate)
        || second.createdAt.localeCompare(first.createdAt) || second.id - first.id)[0]
      ?? activeTerms.filter((term) => term.startDate > today)
        .sort((first, second) => first.startDate.localeCompare(second.startDate)
          || second.createdAt.localeCompare(first.createdAt) || second.id - first.id)[0]
      ?? this.historyTerms(group)[0];
  }
  historyTerms(group: MembershipGroup): Membership[] {
    return [...group.terms].sort((first, second) => second.lastActivityAt.localeCompare(first.lastActivityAt)
      || second.createdAt.localeCompare(first.createdAt) || second.id - first.id);
  }
  groupState(group: MembershipGroup): string {
    const term = this.currentTerm(group);
    return term ? this.memberStatus(term) : 'No membership';
  }
  historyTermStatus(group: MembershipGroup, term: Membership): string {
    const current = this.currentTerm(group);
    if (term.status === 'ACTIVE' && current && term.id !== current.id
      && current.status === 'ACTIVE' && term.startDate < current.startDate
      && term.startDate <= this.todayDate() && term.endDate >= this.todayDate()) {
      return 'Superseded';
    }
    return this.memberStatus(term);
  }
  isMemberHistoryExpanded(memberId: number): boolean {
    return this.expandedMemberIds().includes(memberId);
  }
  toggleMemberHistory(memberId: number): void {
    this.expandedMemberIds.update((expanded) => expanded.includes(memberId)
      ? expanded.filter((id) => id !== memberId)
      : [...expanded, memberId]);
  }
  canFreezeMembership(member: Membership): boolean {
    return member.status === 'ACTIVE' && !member.renewalStartDate
      && !this.isUpcoming(member.startDate) && !this.isExpired(member.endDate)
      && this.nextFreezeDate(member) <= member.endDate;
  }
  canCancelFreeze(freeze: Freeze): boolean {
    return freeze.cancelledAt === null && freeze.endedEarlyAt === null && freeze.startDate > this.todayDate();
  }
  canEndFreezeEarly(freeze: Freeze): boolean {
    const today = this.todayDate();
    return freeze.cancelledAt === null && freeze.endedEarlyAt === null
      && freeze.startDate <= today && freeze.endDate >= today;
  }
  freezeStatus(freeze: Freeze): string {
    if (freeze.cancelledAt) return 'Cancelled before start';
    if (freeze.endedEarlyAt) return `Ended early on ${this.dateLabel(freeze.endedEarlyAt)}`;
    if (this.isUpcoming(freeze.startDate)) return 'Scheduled';
    if (this.isExpired(freeze.endDate)) return 'Completed';
    return 'In progress';
  }
  upcomingFreeze(member: Membership): Freeze | null {
    return [...member.freezes].filter((freeze) => this.canCancelFreeze(freeze))
      .sort((first, second) => first.startDate.localeCompare(second.startDate))[0] ?? null;
  }
  nextFreezeDate(member: Membership): string {
    let candidate = this.todayDate();
    for (const freeze of [...member.freezes].sort((first, second) => first.startDate.localeCompare(second.startDate))) {
      if (freeze.cancelledAt) continue;
      const effectiveEnd = freeze.endedEarlyAt
        ? this.addDays(freeze.endedEarlyAt, -1)
        : freeze.endDate;
      if (effectiveEnd < candidate) continue;
      if (freeze.startDate > candidate) break;
      candidate = this.addDays(effectiveEnd, 1);
    }
    return candidate;
  }
  hasTransferTarget(member: Membership): boolean {
    return this.activeBranches().some((branch) => branch.id !== member.branchId);
  }
  termDaysRemainingLabel(member: Membership): string {
    if (member.status === 'CANCELLED') return 'Term cancelled';
    if (this.isUpcoming(member.startDate)) return `Starts in ${this.daysUntil(member.startDate)}d`;
    if (this.isExpired(member.endDate)) return 'Term ended';
    const left = this.daysUntil(member.endDate);
    if (left === 0) return 'Ends today';
    if (left === 1) return '1 day left';
    return `${left} days left`;
  }
  ptSessionProgress(member: Membership): number {
    if (!member.ptSessionsIncluded || member.ptSessionsIncluded <= 0) return 0;
    const used = member.ptSessionsUsed ?? 0;
    return Math.min(100, Math.round((used / member.ptSessionsIncluded) * 100));
  }
  canCancelRenewal(member: Membership): boolean {
    return member.status === 'ACTIVE' && member.renewalStartDate !== null
      && this.isUpcoming(member.renewalStartDate);
  }
  canRenewMembership(member: Membership): boolean {
    if (member.renewalStartDate || (member.status === 'ACTIVE' && this.isUpcoming(member.startDate))) {
      return false;
    }
    const today = this.todayDate();
    const otherActiveTerms = this.memberships().filter((other) =>
      other.id !== member.id && other.email.toLowerCase() === member.email.toLowerCase()
      && other.status === 'ACTIVE' && other.endDate >= today);
    if (member.status === 'CANCELLED' || member.endDate < today) {
      return otherActiveTerms.length === 0;
    }
    const laterActiveTerm = otherActiveTerms.some((other) =>
      other.startDate > today || other.startDate > member.startDate
      || (other.startDate === member.startDate && other.createdAt > member.createdAt));
    return !laterActiveTerm;
  }
  auditActionLabel(action: string): string {
    const labels: Record<string, string> = {
      MEMBERSHIP_CREATED: 'Membership created',
      MEMBERSHIP_RENEWED: 'Renewal scheduled',
      MEMBERSHIP_CANCELLED: 'Membership cancelled',
      MEMBERSHIP_RENEWAL_CANCELLED: 'Renewal cancelled',
      MEMBERSHIP_FROZEN: 'Membership frozen',
      MEMBERSHIP_FREEZE_CANCELLED: 'Scheduled freeze cancelled',
      MEMBERSHIP_FREEZE_ENDED_EARLY: 'Freeze ended early',
      MEMBERSHIP_TRANSFERRED: 'Membership transferred',
      MEMBERSHIP_CORRECTED: 'Membership corrected',
      PACKAGE_CREATED: 'Package created',
      PACKAGE_UPDATED: 'Package updated',
      PACKAGE_ARCHIVED: 'Package archived',
      PACKAGE_RESTORED: 'Package restored',
      BRANCH_CREATED: 'Branch created',
      BRANCH_UPDATED: 'Branch updated',
      STAFF_CREATED: 'Staff account created',
      STAFF_DELETED: 'Staff account removed',
      PT_CREATED: 'Trainer profile registered',
      PT_UPDATED: 'Trainer details updated',
      PT_SESSION_BOOKED: 'Training session booked',
      PT_SESSION_COMPLETED: 'Training session completed',
      PT_SESSION_MISSED: 'Training session missed',
      PT_SESSION_CANCELLED: 'Training session cancelled'
    };
    return labels[action] ?? action.split('_').filter(Boolean)
      .map((part) => part.charAt(0) + part.slice(1).toLowerCase()).join(' ');
  }
  auditActionClass(action: string): string {
    if (!action) return 'action-info';
    if (action.includes('CREATED') || action.includes('ENROLLED') || action.includes('RESTORED') || action.includes('ACTIVE') || action.includes('COMPLETED') || action.includes('PAID') || action.includes('SETTLED')) return 'action-positive';
    if (action.includes('CANCELLED') || action.includes('DELETED') || action.includes('ARCHIVED')) return 'action-negative';
    if (action.includes('FROZEN') || action.includes('CORRECTED') || action.includes('TRANSFERRED') || action.includes('UPDATED') || action.includes('MISSED')) return 'action-warning';
    return 'action-info';
  }
  auditChanges(entry: ActionHistory): AuditChange[] {
    const cached = this.auditDiffCache.get(entry);
    if (cached) return cached;
    const before = this.parseAuditObject(entry.beforeData);
    const after = this.parseAuditObject(entry.afterData);
    if ((entry.beforeData && !before) || (entry.afterData && !after)) {
      const diff = [{
        field: 'Stored audit data',
        before: entry.beforeData ?? '—',
        after: entry.afterData ?? '—'
      }];
      this.auditDiffCache.set(entry, diff);
      return diff;
    }
    const keys = [...new Set([...Object.keys(before ?? {}), ...Object.keys(after ?? {})])];
    const diff = keys.filter((key) => JSON.stringify(before?.[key]) !== JSON.stringify(after?.[key]))
      .map((key) => ({
        field: this.auditFieldLabel(key),
        before: this.auditValue(before?.[key]),
        after: this.auditValue(after?.[key])
      }));
    this.auditDiffCache.set(entry, diff);
    return diff;
  }
  private parseAuditObject(value: string | null): Record<string, unknown> | null {
    if (!value) return null;
    try {
      const parsed: unknown = JSON.parse(value);
      if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) return null;
      return parsed as Record<string, unknown>;
    } catch {
      return null;
    }
  }
  private auditFieldLabel(key: string): string {
    const labels: Record<string, string> = {
      amountPaid: 'Amount paid',
      branchId: 'Branch ID',
      branchName: 'Branch',
      cancelledAt: 'Cancelled at',
      createdAt: 'Created at',
      discountPercent: 'Discount',
      endDate: 'End date',
      frozen: 'Frozen',
      listPrice: 'List price',
      memberName: 'Member',
      planId: 'Package ID',
      planName: 'Package',
      renewalStartDate: 'Renewal starts',
      startDate: 'Start date',
      status: 'Status'
    };
    return labels[key] ?? key.replace(/([A-Z])/g, ' $1').replace(/^./, (value) => value.toUpperCase());
  }
  private auditValue(value: unknown): string {
    if (value === null || value === undefined || value === '') return '—';
    if (typeof value === 'boolean') return value ? 'Yes' : 'No';
    if (typeof value === 'object') return JSON.stringify(value);
    const text = String(value);
    return /^[A-Z0-9_]+$/.test(text)
      ? text.replaceAll('_', ' ').toLowerCase().replace(/^./, (first) => first.toUpperCase())
      : text;
  }
  countForPlan(id: number): number { return this.plans().find((plan) => plan.id === id)?.usageCount ?? 0; }
  discountAmount(): number { return (this.selectedPlan()?.price ?? 0) * Math.min(100, Math.max(0, Number(this.memberDraft.discountPercent) || 0)) / 100; }
  finalPrice(): number { return Math.max(0, (this.selectedPlan()?.price ?? 0) - this.discountAmount()); }
  planDurationLabel(plan: Plan): string { return plan.durationDays !== null ? `${plan.durationDays} ${plan.durationDays === 1 ? 'day' : 'days'}` : `${plan.durationMonths} ${plan.durationMonths === 1 ? 'month' : 'months'}`; }
  addDays(value: string, days: number): string { const date = new Date(`${value}T00:00:00Z`); date.setUTCDate(date.getUTCDate() + days); return date.toISOString().slice(0, 10); }
  addMonths(value: string, months: number): string { const [year, month, day] = value.split('-').map(Number); const firstOfMonth = new Date(Date.UTC(year, month - 1 + months, 1)); const targetYear = firstOfMonth.getUTCFullYear(); const targetMonth = firstOfMonth.getUTCMonth(); const lastDay = new Date(Date.UTC(targetYear, targetMonth + 1, 0)).getUTCDate(); return `${targetYear}-${String(targetMonth + 1).padStart(2, '0')}-${String(Math.min(day, lastDay)).padStart(2, '0')}`; }
  renewalMinimumStartDate(member: Membership): string { return member.status === 'CANCELLED' || member.endDate < this.todayDate() ? this.todayDate() : this.addDays(member.endDate, 1); }
  renewalDiscountAmount(): number { return (this.selectedRenewalPlan()?.price ?? 0) * Math.min(100, Math.max(0, Number(this.renewalDraft.discountPercent) || 0)) / 100; }
  renewalTotal(): number { return Math.max(0, (this.selectedRenewalPlan()?.price ?? 0) - this.renewalDiscountAmount()); }
  renewalEndDate(): string { const plan = this.selectedRenewalPlan(); const exclusiveEnd = plan?.durationDays !== null && plan?.durationDays !== undefined ? this.addDays(this.renewalDraft.startDate, plan.durationDays) : this.addMonths(this.renewalDraft.startDate, plan?.durationMonths ?? 0); return this.addDays(exclusiveEnd, -1); }
  previewEndDate(): string { const plan = this.selectedPlan(); const exclusiveEnd = plan?.durationDays !== null && plan?.durationDays !== undefined ? this.addDays(this.memberDraft.startDate, plan.durationDays) : this.addMonths(this.memberDraft.startDate, plan?.durationMonths ?? 0); return this.dateLabel(this.addDays(exclusiveEnd, -1)); }
  barHeight(value: number): number {
    if (value <= 0) return 0;
    const max = Math.max(this.dashboard()?.revenueThisMonth ?? 0, this.dashboard()?.revenueLastMonth ?? 0, 1);
    return Math.max(8, Math.round((value / max) * 100));
  }

  private async loadData(): Promise<void> {
    try {
      if (this.user()?.role === 'SUPER_ADMIN') {
        await this.loadPlatformData();
        return;
      }
      if (this.user()?.role === 'PERSONAL_TRAINER') {
        await this.loadMyPtDashboardData();
        return;
      }
      const owner = this.user()?.role === 'OWNER';
      const staffUser = this.user()?.role === 'STAFF';
      const branchRequest = this.user()?.role === 'MEMBER'
        ? Promise.resolve([] as Branch[])
        : firstValueFrom(this.api.branches());
      const loadOverview = owner;
      const loadTeam = owner && this.view() === 'team';
      const loadHistory = owner && this.view() === 'history';
      const loadPt = owner && this.view() === 'pt-management';
      const [plans, membershipPage, branches, pts, dashboard, recentMemberships, staff, actionHistory, renewalReminders, staffExpiring, staffRecent] = await Promise.all([
        firstValueFrom(this.api.plans()),
        firstValueFrom(this.api.membershipPage(this.membershipPageIndex(), this.membershipPageSize,
          this.searchTerm.trim(), this.memberFilter())),
        branchRequest,
        firstValueFrom(this.api.personalTrainers()).catch(() => [] as PtProfile[]),
        loadOverview ? firstValueFrom(this.api.dashboard()).catch(() => null) : Promise.resolve(null),
        loadOverview ? firstValueFrom(this.api.recentMemberships(this.dashboardBranchId())).catch(() => null) : Promise.resolve(null),
        loadTeam ? firstValueFrom(this.api.staff()).catch(() => null) : Promise.resolve(null),
        loadHistory ? firstValueFrom(this.api.history()).catch(() => null) : Promise.resolve(null),
        loadHistory ? firstValueFrom(this.api.renewalReminders()).catch(() => null) : Promise.resolve(null),
        staffUser ? firstValueFrom(this.api.expiringMemberships(7)).catch(() => [] as Membership[]) : Promise.resolve([] as Membership[]),
        staffUser ? firstValueFrom(this.api.recentMemberships(null)).catch(() => [] as Membership[]) : Promise.resolve([] as Membership[])
      ]);
      this.plans.set(plans);
      this.setMembershipPage(membershipPage);
      this.branches.set(branches);
      this.personalTrainers.set(pts);
      if (staffExpiring && staffExpiring.length > 0) this.staffExpiringMembersList.set(staffExpiring);
      if (staffRecent && staffRecent.length > 0) this.staffRecentMembersList.set(staffRecent);
      if (loadPt) void this.loadPtManagementData();
      if (owner) void this.loadMyUpgradeRequests();
      if (this.dashboardBranchId() !== null
        && !branches.some((branch) => branch.id === this.dashboardBranchId())) {
        this.dashboardBranchId.set(null);
      }
      if (dashboard) this.dashboard.set(dashboard);
      if (recentMemberships) this.overviewRecentMemberships.set(recentMemberships);
      if (staff) this.staff.set(staff);
      if (actionHistory) this.actionHistory.set(actionHistory);
      if (renewalReminders) this.renewalReminders.set(renewalReminders);
      this.refreshedAt.set(new Date());
    } catch (error) {
      if (this.user()) {
        this.error.set(this.api.message(error, 'Could not load club data. Check that the API is running.'));
      }
    }
  }

  private async loadOverviewData(): Promise<void> {
    if (this.user()?.role === 'STAFF') {
      try {
        const [recent, expiring] = await Promise.all([
          firstValueFrom(this.api.recentMemberships(null)).catch(() => [] as Membership[]),
          firstValueFrom(this.api.expiringMemberships(7)).catch(() => [] as Membership[])
        ]);
        this.staffRecentMembersList.set(recent);
        this.staffExpiringMembersList.set(expiring);
        this.refreshedAt.set(new Date());
      } catch (error) {
        this.error.set(this.api.message(error, 'Could not load front desk data.'));
      }
      return;
    }
    if (this.user()?.role !== 'OWNER') return;
    const branchId = this.dashboardBranchId();
    try {
      const [dashboard, recent, ptAnalytics] = await Promise.all([
        firstValueFrom(this.api.dashboard()),
        firstValueFrom(this.api.recentMemberships(branchId)),
        firstValueFrom(this.api.ptAnalytics(this.addDays(this.todayDate(), -30), this.todayDate())).catch(() => null)
      ]);
      this.dashboard.set(dashboard);
      if (this.dashboardBranchId() === branchId) this.overviewRecentMemberships.set(recent);
      if (ptAnalytics) this.ptAnalyticsData.set(ptAnalytics);
      this.refreshedAt.set(new Date());
    } catch (error) {
      this.error.set(this.api.message(error, 'Could not load overview data.'));
    }
  }

  async refreshOverview(): Promise<void> {
    this.busy.set(true);
    try {
      await this.loadOverviewData();
      this.notice.set('Overview data refreshed.');
    } catch (error) {
      this.error.set(this.api.message(error, 'Could not refresh overview.'));
    } finally {
      this.busy.set(false);
    }
  }

  private async loadOverviewRecentMemberships(): Promise<void> {
    const branchId = this.dashboardBranchId();
    try {
      const recent = await firstValueFrom(this.api.recentMemberships(branchId));
      if (this.dashboardBranchId() === branchId) this.overviewRecentMemberships.set(recent);
    } catch (error) {
      this.error.set(this.api.message(error, 'Could not load recent membership activity.'));
    }
  }

  private async loadPlans(): Promise<void> {
    try {
      this.plans.set(await firstValueFrom(this.api.plans()));
    } catch (error) {
      this.error.set(this.api.message(error, 'Could not refresh packages.'));
    }
  }

  private async loadStaffData(): Promise<void> {
    try {
      this.staff.set(await firstValueFrom(this.api.staff()));
    } catch (error) {
      this.error.set(this.api.message(error, 'Could not load the staff list.'));
    }
  }

  private async loadHistoryData(): Promise<void> {
    try {
      const [history, reminders] = await Promise.all([
        firstValueFrom(this.api.history()),
        firstValueFrom(this.api.renewalReminders())
      ]);
      this.actionHistory.set(history);
      this.renewalReminders.set(reminders);
    } catch (error) {
      this.error.set(this.api.message(error, 'Could not load action history and renewal reminders.'));
    }
  }

  private async refreshMembershipViews(page = this.membershipPageIndex()): Promise<void> {
    await Promise.all([
      this.loadMembershipPage(page),
      this.user()?.role === 'OWNER' ? this.loadOverviewData() : Promise.resolve()
    ]);
  }

  private async refreshBranchesAndOverview(): Promise<void> {
    try {
      this.branches.set(await firstValueFrom(this.api.branches()));
      if (this.dashboardBranchId() !== null
        && !this.branches().some((branch) => branch.id === this.dashboardBranchId())) {
        this.dashboardBranchId.set(null);
      }
      if (this.user()?.role === 'OWNER') await this.loadOverviewData();
    } catch (error) {
      this.error.set(this.api.message(error, 'Could not refresh branches.'));
    }
  }

  private async loadMembershipPage(page: number): Promise<void> {
    try {
      const result = await firstValueFrom(this.api.membershipPage(
        page, this.membershipPageSize, this.searchTerm.trim(), this.memberFilter()));
      this.setMembershipPage(result);
      this.error.set('');
    } catch (error) {
      this.error.set(this.api.message(error, 'Could not load the requested member page.'));
    }
  }

  private setMembershipPage(result: import('./gym-api.service').MembershipPage): void {
    this.membershipGroups.set(result.members);
    this.memberships.set(result.members.flatMap((group) => group.terms));
    this.membershipPageIndex.set(result.page);
    this.membershipTotal.set(result.totalMembers);
    this.membershipTotalPages.set(result.totalPages);
  }

  async loadPtManagementData(): Promise<void> {
    this.busy.set(true);
    try {
      const [pts, payouts, analytics] = await Promise.all([
        firstValueFrom(this.api.personalTrainers()),
        firstValueFrom(this.api.allPtPayouts()),
        firstValueFrom(this.api.ptAnalytics(this.addDays(this.todayDate(), -30), this.todayDate()))
      ]);
      this.personalTrainers.set(pts);
      this.ptPayouts.set(payouts);
      this.ptAnalyticsData.set(analytics);
    } catch (error) {
      this.error.set(this.api.message(error, 'Could not load trainer management data.'));
    } finally {
      this.busy.set(false);
    }
  }

  async loadMyPtDashboardData(): Promise<void> {
    this.busy.set(true);
    try {
      const [profile, sessions, trainees] = await Promise.all([
        firstValueFrom(this.api.myPtProfile()),
        firstValueFrom(this.api.myPtSessions()),
        firstValueFrom(this.api.myPtTrainees()).catch(() => [] as Membership[])
      ]);
      this.myPtProfile.set(profile);
      this.ptSessions.set(sessions);
      this.ptTrainees.set(trainees);
      const groups = this.toMembershipGroups(trainees);
      this.membershipGroups.set(groups);
      this.membershipTotal.set(groups.length);
      this.membershipTotalPages.set(1);
    } catch (error) {
      this.error.set(this.api.message(error, 'Could not load trainer dashboard.'));
    } finally {
      this.busy.set(false);
    }
  }

  async loadMyPtEarningsData(): Promise<void> {
    this.busy.set(true);
    try {
      const [earnings, payouts] = await Promise.all([
        firstValueFrom(this.api.myPtEarnings()),
        firstValueFrom(this.api.myPtPayouts())
      ]);
      this.ptEarnings.set(earnings);
      this.ptPayouts.set(payouts);
    } catch (error) {
      this.error.set(this.api.message(error, 'Could not load earnings.'));
    } finally {
      this.busy.set(false);
    }
  }

  startNewPt(): void {
    this.error.set('');
    this.editingPt.set(true);
    this.ptDraft = {
      id: 0,
      fullName: '',
      email: '',
      phoneNumber: '',
      password: '',
      branchId: this.activeBranches()[0]?.id ? String(this.activeBranches()[0].id) : '',
      specializations: '',
      bio: '',
      sessionFeePerHour: 500,
      commissionPercent: 70
    };
  }

  editPt(pt: PtProfile): void {
    this.error.set('');
    this.editingPt.set(true);
    this.ptDraft = {
      id: pt.id,
      fullName: pt.fullName,
      email: pt.email,
      phoneNumber: pt.phoneNumber ?? '',
      password: '',
      branchId: pt.branchId ? String(pt.branchId) : '',
      specializations: pt.specializations ?? '',
      bio: pt.bio ?? '',
      sessionFeePerHour: pt.sessionFeePerHour,
      commissionPercent: pt.commissionPercent
    };
  }

  cancelPtEdit(): void {
    this.editingPt.set(false);
    this.error.set('');
  }

  async savePt(): Promise<void> {
    this.error.set('');
    this.busy.set(true);
    try {
      if (this.ptDraft.id === 0) {
        await firstValueFrom(this.api.createPt({
          fullName: this.ptDraft.fullName.trim(),
          email: this.ptDraft.email.trim(),
          phoneNumber: this.ptDraft.phoneNumber.trim(),
          password: this.ptDraft.password,
          branchId: this.ptDraft.branchId ? Number(this.ptDraft.branchId) : null,
          specializations: this.ptDraft.specializations.trim(),
          bio: this.ptDraft.bio.trim(),
          sessionFeePerHour: Number(this.ptDraft.sessionFeePerHour),
          commissionPercent: Number(this.ptDraft.commissionPercent)
        }));
        this.notice.set('Trainer created successfully.');
      } else {
        await firstValueFrom(this.api.updatePt(this.ptDraft.id, {
          branchId: this.ptDraft.branchId ? Number(this.ptDraft.branchId) : null,
          specializations: this.ptDraft.specializations.trim(),
          bio: this.ptDraft.bio.trim(),
          sessionFeePerHour: Number(this.ptDraft.sessionFeePerHour),
          commissionPercent: Number(this.ptDraft.commissionPercent)
        }));
        this.notice.set('Trainer updated successfully.');
      }
      this.editingPt.set(false);
      await this.loadPtManagementData();
    } catch (error) {
      this.error.set(this.api.message(error, 'Could not save trainer profile.'));
    } finally {
      this.busy.set(false);
    }
  }

  async togglePtActive(pt: PtProfile): Promise<void> {
    this.error.set('');
    this.busy.set(true);
    try {
      if (pt.active) {
        await firstValueFrom(this.api.deactivatePt(pt.id));
        this.notice.set(`${pt.fullName} deactivated.`);
      } else {
        await firstValueFrom(this.api.activatePt(pt.id));
        this.notice.set(`${pt.fullName} activated.`);
      }
      await this.loadPtManagementData();
    } catch (error) {
      this.error.set(this.api.message(error, 'Could not change trainer status.'));
    } finally {
      this.busy.set(false);
    }
  }

  readonly sessionCompleting = signal<PtSession | null>(null);
  sessionPinInput = '';
  sessionCompletionNotes = '';

  openSessionCompletionModal(session: PtSession): void {
    this.sessionCompleting.set(session);
    this.sessionPinInput = '';
    this.sessionCompletionNotes = '';
  }

  closeSessionCompletionModal(): void {
    this.sessionCompleting.set(null);
    this.sessionPinInput = '';
    this.sessionCompletionNotes = '';
  }

  async submitSessionCompletion(isStaffOverride: boolean = false): Promise<void> {
    const session = this.sessionCompleting();
    if (!session) return;
    this.error.set('');
    this.busy.set(true);
    try {
      const pin = isStaffOverride ? '' : this.sessionPinInput.trim();
      await firstValueFrom(this.api.completePtSession(session.id, this.sessionCompletionNotes || 'Completed session', pin));
      this.notice.set(`Session with ${session.memberName} marked as COMPLETED.`);
      this.closeSessionCompletionModal();
      if (this.user()?.role === 'PERSONAL_TRAINER') {
        await this.loadMyPtDashboardData();
      } else {
        const pt = this.selectedPtForSessions();
        if (pt) await this.loadPtSessionsForModal(pt.id);
        await this.loadPtManagementData();
      }
      if (this.analyticsPeriod() === 'custom') void this.loadReport();
    } catch (error) {
      this.error.set(this.api.message(error, 'Could not complete session.'));
    } finally {
      this.busy.set(false);
    }
  }

  toMembershipGroups(items: Membership[]): import('./gym-api.service').MembershipGroup[] {
    const map = new Map<string, import('./gym-api.service').MembershipGroup>();
    for (const m of items) {
      const key = m.email || String(m.id);
      let g = map.get(key);
      if (!g) {
        g = {
          memberId: m.id,
          memberName: m.memberName,
          email: m.email,
          phoneNumber: m.phoneNumber,
          terms: []
        };
        map.set(key, g);
      }
      g.terms.push(m);
    }
    return Array.from(map.values());
  }

  openBookSessionModal(membership: Membership): void {
    this.bookingPtSession.set(membership);
    this.bookingSessionDate = this.todayDate();
    const now = new Date();
    const curHour = String(now.getHours()).padStart(2, '0');
    const nextHour = String(Math.min(23, now.getHours() + 1)).padStart(2, '0');
    this.bookingSessionStartTime = `${curHour}:00`;
    this.bookingSessionEndTime = `${nextHour}:00`;
    this.bookingSessionNotes = '';
  }

  closeBookSessionModal(): void {
    this.bookingPtSession.set(null);
  }

  async submitBookSession(): Promise<void> {
    const membership = this.bookingPtSession();
    if (!membership) return;
    if (!this.bookingSessionDate || !this.bookingSessionStartTime || !this.bookingSessionEndTime) {
      this.error.set('Please select session date, start time, and end time.');
      return;
    }
    this.error.set('');
    this.busy.set(true);
    try {
      await firstValueFrom(this.api.bookPtSession({
        membershipId: membership.id,
        scheduledDate: this.bookingSessionDate,
        startTime: this.bookingSessionStartTime,
        endTime: this.bookingSessionEndTime,
        notes: this.bookingSessionNotes || undefined
      }));
      this.notice.set(`Training session scheduled for ${membership.memberName}.`);
      this.closeBookSessionModal();
      await this.loadMyPtDashboardData();
    } catch (error) {
      this.error.set(this.api.message(error, 'Could not schedule session. Check for time conflicts or remaining session limits.'));
    } finally {
      this.busy.set(false);
    }
  }

  async quickStartSessionNow(membership: Membership): Promise<void> {
    this.error.set('');
    this.busy.set(true);
    try {
      const now = new Date();
      const curHour = String(now.getHours()).padStart(2, '0');
      const nextHour = String(Math.min(23, now.getHours() + 1)).padStart(2, '0');
      const session = await firstValueFrom(this.api.bookPtSession({
        membershipId: membership.id,
        scheduledDate: this.todayDate(),
        startTime: `${curHour}:00`,
        endTime: `${nextHour}:00`,
        notes: 'In-progress training session'
      }));
      await this.loadMyPtDashboardData();
      this.openSessionCompletionModal(session);
      this.notice.set(`Session started with ${membership.memberName}. Ask client for their 4-digit PIN to complete attendance.`);
    } catch (error) {
      this.error.set(this.api.message(error, 'Could not start session now.'));
    } finally {
      this.busy.set(false);
    }
  }

  completePtSessionQuick(session: PtSession): void {
    this.openSessionCompletionModal(session);
  }

  async missedPtSessionQuick(session: PtSession): Promise<void> {
    this.error.set('');
    this.busy.set(true);
    try {
      await firstValueFrom(this.api.markPtSessionMissed(session.id));
      this.notice.set(`Session marked as missed.`);
      await this.loadMyPtDashboardData();
    } catch (error) {
      this.error.set(this.api.message(error, 'Could not mark session missed.'));
    } finally {
      this.busy.set(false);
    }
  }

  async cancelPtSessionQuick(session: PtSession): Promise<void> {
    const reason = window.prompt('Please enter a cancellation reason:');
    if (reason === null) return;
    this.error.set('');
    this.busy.set(true);
    try {
      await firstValueFrom(this.api.cancelPtSession(session.id, reason || 'Cancelled'));
      this.notice.set(`Session cancelled.`);
      if (this.user()?.role === 'PERSONAL_TRAINER') {
        await this.loadMyPtDashboardData();
      }
    } catch (error) {
      this.error.set(this.api.message(error, 'Could not cancel session.'));
    } finally {
      this.busy.set(false);
    }
  }

  openPtPayoutModal(pt: PtProfile): void {
    this.ptPayoutDraft = {
      ptId: pt.id,
      ptName: pt.fullName,
      periodFrom: this.addDays(this.todayDate(), -30),
      periodTo: this.todayDate(),
      notes: ''
    };
    this.ptPayoutModalOpen.set(true);
  }

  async submitPtPayout(): Promise<void> {
    this.error.set('');
    this.busy.set(true);
    try {
      await firstValueFrom(this.api.createPtPayout(this.ptPayoutDraft.ptId, {
        periodFrom: this.ptPayoutDraft.periodFrom,
        periodTo: this.ptPayoutDraft.periodTo,
        notes: this.ptPayoutDraft.notes
      }));
      this.notice.set('Payout batch created successfully.');
      this.ptPayoutModalOpen.set(false);
      await this.loadPtManagementData();
    } catch (error) {
      this.error.set(this.api.message(error, 'Could not create payout batch. Ensure there are unpaid completed sessions in the period.'));
    } finally {
      this.busy.set(false);
    }
  }

  async markPtPayoutPaidQuick(payout: PtPayout): Promise<void> {
    this.error.set('');
    this.busy.set(true);
    try {
      await firstValueFrom(this.api.markPtPayoutPaid(payout.id));
      this.notice.set(`Payout #${payout.id} marked as PAID.`);
      await this.loadPtManagementData();
    } catch (error) {
      this.error.set(this.api.message(error, 'Could not mark payout paid.'));
    } finally {
      this.busy.set(false);
    }
  }

  async openPtSessionsModal(pt: PtProfile): Promise<void> {
    this.selectedPtForSessions.set(pt);
    this.ptSessionsModalFilter.set('ALL');
    this.ptSessionsModalOpen.set(true);
    await this.loadPtSessionsForModal(pt.id);
  }

  closePtSessionsModal(): void {
    this.ptSessionsModalOpen.set(false);
    this.selectedPtForSessions.set(null);
    this.ptSessionsModalList.set([]);
  }

  async loadPtSessionsForModal(ptId: number): Promise<void> {
    this.ptSessionsModalLoading.set(true);
    try {
      const list = await firstValueFrom(this.api.ptSessions(ptId));
      this.ptSessionsModalList.set(list);
    } catch (err) {
      this.error.set(this.api.message(err, 'Could not load sessions for this trainer.'));
    } finally {
      this.ptSessionsModalLoading.set(false);
    }
  }

  completePtSessionFromModal(session: PtSession): void {
    this.openSessionCompletionModal(session);
  }

  async markPtSessionMissedFromModal(session: PtSession): Promise<void> {
    this.error.set('');
    this.busy.set(true);
    try {
      await firstValueFrom(this.api.markPtSessionMissed(session.id));
      this.notice.set('Session marked as missed.');
      const pt = this.selectedPtForSessions();
      if (pt) await this.loadPtSessionsForModal(pt.id);
      await this.loadPtManagementData();
    } catch (error) {
      this.error.set(this.api.message(error, 'Could not mark session missed.'));
    } finally {
      this.busy.set(false);
    }
  }

  async cancelPtSessionFromModal(session: PtSession): Promise<void> {
    const reason = prompt('Please enter cancellation reason:', 'Client requested reschedule');
    if (reason === null) return;
    this.error.set('');
    this.busy.set(true);
    try {
      await firstValueFrom(this.api.cancelPtSession(session.id, reason || 'Cancelled'));
      this.notice.set('Session cancelled.');
      const pt = this.selectedPtForSessions();
      if (pt) await this.loadPtSessionsForModal(pt.id);
      await this.loadPtManagementData();
    } catch (error) {
      this.error.set(this.api.message(error, 'Could not cancel session.'));
    } finally {
      this.busy.set(false);
    }
  }

  visiblePtSessions(): PtSession[] {
    const filter = this.ptSessionStatusFilter();
    if (filter === 'all') return this.ptSessions();
    return this.ptSessions().filter(s => s.status === filter);
  }

  upcomingPtSessions(): PtSession[] {
    return this.ptSessions().filter(s => s.status === 'BOOKED' || s.status === 'TENTATIVE');
  }

  completedPtEarnings(): PtEarning[] {
    return this.ptEarnings();
  }

  pendingPtEarningsTotal(): number {
    return this.ptEarnings()
      .filter(e => !e.payoutId)
      .reduce((sum, e) => sum + e.ptCommission, 0);
  }

  paidPtEarningsTotal(): number {
    return this.ptEarnings()
      .filter(e => !!e.payoutId)
      .reduce((sum, e) => sum + e.ptCommission, 0);
  }

  totalPtGrossRevenue(): number {
    return this.ptEarnings()
      .reduce((sum, e) => sum + e.grossRevenue, 0);
  }

  totalPtNetCommission(): number {
    return this.ptEarnings()
      .reduce((sum, e) => sum + e.ptCommission, 0);
  }

  private readStoredUser(): User | null {
    try { const value = sessionStorage.getItem('gym.user'); return value ? JSON.parse(value) as User : null; }
    catch { return null; }
  }
}
