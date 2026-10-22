import { Component, computed, effect, signal, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { GymApiService, MembershipUpgradeQuote, MembershipUpgradePayload, SaasUpgradeQuote, ActionHistory, Analytics, Branch, BranchPayload, BranchType, CancellationReason, Dashboard, Freeze, Membership, MembershipAccess, MembershipGroup, Plan, RenewalReminder, User, PtProfile, PtSession, PtEarning, PtPayout, PtAnalytics, PtPackageType, ActivationCode, PlatformMetrics, PlatformOrganization, UpgradeRequest, UpgradeRequestPayload, TenantBackup, BulkMemberImportRow, BulkMemberImportResult } from './gym-api.service';
import { CsrfTokenService } from './csrf-token.service';
import { ALL_LEGAL_DOCUMENTS, LegalDocument } from './legal-content';
import { CookieBannerComponent, LegalModalComponent } from './shared/components';
import {
  PublicSiteComponent,
  AuthComponent,
  SidebarComponent,
  TopbarComponent,
  OwnerOverviewComponent,
  StaffOverviewComponent,
  MembersComponent,
  PackagesComponent,
  RegisterComponent,
  TeamComponent,
  BranchesComponent,
  AccountComponent,
  PlatformAdminComponent,
  ReportsComponent,
  HistoryComponent,
  PtManagementComponent,
  PtDashboardComponent,
  PtEarningsComponent
} from './features';
import {
  CancellationModalComponent,
  RenewalModalComponent,
  AdjustmentModalComponent,
  PtPayoutModalComponent,
  MembershipUpgradeModalComponent,
  RejectRequestModalComponent,
  RedeemModalComponent,
  CancelSubscriptionModalComponent,
  GenerateCodeModalComponent,
  PtSessionsModalComponent,
  PtSessionCompleteModalComponent,
  PtBookSessionModalComponent,
  UpgradeModalComponent
} from './shared/modals';

type View = 'overview' | 'members' | 'packages' | 'register' | 'team' | 'account' | 'branches' | 'reports' | 'history' | 'pt-management' | 'pt-dashboard' | 'pt-earnings' | 'platform-admin';
type AuthMode = 'home' | 'login' | 'forgot-password' | 'register-gym';
type MemberFilter = 'all' | 'active' | 'expiring' | 'cancelled';
type PlanFilter = 'active' | 'archived' | 'all';
type AdjustmentMode = 'freeze' | 'transfer' | 'correct' | 'cancel-freeze' | 'end-freeze';
type AuditChange = { field: string; before: string; after: string };

function hasExpiredSession(): boolean {
  return typeof sessionStorage !== 'undefined' && sessionStorage.getItem('gym.session-expired') === '1';
}

@Component({
  selector: 'app-root',
  imports: [
    FormsModule,
    CommonModule,
    CookieBannerComponent,
    LegalModalComponent,
    PublicSiteComponent,
    AuthComponent,
    SidebarComponent,
    TopbarComponent,
    OwnerOverviewComponent,
    StaffOverviewComponent,
    MembersComponent,
    PackagesComponent,
    RegisterComponent,
    TeamComponent,
    BranchesComponent,
    AccountComponent,
    PlatformAdminComponent,
    ReportsComponent,
    HistoryComponent,
    PtManagementComponent,
    PtDashboardComponent,
    PtEarningsComponent,
    CancellationModalComponent,
    RenewalModalComponent,
    AdjustmentModalComponent,
    PtPayoutModalComponent,
    MembershipUpgradeModalComponent,
    RejectRequestModalComponent,
    RedeemModalComponent,
    CancelSubscriptionModalComponent,
    GenerateCodeModalComponent,
    PtSessionsModalComponent,
    PtSessionCompleteModalComponent,
    PtBookSessionModalComponent,
    UpgradeModalComponent
  ],
  templateUrl: './dashboard.html',
  styles: [':host { display: block; min-height: 100vh; }']
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
  readonly isPremium = computed(() => ['STARTER', 'PRO', 'ENTERPRISE', 'PREMIUM'].includes(this.user()?.subscriptionTier || ''));
  readonly hasBrandingAccess = computed(() => !!this.user()?.enabledFeatures?.includes('CUSTOM_BRANDING') || ['STARTER', 'PRO', 'ENTERPRISE', 'PREMIUM'].includes(this.user()?.subscriptionTier || ''));
  readonly hasReceiptUploadAccess = computed(() => !!this.user()?.enabledFeatures?.includes('RECEIPT_IMAGE_UPLOAD') || ['PRO', 'ENTERPRISE', 'PREMIUM'].includes(this.user()?.subscriptionTier || ''));
  readonly hasHistoryAccess = computed(() => !!this.user()?.enabledFeatures?.includes('ACTION_HISTORY') || ['PRO', 'ENTERPRISE', 'PREMIUM'].includes(this.user()?.subscriptionTier || ''));
  readonly hasPtAccess = computed(() => !!this.user()?.enabledFeatures?.includes('PERSONAL_TRAINERS') || ['ENTERPRISE', 'PREMIUM'].includes(this.user()?.subscriptionTier || ''));
  readonly hasAnalyticsAccess = computed(() => !!this.user()?.enabledFeatures?.includes('ANALYTICS') || ['ENTERPRISE', 'PREMIUM'].includes(this.user()?.subscriptionTier || ''));

  tierBadgeLabel(tier?: string): string {
    switch (tier) {
      case 'STARTER': return '⭐ STARTER';
      case 'PRO': return '⚡ PRO';
      case 'ENTERPRISE':
      case 'PREMIUM': return '👑 ENTERPRISE';
      default: return 'STANDARD';
    }
  }

  tierDisplayName(tier?: string): string {
    switch (tier) {
      case 'STARTER': return 'Starter';
      case 'PRO': return 'Pro';
      case 'ENTERPRISE':
      case 'PREMIUM': return 'Enterprise';
      default: return 'Standard';
    }
  }

  tierPillClass(tier?: string): string {
    switch (tier) {
      case 'STARTER': return 'tier-starter';
      case 'PRO': return 'tier-pro';
      case 'ENTERPRISE':
      case 'PREMIUM': return 'tier-enterprise';
      default: return 'tier-standard';
    }
  }

  canUpgradeTier(): boolean {
    const role = this.user()?.role;
    const tier = this.user()?.subscriptionTier;
    return role === 'OWNER' && tier !== 'ENTERPRISE' && tier !== 'PREMIUM';
  }

  readonly brandingDraft = {
    name: '',
    logoData: '' as string | null
  };
  readonly brandingSaving = signal(false);
  readonly brandingSuccessNotice = signal('');
  readonly logoPreview = signal<string | null>(null);

  initBrandingDraft(): void {
    const u = this.user();
    this.brandingDraft.name = u?.organizationName || '';
    this.brandingDraft.logoData = u?.organizationLogo || null;
    this.logoPreview.set(u?.organizationLogo || null);
  }

  onLogoSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (!input.files || input.files.length === 0) return;
    const file = input.files[0];
    if (!['image/jpeg', 'image/png', 'image/webp', 'image/svg+xml'].includes(file.type)) {
      this.error.set('Please select a valid image (PNG, JPG, SVG, WebP) under 2 MB.');
      return;
    }
    if (file.size > 2 * 1024 * 1024) {
      this.error.set('Logo file size must be less than 2 MB.');
      return;
    }
    const reader = new FileReader();
    reader.onload = () => {
      const result = reader.result as string;
      this.brandingDraft.logoData = result;
      this.logoPreview.set(result);
    };
    reader.readAsDataURL(file);
  }

  removeBrandingLogo(): void {
    this.brandingDraft.logoData = null;
    this.logoPreview.set(null);
  }

  async saveBranding(): Promise<void> {
    if (!this.brandingDraft.name.trim()) {
      this.error.set('Gym name cannot be empty.');
      return;
    }
    if (!this.hasBrandingAccess()) {
      this.openUpgradeModal('branding');
      return;
    }
    this.brandingSaving.set(true);
    this.error.set('');
    try {
      const updated = await firstValueFrom(this.api.updateBranding({
        name: this.brandingDraft.name.trim(),
        logoData: this.brandingDraft.logoData
      }));
      this.user.set(updated);
      try { sessionStorage.setItem('gym.user', JSON.stringify(updated)); } catch {}
      this.initBrandingDraft();
      this.notice.set('Gym branding updated successfully!');
      this.brandingSuccessNotice.set('Gym branding updated successfully!');
      setTimeout(() => this.brandingSuccessNotice.set(''), 4000);
    } catch (err: any) {
      this.error.set(this.api.message(err, 'Failed to update gym branding.'));
    } finally {
      this.brandingSaving.set(false);
    }
  }

  readonly upgradeModalOpen = signal(false);
  readonly upgradeFeatureTarget = signal<'pt' | 'history' | 'branding' | 'receipt' | 'analytics' | 'general'>('general');
  readonly upgradeTargetTier = signal<'STARTER' | 'PRO' | 'ENTERPRISE'>('PRO');
  readonly upgradeLoading = signal(false);
  readonly upgradeModalTab = signal<'request' | 'redeem' | 'compare'>('request');
  readonly publicPricingCycle = signal<'ANNUAL' | 'MONTHLY'>('ANNUAL');
  readonly upgradeBillingPlan = signal<'ANNUAL' | 'HALF_YEAR' | 'MONTHLY'>('ANNUAL');
  readonly upgradePaymentMethod = signal<'BANK_WIRE' | 'UPI_QR' | 'CORPORATE_INVOICE'>('BANK_WIRE');
  readonly upgradeGstNumber = signal('');
  readonly upgradeBillingAddress = signal('');
  readonly upgradeNotes = signal('');
  readonly saasUpgradeQuote = signal<SaasUpgradeQuote | null>(null);
  readonly saasUpgradeQuoteLoading = signal<boolean>(false);
  readonly submittingUpgradeRequest = signal(false);
  readonly myUpgradeRequests = signal<UpgradeRequest[]>([]);
  readonly myUpgradeRequestsLoading = signal(false);
  readonly pendingMyUpgradeRequest = computed(() => {
    return this.myUpgradeRequests().find(r => r.status === 'PENDING') || null;
  });

  readonly copiedSettlementField = signal<string>('');

  readonly quotePlanAmount = computed(() => {
    const tier = this.upgradeTargetTier();
    const plan = this.upgradeBillingPlan();
    if (tier === 'STARTER') {
      switch (plan) {
        case 'ANNUAL': return 9999;
        case 'HALF_YEAR': return 5999;
        case 'MONTHLY': return 1199;
      }
    } else if (tier === 'ENTERPRISE') {
      switch (plan) {
        case 'ANNUAL': return 39999;
        case 'HALF_YEAR': return 22999;
        case 'MONTHLY': return 4499;
      }
    }
    switch (plan) {
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
    const tier = this.upgradeTargetTier();
    const plan = this.upgradeBillingPlan();
    if (tier === 'STARTER') {
      switch (plan) {
        case 'ANNUAL': return '₹833 / mo';
        case 'HALF_YEAR': return '₹1,000 / mo';
        case 'MONTHLY': return '₹1,199 / mo';
      }
    } else if (tier === 'ENTERPRISE') {
      switch (plan) {
        case 'ANNUAL': return '₹3,333 / mo';
        case 'HALF_YEAR': return '₹3,833 / mo';
        case 'MONTHLY': return '₹4,499 / mo';
      }
    }
    switch (plan) {
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
    tier: 'PRO' as 'STARTER' | 'PRO' | 'ENTERPRISE' | 'STANDARD' | 'PREMIUM',
    durationDays: 365 as number | null,
    notes: ''
  };
  readonly generatingCode = signal(false);
  readonly copiedCode = signal<string | null>(null);

  // ─── Legal & Compliance Hub Signals ───────────────────────────────────────
  readonly legalModalOpen = signal(false);
  readonly legalModalTab = signal<'terms' | 'privacy' | 'disclaimer' | 'acceptable-use'>('terms');
  readonly legalSearchQuery = signal('');
  readonly cookieConsentDismissed = signal(this.readCookieConsent());

  readonly activeLegalDoc = computed<LegalDocument>(() => ALL_LEGAL_DOCUMENTS[this.legalModalTab()]);
  readonly filteredLegalSections = computed(() => {
    const doc = this.activeLegalDoc();
    const query = this.legalSearchQuery().trim().toLowerCase();
    if (!query) return doc.sections;
    return doc.sections.filter(sec =>
      sec.title.toLowerCase().includes(query) ||
      sec.content.some(para => para.toLowerCase().includes(query))
    );
  });

  openLegal(tab: 'terms' | 'privacy' | 'disclaimer' | 'acceptable-use' = 'terms'): void {
    this.legalModalTab.set(tab);
    this.legalSearchQuery.set('');
    this.legalModalOpen.set(true);
  }

  closeLegal(): void {
    this.legalModalOpen.set(false);
  }

  dismissCookieConsent(): void {
    this.cookieConsentDismissed.set(true);
    if (typeof localStorage !== 'undefined') {
      try {
        localStorage.setItem('gym_cookie_consent_accepted', 'true');
      } catch {
        // ignore storage errors
      }
    }
  }

  private readCookieConsent(): boolean {
    if (typeof localStorage === 'undefined') return true;
    try {
      return localStorage.getItem('gym_cookie_consent_accepted') === 'true';
    } catch {
      return true;
    }
  }

  printLegalDoc(): void {
    if (typeof window !== 'undefined') {
      window.print();
    }
  }

  registerGymDraft = {
    fullName: '',
    email: '',
    phoneNumber: '',
    password: '',
    organizationName: '',
    organizationCode: '',
    branchName: '',
    branchAddress: '',
    subscriptionTier: 'STANDARD' as 'STANDARD' | 'STARTER' | 'PRO' | 'ENTERPRISE' | 'PREMIUM',
    acceptTerms: false
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
  renewalDraft = { planId: '', branchId: '', personalTrainerId: '', discountPercent: 0, startDate: '', paymentMethod: 'CASH', transactionId: '' };
  staffDraft = { fullName: '', email: '', phoneNumber: '', password: '', branchId: '' };
  memberDraft = { fullName: '', email: '', phoneNumber: '', password: '', planId: '', branchId: '', personalTrainerId: '', discountPercent: 0, startDate: this.todayDate(), waiverAcknowledged: false, paymentMethod: 'CASH', transactionId: '' };
  ptDraft = { id: 0, fullName: '', email: '', phoneNumber: '', password: '', branchId: '', specializations: '', bio: '', sessionFeePerHour: 500, commissionPercent: 70 };
  ptPayoutDraft = { ptId: 0, ptName: '', periodFrom: this.addDays(this.todayDate(), -30), periodTo: this.todayDate(), notes: '' };
  receiptFile: File | null = null;
  resetDraft = { email: '', code: '', newPassword: '' };
  profileDraft = { fullName: '', email: '', phoneNumber: '' };
  passwordDraft = { currentPassword: '', newPassword: '' };

  readonly staffExpiringMembersList = signal<Membership[]>([]);

  // ─── Public Pricing & Plans Matrix ───────────────────────────────────────────
  readonly pricingCycle = signal<'ANNUAL' | 'MONTHLY'>('ANNUAL');
  readonly pricingMatrixExpanded = signal<boolean>(true);
  readonly pricingCategoryFilter = signal<'ALL' | 'CAPACITY' | 'WORKFLOW' | 'DATA' | 'SECURITY'>('ALL');
  readonly pricingBrochureModalOpen = signal<boolean>(false);

  // ─── Member Self-Service Portal ──────────────────────────────────────────────
  readonly memberRenewalModalOpen = signal<boolean>(false);
  readonly memberFreezeModalOpen = signal<boolean>(false);
  readonly memberSelectedPlanId = signal<number | null>(null);
  memberFreezeStartDate = '';
  memberFreezeEndDate = '';
  memberFreezeReason = '';

  // ─── Cloud Data Portability & Bulk Import ─────────────────────────────────────
  readonly downloadingBackup = signal<boolean>(false);
  readonly bulkImportModalOpen = signal<boolean>(false);
  readonly bulkImportLoading = signal<boolean>(false);
  bulkImportCsvText = '';
  readonly bulkImportResult = signal<BulkMemberImportResult | null>(null);

  // ─── Sticky Field-Level Validation & Scroll Helper ───────────────────────────
  readonly fieldErrors = signal<Record<string, string>>({});

  // ─── Member Mid-Cycle Plan Upgrade ──────────────────────────────────────────
  readonly upgradingMembership = signal<Membership | null>(null);
  readonly upgradeTargetPlanId = signal<number | null>(null);
  readonly upgradeQuote = signal<MembershipUpgradeQuote | null>(null);
  readonly upgradeQuoteLoading = signal<boolean>(false);
  readonly upgradeQuoteError = signal<string>('');
  upgradeDraft = {
    topUpPaid: 0,
    paymentMethod: 'CASH',
    transactionId: '',
    personalTrainerId: null as number | null
  };

  // ─── Staff Edit State ────────────────────────────────────────────────────────
  readonly editingStaff = signal<boolean>(false);
  staffEditDraft = { id: 0, fullName: '', email: '', phoneNumber: '', branchId: '', password: '' };

  // ─── Branch Enterprise Gating ────────────────────────────────────────────────
  readonly enterpriseBranchPromptOpen = signal<boolean>(false);

  // ─── Packages Feature Comparison Matrix ───────────────────────────────────────
  readonly packageViewMode = signal<'cards' | 'matrix'>('cards');

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
    this.upgradingMembership() ||
    this.legalModalOpen() ||
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
    if (this.upgradingMembership()) { this.closeMemberUpgradeModal(); return; }
    if (this.legalModalOpen()) { this.closeLegal(); return; }
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
      void this.refreshCurrentUser();
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

  async refreshCurrentUser(): Promise<void> {
    if (!this.user()) return;
    try {
      const freshUser = await firstValueFrom(this.api.me());
      this.user.set(freshUser);
      try { sessionStorage.setItem('gym.user', JSON.stringify(freshUser)); } catch {}
      if (this.view() === 'account') {
        this.initBrandingDraft();
      }
    } catch {
      // Retain stored user session if network is unavailable
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

  async registerGym(): Promise<void> {
    if (!this.registerGymDraft.acceptTerms) {
      this.error.set('Please review and accept the Terms of Use, Privacy Policy, and Health Disclaimer to proceed.');
      return;
    }
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

  openUpgradeModal(target: 'pt' | 'history' | 'branding' | 'receipt' | 'analytics' | 'general' = 'general'): void {
    this.upgradeFeatureTarget.set(target);
    if (target === 'branding') {
      this.upgradeTargetTier.set('STARTER');
    } else if (target === 'receipt' || target === 'history') {
      this.upgradeTargetTier.set('PRO');
    } else if (target === 'pt' || target === 'analytics') {
      this.upgradeTargetTier.set('ENTERPRISE');
    } else {
      this.upgradeTargetTier.set('PRO');
    }
    this.upgradeModalTab.set('request');
    this.upgradeModalOpen.set(true);
    void this.loadMyUpgradeRequests();
    this.loadSaasUpgradeQuote();
  }

  setUpgradeTargetTier(tier: 'STARTER' | 'PRO' | 'ENTERPRISE'): void {
    this.upgradeTargetTier.set(tier);
    this.loadSaasUpgradeQuote(tier);
  }

  closeUpgradeModal(): void {
    this.upgradeModalOpen.set(false);
  }

  setUpgradeModalTab(tab: 'request' | 'redeem' | 'compare'): void {
    this.upgradeModalTab.set(tab);
    this.error.set('');
  }

  togglePublicPricingCycle(): void {
    this.publicPricingCycle.update(v => v === 'ANNUAL' ? 'MONTHLY' : 'ANNUAL');
  }

  selectTierFromComparison(tier: 'STARTER' | 'PRO' | 'ENTERPRISE'): void {
    this.setUpgradeTargetTier(tier);
    this.setUpgradeModalTab('request');
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
      const tierTitle = this.tierDisplayName(this.upgradeTargetTier());
      const planName = `${this.upgradeBillingPlan() === 'ANNUAL' ? 'Annual' : this.upgradeBillingPlan() === 'HALF_YEAR' ? 'Half-Year' : 'Monthly'} ${tierTitle} (${this.quotePlanDurationDays()} Days)`;
      const durationDays = this.quotePlanDurationDays();

      await firstValueFrom(this.api.requestUpgrade({
        billingPlan: planName,
        durationDays,
        paymentMethod: this.upgradePaymentMethod(),
        gstNumber: this.upgradeGstNumber().trim() || undefined,
        billingAddress: this.upgradeBillingAddress().trim() || undefined,
        notes: this.upgradeNotes().trim() || undefined,
        targetTier: this.upgradeTargetTier(),
      estimatedTopUp: this.saasUpgradeQuote()?.estimatedTopUp ?? undefined
    }));

      await this.loadMyUpgradeRequests();
      this.notice.set(`Your ${tierTitle} upgrade request has been submitted! Our accounts team will review payment and issue your activation key.`);
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
      tier: 'PRO',
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

  async updateOrgTier(id: number, tier: 'STANDARD' | 'STARTER' | 'PRO' | 'ENTERPRISE' | 'PREMIUM' | string): Promise<void> {
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
    if ((view === 'pt-management' || view === 'pt-dashboard' || view === 'pt-earnings') && !this.hasPtAccess()) {
      this.openUpgradeModal('pt');
      return;
    }
    if (view === 'history' && !this.hasHistoryAccess()) {
      this.openUpgradeModal('history');
      return;
    }
    if (view === 'reports' && !this.hasAnalyticsAccess()) {
      this.openUpgradeModal('analytics');
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
    if (view === 'register') { this.memberDraft = { ...this.memberDraft, fullName: '', email: '', phoneNumber: '', password: '', planId: '', branchId: this.user()?.role === 'OWNER' ? String(this.activeBranches()[0]?.id ?? '') : String(this.user()?.branchId ?? ''), personalTrainerId: '', discountPercent: 0, startDate: this.todayDate(), waiverAcknowledged: false, paymentMethod: 'CASH', transactionId: '' }; this.receiptFile = null; }
    if (view === 'account' && this.user()) {
      this.profileDraft = { fullName: this.user()!.fullName, email: this.user()!.email, phoneNumber: this.user()!.phoneNumber ?? '' };
      this.initBrandingDraft();
    }
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

  readonly ptNoteDismissed = signal<boolean>(false);

  private readonly CHART_PALETTE = ['#6366f1', '#10b981', '#f59e0b', '#ec4899', '#8b5cf6', '#06b6d4', '#3b82f6', '#f43f5e', '#14b8a6', '#64748b'];

  calculateDonutSlices(items: { name: string; value: number; color?: string }[]): Array<{
    name: string;
    value: number;
    percent: number;
    dashArray: string;
    dashOffset: number;
    color: string;
  }> {
    const total = items.reduce((acc, curr) => acc + Math.max(0, curr.value), 0);
    const C = 251.327; // 2 * PI * 40
    if (total <= 0) {
      return [{
        name: 'No data',
        value: 0,
        percent: 0,
        dashArray: `0 ${C}`,
        dashOffset: 0,
        color: '#94a3b8'
      }];
    }
    let accumulatedOffset = 0;
    return items.map((item, index) => {
      const val = Math.max(0, item.value);
      const pct = Math.round((val / total) * 100);
      const arcLength = (val / total) * C;
      const slice = {
        name: item.name,
        value: val,
        percent: pct,
        dashArray: `${arcLength.toFixed(2)} ${(C - arcLength).toFixed(2)}`,
        dashOffset: -accumulatedOffset,
        color: item.color || this.CHART_PALETTE[index % this.CHART_PALETTE.length]
      };
      accumulatedOffset += arcLength;
      return slice;
    });
  }

  executiveSalesDonutSlices(): ReturnType<typeof this.calculateDonutSlices> {
    const data = this.analyticsData();
    if (!data) return [];
    return this.calculateDonutSlices([
      { name: 'New Enrollments', value: data.newMembershipRevenue, color: '#6366f1' },
      { name: 'Renewals', value: data.renewalRevenue, color: '#10b981' },
      { name: 'Discounts Absorbed', value: data.discountsGiven, color: '#f59e0b' }
    ]);
  }

  packagePerformanceDonutSlices(): ReturnType<typeof this.calculateDonutSlices> {
    const data = this.analyticsData();
    if (!data || !data.packagePerformance || data.packagePerformance.length === 0) return [];
    return this.calculateDonutSlices(
      data.packagePerformance.map((pkg, idx) => ({
        name: pkg.packageName,
        value: pkg.revenue,
        color: this.CHART_PALETTE[idx % this.CHART_PALETTE.length]
      }))
    );
  }

  ptSessionInventoryDonutSlices(): ReturnType<typeof this.calculateDonutSlices> {
    const pt = this.ptAnalyticsData();
    if (!pt) return [];
    return this.calculateDonutSlices([
      { name: 'Completed & Verified', value: pt.sessionsCompleted, color: '#10b981' },
      { name: 'Remaining in Pipeline', value: pt.remainingSessions, color: '#6366f1' },
      { name: 'Missed Sessions', value: pt.sessionsMissed, color: '#f43f5e' },
      { name: 'Cancelled Sessions', value: pt.sessionsCancelled, color: '#f59e0b' }
    ]);
  }

  ptRevenueSplitDonutSlices(): ReturnType<typeof this.calculateDonutSlices> {
    const pt = this.ptAnalyticsData();
    if (!pt) return [];
    const gross = pt.totalPtGrossRevenue > 0 ? pt.totalPtGrossRevenue : pt.contractedPtRevenue;
    const gymShare = pt.totalPtGrossRevenue > 0 ? pt.totalGymRetained : Math.round(gross * 0.6);
    const ptShare = pt.totalPtGrossRevenue > 0 ? pt.totalPtCommission : Math.round(gross * 0.4);
    return this.calculateDonutSlices([
      { name: pt.totalPtGrossRevenue > 0 ? 'Club Retained Profit' : 'Est. Club Profit (60%)', value: gymShare, color: '#3b82f6' },
      { name: pt.totalPtGrossRevenue > 0 ? 'Trainer Commission Pool' : 'Est. Trainer Pool (40%)', value: ptShare, color: '#10b981' }
    ]);
  }

  taxBreakdownDonutSlices(): ReturnType<typeof this.calculateDonutSlices> {
    const data = this.analyticsData();
    if (!data || !data.tax) return [];
    const tax = data.tax;
    return this.calculateDonutSlices([
      { name: 'Base Net Sales', value: tax.recordedSalesExcludingEstimatedGst, color: '#6366f1' },
      { name: `CGST (${tax.cgstRate}%)`, value: tax.estimatedCgst, color: '#10b981' },
      { name: `SGST (${tax.sgstRate}%)`, value: tax.estimatedSgst, color: '#06b6d4' },
      { name: `IGST (${tax.igstRate}%)`, value: tax.estimatedIgst, color: '#f59e0b' }
    ]);
  }

  retentionMemberSlices(): ReturnType<typeof this.calculateDonutSlices> {
    const data = this.analyticsData();
    if (!data) return [];
    return this.calculateDonutSlices([
      { name: 'Retained Active Members', value: data.activeMembers, color: '#10b981' },
      { name: 'Opening Cohort Lost', value: data.membersLeft, color: '#f43f5e' },
      { name: 'New Members Acquired', value: data.newMembers, color: '#6366f1' }
    ]);
  }

  maxPackageRevenue(): number {
    const pkgs = this.analyticsData()?.packagePerformance ?? [];
    return Math.max(1, ...pkgs.map(p => p.revenue));
  }

  maxPackageTerms(): number {
    const pkgs = this.analyticsData()?.packagePerformance ?? [];
    return Math.max(1, ...pkgs.map(p => p.memberships));
  }

  roundPct(numerator: number, denominator: number): number {
    if (!denominator || denominator <= 0) return 0;
    return Math.min(100, Math.round((numerator / denominator) * 100));
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

  // ─── Sticky Field-Level Validation & Scroll Helper ───────────────────────────
  hasFieldError(field: string): boolean {
    return !!this.fieldErrors()[field];
  }

  getFieldError(field: string): string {
    return this.fieldErrors()[field] || '';
  }

  setFieldError(field: string, msg: string): void {
    this.fieldErrors.update(curr => ({ ...curr, [field]: msg }));
  }

  clearFieldError(field: string): void {
    if (this.fieldErrors()[field]) {
      this.fieldErrors.update(curr => {
        const next = { ...curr };
        delete next[field];
        return next;
      });
    }
  }

  clearAllFieldErrors(): void {
    this.fieldErrors.set({});
  }

  scrollToFirstError(containerSelector?: string): void {
    if (typeof document === 'undefined') return;
    setTimeout(() => {
      const container = containerSelector ? document.querySelector(containerSelector) : document;
      if (!container) return;
      const firstInvalid = container.querySelector(
        '.field-has-error, .input-has-error, .field-error-msg, input:invalid, select:invalid, [aria-invalid="true"]'
      );
      if (firstInvalid) {
        firstInvalid.scrollIntoView({ behavior: 'smooth', block: 'center' });
        if (firstInvalid instanceof HTMLElement) {
          const inputEl = firstInvalid.tagName === 'INPUT' || firstInvalid.tagName === 'SELECT' || firstInvalid.tagName === 'TEXTAREA'
            ? firstInvalid
            : firstInvalid.querySelector('input, select, textarea');
          if (inputEl instanceof HTMLElement) {
            inputEl.focus({ preventScroll: true });
          }
        }
      }
    }, 60);
  }

  preventNonNumeric(event: KeyboardEvent, allowDecimal = true): void {
    const allowedKeys = ['Backspace', 'Tab', 'Delete', 'ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown', 'Enter', 'Escape', 'Home', 'End'];
    if (allowedKeys.includes(event.key)) return;
    if (event.ctrlKey || event.metaKey) return;
    if (allowDecimal && (event.key === '.' || event.key === ',')) {
      const target = event.target as HTMLInputElement;
      if (target && !target.value.includes('.') && !target.value.includes(',')) return;
    }
    if (!/^[0-9]$/.test(event.key)) {
      event.preventDefault();
    }
  }

  sanitizeNumericInput(event: Event, targetObj: any, field: string, isInt = false): void {
    const input = event.target as HTMLInputElement;
    if (!input) return;
    let clean = input.value.replace(/[^0-9.]/g, '');
    const firstDot = clean.indexOf('.');
    if (firstDot !== -1) {
      clean = clean.substring(0, firstDot + 1) + clean.substring(firstDot + 1).replace(/\./g, '');
    }
    if (clean === '') {
      targetObj[field] = 0;
      return;
    }
    const num = isInt ? parseInt(clean, 10) : parseFloat(clean);
    targetObj[field] = isNaN(num) ? 0 : Math.max(0, num);
  }

  // ─── Staff Edit State & Methods ───────────────────────────────────────────────
  openEditStaffModal(staff: User): void {
    this.clearAllFieldErrors();
    this.staffEditDraft = {
      id: staff.id,
      fullName: staff.fullName,
      email: staff.email,
      phoneNumber: staff.phoneNumber ?? '',
      branchId: staff.branchId ? String(staff.branchId) : (this.activeBranches()[0]?.id ? String(this.activeBranches()[0].id) : ''),
      password: ''
    };
    this.editingStaff.set(true);
  }

  closeEditStaffModal(): void {
    this.editingStaff.set(false);
    this.clearAllFieldErrors();
  }

  async saveStaffEdit(): Promise<void> {
    this.clearAllFieldErrors();
    const draft = this.staffEditDraft;
    let hasErr = false;

    if (!draft.fullName?.trim()) {
      this.setFieldError('staffEditFullName', 'Full name is mandatory.');
      hasErr = true;
    }
    if (!draft.branchId) {
      this.setFieldError('staffEditBranch', 'Branch facility assignment is mandatory.');
      hasErr = true;
    }
    if (draft.password && draft.password.trim().length > 0 && draft.password.trim().length < 8) {
      this.setFieldError('staffEditPassword', 'New password must be at least 8 characters long.');
      hasErr = true;
    }

    if (hasErr) {
      this.error.set('Please fix the required fields marked in red below.');
      this.scrollToFirstError('#staff-edit-modal');
      return;
    }

    this.busy.set(true);
    this.error.set('');
    try {
      const payload: { fullName: string; phoneNumber?: string; branchId: number; password?: string } = {
        fullName: draft.fullName.trim(),
        phoneNumber: draft.phoneNumber.trim() || undefined,
        branchId: Number(draft.branchId)
      };
      if (draft.password && draft.password.trim().length >= 8) {
        payload.password = draft.password.trim();
      }
      await firstValueFrom(this.api.updateStaff(draft.id, payload));
      this.notice.set(`Staff member ${draft.fullName} updated successfully.`);
      this.closeEditStaffModal();
      await this.loadStaffData();
    } catch (err) {
      this.error.set(this.api.message(err, 'Failed to update staff member.'));
      this.scrollToFirstError('#staff-edit-modal');
    } finally {
      this.busy.set(false);
    }
  }

  // ─── Branch Active Quick Toggle & Enterprise Gating ───────────────────────────
  async toggleBranchActive(branch: Branch): Promise<void> {
    this.busy.set(true);
    this.error.set('');
    try {
      const updatedActive = !branch.active;
      const payload: BranchPayload = {
        name: branch.name,
        code: branch.code,
        type: branch.type,
        address: branch.address ?? '',
        phoneNumber: branch.phoneNumber ?? '',
        active: updatedActive
      };
      await firstValueFrom(this.api.updateBranch(branch.id, payload));
      this.notice.set(`Branch "${branch.name}" is now ${updatedActive ? 'Active' : 'Paused'}.`);
      await this.refreshBranchesAndOverview();
    } catch (err) {
      this.error.set(this.api.message(err, 'Could not update branch active status.'));
    } finally {
      this.busy.set(false);
    }
  }

  closeEnterpriseBranchPrompt(): void {
    this.enterpriseBranchPromptOpen.set(false);
  }

  // ─── Packages Feature Comparison Matrix ───────────────────────────────────────
  setPackageViewMode(mode: 'cards' | 'matrix'): void {
    this.packageViewMode.set(mode);
  }

  // ─── Capacity Display Helpers ────────────────────────────────────────────────
  tierMaxBranches(): number {
    const tier = this.user()?.subscriptionTier;
    switch (tier) {
      case 'STANDARD':
      case 'STARTER':
      case 'PRO':
        return 1;
      case 'ENTERPRISE':
      case 'PREMIUM':
      default:
        return 9999;
    }
  }

  tierMaxStaff(): number {
    const tier = this.user()?.subscriptionTier;
    switch (tier) {
      case 'STANDARD': return 2;
      case 'STARTER': return 5;
      case 'PRO': return 15;
      case 'ENTERPRISE':
      case 'PREMIUM':
      default:
        return 9999;
    }
  }

  tierMaxMembers(): number {
    const tier = this.user()?.subscriptionTier;
    switch (tier) {
      case 'STANDARD': return 50;
      case 'STARTER': return 250;
      case 'PRO': return 1000;
      case 'ENTERPRISE':
      case 'PREMIUM':
      default:
        return 9999;
    }
  }

  hasReachedStaffLimit(): boolean {
    const max = this.tierMaxStaff();
    return this.staff().length >= max;
  }

  maxStaffDisplay(): string {
    const max = this.tierMaxStaff();
    return max >= 9999 ? 'Unlimited' : String(max);
  }

  maxMembersDisplay(): string {
    const max = this.tierMaxMembers();
    return max >= 9999 ? 'Unlimited' : String(max);
  }

  maxBranchesDisplay(): string {
    const max = this.tierMaxBranches();
    return max >= 9999 ? 'Unlimited' : String(max);
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
    if (price <= 0) {
      return 'Please enter a valid package price greater than 0.';
    }
    if (sessions <= 0) {
      return 'Please specify at least 1 included PT session.';
    }
    if (sessions > 100) {
      return 'Included PT sessions cannot exceed 100 sessions per package.';
    }
    if (ptFee > price) {
      const derivedPerSession = Math.round((ptFee / sessions) * 100) / 100;
      return `Calculation Error: ${sessions} sessions @ ${this.money(derivedPerSession)} each = ${this.money(ptFee)}, which exceeds total package price of ${this.money(price)}. Total PT allocation cannot exceed the package price.`;
    }
    return '';
  }

  async savePlan(): Promise<void> {
    this.clearAllFieldErrors();
    let hasErr = false;
    if (!this.planDraft.name?.trim()) {
      this.setFieldError('planName', 'Package name is mandatory.');
      hasErr = true;
    }
    if (!this.planDraft.price || Number(this.planDraft.price) <= 0) {
      this.setFieldError('planPrice', 'Package price must be greater than 0.');
      hasErr = true;
    }
    if (this.planDraft.durationMode === 'days' && (!this.planDraft.durationDays || Number(this.planDraft.durationDays) < 1)) {
      this.setFieldError('planDurationDays', 'Duration in days must be at least 1.');
      hasErr = true;
    }
    const ptErr = this.planPtValidationError();
    if (ptErr) {
      this.setFieldError('planPtFee', ptErr);
      this.error.set(ptErr);
      hasErr = true;
    }
    if (hasErr) {
      this.error.set('Please fix the package form errors highlighted below.');
      this.scrollToFirstError('.plan-form');
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
      this.scrollToFirstError('.plan-form');
    } finally {
      this.busy.set(false);
    }
  }

  startNewPlan(): void { this.planDraft = { id: 0, name: '', description: '', price: 49, durationMode: 'months', durationMonths: 1, durationDays: 30, branchId: '', active: true, ptPackageType: 'NONE' as PtPackageType, includedPtSessions: 0, ptFeeIncluded: 0 }; this.editingPlan.set(true); }
  editPlan(plan: Plan): void { this.planDraft = { id: plan.id, name: plan.name, description: plan.description ?? '', price: plan.price, durationMode: plan.durationDays !== null ? 'days' : 'months', durationMonths: plan.durationMonths ?? 1, durationDays: plan.durationDays ?? 30, branchId: plan.branchId === null ? '' : String(plan.branchId), active: plan.active, ptPackageType: plan.ptPackageType ?? 'NONE', includedPtSessions: plan.includedPtSessions ?? 0, ptFeeIncluded: plan.ptFeeIncluded ?? 0 }; this.editingPlan.set(true); }

  async registerMember(): Promise<void> {
    this.clearAllFieldErrors();
    let hasErr = false;

    if (!this.memberDraft.fullName?.trim()) {
      this.setFieldError('memberFullName', 'Member full name is mandatory.');
      hasErr = true;
    }
    if (!this.memberDraft.email?.trim() || !this.memberDraft.email.includes('@')) {
      this.setFieldError('memberEmail', 'A valid email address is mandatory.');
      hasErr = true;
    }
    if (!this.memberDraft.planId || !this.selectedPlan()) {
      this.setFieldError('memberPlan', 'Please select a membership package.');
      hasErr = true;
    }
    if (this.user()?.role === 'OWNER' && !this.memberDraft.branchId) {
      this.setFieldError('memberBranch', 'Please assign an enrollment branch facility.');
      hasErr = true;
    }
    if (!this.memberDraft.startDate) {
      this.setFieldError('memberStartDate', 'Membership start date is mandatory.');
      hasErr = true;
    }
    if (this.memberDraft.paymentMethod !== 'CASH' && !this.memberDraft.transactionId?.trim()) {
      this.setFieldError('memberTransactionId', `Transaction ID / Reference is mandatory for ${this.memberDraft.paymentMethod} payments.`);
      hasErr = true;
    }
    if (!this.memberDraft.waiverAcknowledged) {
      this.setFieldError('memberWaiver', 'Health waiver and club safety terms acknowledgment is mandatory.');
      hasErr = true;
    }

    if (hasErr) {
      this.error.set('Please fix the required fields marked in red below.');
      this.scrollToFirstError('#member-registration-form');
      return;
    }
    this.busy.set(true);
    this.error.set('');
    try {
      const receiptImageData = this.receiptFile ? await this.readReceiptFile(this.receiptFile) : null;
      if (receiptImageData && !this.hasReceiptUploadAccess()) {
        this.error.set('Receipt photo upload is a Pro feature. Please upgrade your gym to Pro or Enterprise.');
        this.openUpgradeModal('receipt');
        return;
      }
      const result = await firstValueFrom(this.api.registerMembership({
        ...this.memberDraft,
        planId: Number(this.memberDraft.planId),
        branchId: this.memberDraft.branchId ? Number(this.memberDraft.branchId) : null,
        personalTrainerId: this.memberDraft.personalTrainerId ? Number(this.memberDraft.personalTrainerId) : null,
        discountPercent: Number(this.memberDraft.discountPercent),
        receiptImageData,
        paymentMethod: this.memberDraft.paymentMethod,
        transactionId: this.memberDraft.paymentMethod === 'CASH' ? null : this.memberDraft.transactionId.trim()
      }));
      const pinMsg = result.memberPin ? ` · Member Verification PIN: ${result.memberPin}` : '';
      this.notice.set(`${result.memberName} is now on the ${result.planName} package${pinMsg}.`);
      this.searchTerm = '';
      this.memberFilter.set('all');
      await this.refreshMembershipViews(0);
      this.navigate('members');
    } catch (error) {
      this.error.set(this.api.message(error, 'Could not create this membership.'));
      this.scrollToFirstError('#member-registration-form');
    } finally {
      this.busy.set(false);
    }
  }

  selectReceipt(event: Event): void {
    const input = event.currentTarget as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    this.error.set('');
    if (!this.hasReceiptUploadAccess()) {
      this.error.set('Receipt photo upload is a Pro feature. Please upgrade your gym to Pro or Enterprise.');
      this.openUpgradeModal('receipt');
      input.value = '';
      return;
    }
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
      startDate: this.renewalMinimumStartDate(member),
      paymentMethod: this.user()?.role === 'MEMBER' ? 'UPI' : 'CASH',
      transactionId: ''
    };
    this.renewalTarget.set(member);
  }

  closeRenewal(): void { this.renewalTarget.set(null); }

  async confirmRenewal(): Promise<void> {
    const member = this.renewalTarget();
    const plan = this.selectedRenewalPlan();
    if (!member || !plan || !this.renewalDraft.startDate) return;
    if (this.renewalDraft.paymentMethod !== 'CASH' && !this.renewalDraft.transactionId?.trim()) {
      this.error.set(`Please enter the Transaction ID / Reference for ${this.renewalDraft.paymentMethod} renewal payments.`);
      return;
    }
    this.busy.set(true);
    this.error.set('');
    try {
      await firstValueFrom(this.api.renewMembership(member.id, {
        planId: plan.id,
        branchId: this.renewalDraft.branchId ? Number(this.renewalDraft.branchId) : null,
        personalTrainerId: this.renewalDraft.personalTrainerId ? Number(this.renewalDraft.personalTrainerId) : null,
        discountPercent: this.user()?.role === 'MEMBER' ? 0 : Number(this.renewalDraft.discountPercent),
        startDate: this.renewalDraft.startDate,
        paymentMethod: this.renewalDraft.paymentMethod,
        transactionId: this.renewalDraft.paymentMethod === 'CASH' ? null : this.renewalDraft.transactionId.trim()
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
    this.clearAllFieldErrors();
    let hasErr = false;
    if (!this.staffDraft.fullName?.trim()) {
      this.setFieldError('staffName', 'Staff full name is mandatory.');
      hasErr = true;
    }
    if (!this.staffDraft.email?.trim() || !this.staffDraft.email.includes('@')) {
      this.setFieldError('staffEmail', 'A valid email address is mandatory.');
      hasErr = true;
    }
    if (!this.staffDraft.password || this.staffDraft.password.length < 8) {
      this.setFieldError('staffPassword', 'Temporary password must be at least 8 characters long.');
      hasErr = true;
    }
    if (!this.staffDraft.branchId) {
      this.setFieldError('staffBranch', 'Branch facility assignment is mandatory.');
      hasErr = true;
    }
    if (hasErr) {
      this.error.set('Please fix the required fields marked in red below.');
      this.scrollToFirstError('.team-form');
      return;
    }
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
      this.scrollToFirstError('.team-form');
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

  startNewBranch(): void {
    const tier = this.user()?.subscriptionTier;
    const maxBranches = this.tierMaxBranches();
    if (this.branches().length >= maxBranches && tier !== 'ENTERPRISE' && tier !== 'PREMIUM') {
      this.enterpriseBranchPromptOpen.set(true);
      return;
    }
    this.branchDraft = { id: 0, name: '', code: '', type: 'COMPANY_OWNED', address: '', phoneNumber: '', active: true };
    this.clearAllFieldErrors();
    this.editingBranch.set(true);
  }

  editBranch(branch: Branch): void {
    this.branchDraft = { id: branch.id, name: branch.name, code: branch.code, type: branch.type, address: branch.address ?? '', phoneNumber: branch.phoneNumber ?? '', active: branch.active };
    this.clearAllFieldErrors();
    this.editingBranch.set(true);
  }

  viewBranchInOverview(branchId: number): void { this.selectDashboardBranch(branchId); this.navigate('overview'); }

  async saveBranch(): Promise<void> {
    this.clearAllFieldErrors();
    let hasErr = false;
    if (!this.branchDraft.name?.trim()) {
      this.setFieldError('branchName', 'Branch name is mandatory.');
      hasErr = true;
    }
    if (!this.branchDraft.code?.trim()) {
      this.setFieldError('branchCode', 'Branch code is mandatory.');
      hasErr = true;
    }
    if (hasErr) {
      this.error.set('Please fix the required fields marked in red below.');
      this.scrollToFirstError('.branch-form');
      return;
    }
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
      this.scrollToFirstError('.branch-form');
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
  selectedUpgradePlan(): Plan | null { return this.activePlans().find((plan) => String(plan.id) === String(this.upgradeTargetPlanId())) ?? null; }
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
    if (member.status === 'UPGRADED') return 'Upgraded';
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
  canUpgradeMembership(member: Membership): boolean {
    return member.status === 'ACTIVE' && !this.isExpired(member.endDate) && !member.renewalStartDate;
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
  canSendRenewalReminder(member: Membership): boolean {
    const role = this.user()?.role;
    if (role !== 'OWNER' && role !== 'STAFF') return false;
    return member.status === 'ACTIVE' && !member.renewalStartDate && !this.isExpired(member.endDate);
  }
  async sendManualReminder(member: Membership): Promise<void> {
    this.busy.set(true);
    this.error.set('');
    try {
      const reminder = await firstValueFrom(this.api.sendRenewalReminder(member.id));
      this.notice.set(`Renewal reminder sent to ${reminder.recipientEmail || member.email} successfully.`);
      if (this.user()?.role === 'OWNER') {
        this.renewalReminders.set(await firstValueFrom(this.api.renewalReminders()));
      }
    } catch (err) {
      this.error.set(this.api.message(err, 'Failed to send renewal reminder.'));
    } finally {
      this.busy.set(false);
    }
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
    this.clearAllFieldErrors();
    let hasErr = false;
    if (!this.ptDraft.fullName?.trim()) {
      this.setFieldError('ptFullName', 'Trainer full name is mandatory.');
      hasErr = true;
    }
    if (!this.ptDraft.email?.trim() || !this.ptDraft.email.includes('@')) {
      this.setFieldError('ptEmail', 'A valid email address is mandatory.');
      hasErr = true;
    }
    if (this.ptDraft.id === 0 && (!this.ptDraft.password || this.ptDraft.password.length < 8)) {
      this.setFieldError('ptPassword', 'Temporary password must be at least 8 characters long.');
      hasErr = true;
    }
    const fee = Number(this.ptDraft.sessionFeePerHour);
    if (isNaN(fee) || fee < 0) {
      this.setFieldError('ptSessionFee', 'Session fee must be a valid non-negative number.');
      hasErr = true;
    }
    const comm = Number(this.ptDraft.commissionPercent);
    if (isNaN(comm) || comm < 0 || comm > 100) {
      this.setFieldError('ptCommission', 'Commission share must be between 0% and 100%.');
      hasErr = true;
    }
    if (hasErr) {
      this.error.set('Please fix the required fields marked in red below.');
      this.scrollToFirstError('.pt-editor-form');
      return;
    }
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
          sessionFeePerHour: fee,
          commissionPercent: comm
        }));
        this.notice.set('Trainer created successfully.');
      } else {
        await firstValueFrom(this.api.updatePt(this.ptDraft.id, {
          branchId: this.ptDraft.branchId ? Number(this.ptDraft.branchId) : null,
          specializations: this.ptDraft.specializations.trim(),
          bio: this.ptDraft.bio.trim(),
          sessionFeePerHour: fee,
          commissionPercent: comm
        }));
        this.notice.set('Trainer updated successfully.');
      }
      this.editingPt.set(false);
      await this.loadPtManagementData();
    } catch (error) {
      this.error.set(this.api.message(error, 'Could not save trainer profile.'));
      this.scrollToFirstError('.pt-editor-form');
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

    selectPlanFromPublic(tier: 'STANDARD' | 'STARTER' | 'PRO' | 'ENTERPRISE'): void {
    this.registerGymDraft.subscriptionTier = tier;
    this.authMode.set('register-gym');
    this.error.set('');
    if (typeof window !== 'undefined') {
      window.scrollTo({ top: 0, behavior: 'smooth' });
    }
  }

  scrollToPricing(): void {
    if (typeof document !== 'undefined') {
      document.getElementById('pricing')?.scrollIntoView({ behavior: 'smooth' });
    }
  }

  scrollToMatrix(): void {
    if (typeof document !== 'undefined') {
      document.getElementById('feature-matrix')?.scrollIntoView({ behavior: 'smooth' });
    }
  }

  downloadPlanComparisonPdf(): void {
    if (typeof window !== 'undefined') {
      this.pricingBrochureModalOpen.set(true);
      setTimeout(() => {
        window.print();
      }, 350);
    }
  }

  closeBrochureModal(): void {
    this.pricingBrochureModalOpen.set(false);
  }

  async downloadGymBackup(): Promise<void> {
    this.downloadingBackup.set(true);
    this.error.set('');
    try {
      const backup = await firstValueFrom(this.api.downloadTenantBackup());
      const orgCode = backup.organizationCode || 'GYM';
      const dateStr = new Date().toISOString().slice(0, 10);
      const jsonStr = JSON.stringify(backup, null, 2);
      const blob = new Blob([jsonStr], { type: 'application/json' });
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `gymflow-${orgCode.toLowerCase()}-backup-${dateStr}.json`;
      a.click();
      window.URL.revokeObjectURL(url);
      this.notice.set('Complete gym data backup archive downloaded successfully.');
    } catch (err) {
      this.error.set(this.api.message(err, 'Failed to download gym backup. Feature requires Pro or Enterprise subscription.'));
    } finally {
      this.downloadingBackup.set(false);
    }
  }

  openBulkImportModal(): void {
    this.bulkImportModalOpen.set(true);
    this.bulkImportCsvText = '';
    this.bulkImportResult.set(null);
    this.error.set('');
  }

  closeBulkImportModal(): void {
    this.bulkImportModalOpen.set(false);
  }

  downloadSampleImportCsv(): void {
    const csvContent = 'Full Name,Email,Phone,Package Name,Branch Code,Start Date,Payment Method\r\n' +
      'Vikram Malhotra,vikram@example.com,+919876543210,Strength,,2026-11-01,UPI\r\n' +
      'Ananya Sen,ananya@example.com,+919876543211,All access,,2026-11-01,CASH\r\n' +
      'Rahul Verma,rahul@example.com,+919876543212,Strength,,2026-11-05,CARD';
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'gymflow-member-import-template.csv';
    a.click();
    window.URL.revokeObjectURL(url);
  }

  handleCsvFileSelect(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files[0]) {
      const file = input.files[0];
      const reader = new FileReader();
      reader.onload = (e) => {
        this.bulkImportCsvText = (e.target?.result as string) || '';
      };
      reader.readAsText(file);
    }
  }

  async submitBulkImport(): Promise<void> {
    const raw = this.bulkImportCsvText.trim();
    if (!raw) {
      this.error.set('Please provide CSV data or select a valid CSV file.');
      return;
    }
    const lines = raw.split(/\r?\n/).filter(line => line.trim().length > 0);
    if (lines.length < 2) {
      this.error.set('CSV must contain a header row and at least one member data row.');
      return;
    }

    const startIndex = (lines[0].toLowerCase().includes('name') || lines[0].toLowerCase().includes('email')) ? 1 : 0;
    const members: BulkMemberImportRow[] = [];

    for (let i = startIndex; i < lines.length; i++) {
      const cols = lines[i].split(',').map(c => c.trim().replace(/^["']|["']$/g, ''));
      if (cols.length >= 2 && cols[0] && cols[1]) {
        members.push({
          fullName: cols[0],
          email: cols[1],
          phoneNumber: cols[2] || '',
          packageName: cols[3] || '',
          branchCode: cols[4] || '',
          startDate: cols[5] || '',
          paymentMethod: cols[6] || 'CASH'
        });
      }
    }

    if (members.length === 0) {
      this.error.set('No valid member rows could be parsed from the CSV text.');
      return;
    }

    this.bulkImportLoading.set(true);
    this.error.set('');
    try {
      const result = await firstValueFrom(this.api.bulkImportMembers(members));
      this.bulkImportResult.set(result);
      if (result.importedCount > 0) {
        this.notice.set(`Successfully imported ${result.importedCount} members into your gym!`);
        await this.refreshMembershipViews();
      }
    } catch (err) {
      this.error.set(this.api.message(err, 'Bulk member import failed. Please verify CSV format and plan permissions.'));
    } finally {
      this.bulkImportLoading.set(false);
    }
  }

  printMembershipReceipt(m: Membership): void {
    if (typeof window === 'undefined') return;
    const win = window.open('', '_blank', 'width=750,height=850');
    if (!win) return;
    const gymName = this.user()?.organizationName || 'GymFlow Club';
    const gymLogo = this.user()?.organizationLogo;
    const dateFormatted = this.dateLabel(m.startDate);
    const expiryFormatted = this.dateLabel(m.endDate);
    const amountStr = this.money(m.amountPaid || m.listPrice);
    const planName = m.planName || 'Standard Membership';
    const invoiceNum = `INV-${m.id.toString().padStart(6, '0')}`;

    win.document.write(`
      <!DOCTYPE html>
      <html>
      <head>
        <title>Tax Invoice Receipt - ${invoiceNum}</title>
        <style>
          body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; color: #1e293b; padding: 40px; margin: 0; background: #fff; }
          .receipt-container { max-width: 600px; margin: 0 auto; border: 1px solid #e2e8f0; border-radius: 12px; padding: 32px; box-shadow: 0 4px 12px rgba(0,0,0,0.05); }
          .receipt-header { display: flex; justify-content: space-between; align-items: center; border-bottom: 2px solid #0f172a; padding-bottom: 20px; margin-bottom: 24px; }
          .gym-title { font-size: 24px; font-weight: 800; color: #0f172a; margin: 0; }
          .receipt-kicker { font-size: 11px; text-transform: uppercase; letter-spacing: 0.08em; color: #64748b; font-weight: 700; }
          .receipt-logo { max-height: 48px; max-width: 140px; object-fit: contain; }
          .invoice-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 24px; font-size: 13px; line-height: 1.5; }
          .line-item-table { width: 100%; border-collapse: collapse; margin-bottom: 24px; font-size: 13.5px; }
          .line-item-table th { background: #f8fafc; text-align: left; padding: 10px 12px; border-bottom: 1px solid #cbd5e1; font-size: 11.5px; text-transform: uppercase; color: #475569; }
          .line-item-table td { padding: 12px; border-bottom: 1px solid #e2e8f0; }
          .total-box { display: flex; justify-content: flex-end; margin-top: 16px; padding-top: 16px; border-top: 2px solid #0f172a; font-size: 18px; font-weight: 800; }
          .receipt-footer { margin-top: 32px; text-align: center; font-size: 11.5px; color: #94a3b8; border-top: 1px solid #e2e8f0; padding-top: 16px; }
          @media print {
            body { padding: 0; }
            .receipt-container { border: none; box-shadow: none; padding: 0; }
          }
        </style>
      </head>
      <body>
        <div class="receipt-container">
          <div class="receipt-header">
            <div>
              <div class="receipt-kicker">OFFICIAL TAX INVOICE &amp; PAYMENT RECEIPT</div>
              <h1 class="gym-title">${gymName}</h1>
              <div style="font-size: 12px; color: #64748b; margin-top: 4px;">Branch: ${m.branchName || 'Main Facility'}</div>
            </div>
            ${gymLogo ? `<img src="${gymLogo}" class="receipt-logo" alt="Gym Logo">` : `<div style="font-size: 28px;">⚡</div>`}
          </div>
          <div class="invoice-grid">
            <div>
              <strong style="color: #475569;">Billed To:</strong><br>
              <strong>${m.memberName}</strong><br>
              ${m.email}<br>
              ${m.phoneNumber || 'N/A'}
            </div>
            <div style="text-align: right;">
              <strong style="color: #475569;">Invoice No:</strong> <strong>${invoiceNum}</strong><br>
              <strong>Date of Issue:</strong> ${dateFormatted}<br>
              <strong>Payment Mode:</strong> ${m.paymentMethod || 'CASH / POS'}<br>
              <strong>Status:</strong> <span style="color: #16a34a; font-weight: 700;">PAID IN FULL</span>
            </div>
          </div>
          <table class="line-item-table">
            <thead>
              <tr>
                <th>Description</th>
                <th>Validity Period</th>
                <th style="text-align: right;">Amount (INR)</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td><strong>${planName}</strong><br><small style="color: #64748b;">Club Facility &amp; Fitness Access</small></td>
                <td>${dateFormatted} to ${expiryFormatted}</td>
                <td style="text-align: right;"><strong>${amountStr}</strong></td>
              </tr>
            </tbody>
          </table>
          <div class="total-box">
            <div>Total Paid: &nbsp; <span style="color: #0f172a;">${amountStr}</span></div>
          </div>
          <div class="receipt-footer">
            Thank you for being a valued member of ${gymName}. This is a computer-generated tax receipt.
          </div>
        </div>
        <script>
          window.onload = function() { window.print(); };
        </script>
      </body>
      </html>
    `);
    win.document.close();
  }

  openMemberRenewalModal(m?: Membership): void {
    if (m) {
      this.memberSelectedPlanId.set(m.planId);
    } else if (this.plans().length > 0) {
      this.memberSelectedPlanId.set(this.plans()[0].id);
    }
    this.memberRenewalModalOpen.set(true);
  }

  closeMemberRenewalModal(): void {
    this.memberRenewalModalOpen.set(false);
  }

  openMemberFreezeModal(m?: Membership): void {
    this.memberFreezeStartDate = this.todayDate();
    this.memberFreezeEndDate = this.addDays(this.todayDate(), 14);
    this.memberFreezeReason = 'Temporary personal pause / travel';
    this.memberFreezeModalOpen.set(true);
  }

  closeMemberFreezeModal(): void {
    this.memberFreezeModalOpen.set(false);
  }

  async submitMemberFreeze(membershipId: number): Promise<void> {
    if (!this.memberFreezeStartDate || !this.memberFreezeEndDate) {
      this.error.set('Please provide both freeze start and end dates.');
      return;
    }
    this.busy.set(true);
    this.error.set('');
    try {
      await firstValueFrom(this.api.freezeMembership(membershipId, {
        startDate: this.memberFreezeStartDate,
        endDate: this.memberFreezeEndDate,
        reason: this.memberFreezeReason.trim() || 'Member self-service freeze'
      }));
      this.notice.set('Your membership has been temporarily frozen. Enjoy your pause!');
      this.closeMemberFreezeModal();
      await this.refreshMembershipViews();
    } catch (err) {
      this.error.set(this.api.message(err, 'Failed to freeze membership. Please verify freeze dates or contact club staff.'));
    } finally {
      this.busy.set(false);
    }
  }

  async submitMemberRenewal(membershipId: number): Promise<void> {
    const planId = this.memberSelectedPlanId();
    if (!planId) {
      this.error.set('Please select a membership package to renew.');
      return;
    }
    this.busy.set(true);
    this.error.set('');
    try {
      await firstValueFrom(this.api.renewMembership(membershipId, {
        planId: planId,
        branchId: null,
        personalTrainerId: null,
        discountPercent: 0,
        startDate: this.todayDate(),
        paymentMethod: 'ONLINE_MEMBER_PORTAL'
      }));
      this.notice.set('Your membership renewal has been processed successfully!');
      this.closeMemberRenewalModal();
      await this.refreshMembershipViews();
    } catch (err) {
      this.error.set(this.api.message(err, 'Renewal failed. Please check with your gym front desk.'));
    } finally {
      this.busy.set(false);
    }
  }


  private readStoredUser(): User | null {
    try { const value = sessionStorage.getItem('gym.user'); return value ? JSON.parse(value) as User : null; }
    catch { return null; }
  }


  loadSaasUpgradeQuote(targetTier?: string): void {
    const tier = targetTier || this.upgradeTargetTier();
    this.saasUpgradeQuoteLoading.set(true);
    this.api.quoteSaasUpgrade(tier).subscribe({
      next: (quote) => {
        this.saasUpgradeQuote.set(quote);
        this.saasUpgradeQuoteLoading.set(false);
      },
      error: () => {
        this.saasUpgradeQuote.set(null);
        this.saasUpgradeQuoteLoading.set(false);
      }
    });
  }

  openMemberUpgradeModal(membership: Membership): void {
    this.upgradingMembership.set(membership);
    this.upgradeTargetPlanId.set(null);
    this.upgradeQuote.set(null);
    this.upgradeQuoteError.set('');
    this.upgradeDraft.topUpPaid = 0;
    this.upgradeDraft.paymentMethod = this.user()?.role === 'MEMBER' ? 'UPI' : 'CASH';
    this.upgradeDraft.transactionId = '';
    this.upgradeDraft.personalTrainerId = membership.personalTrainerId ?? null;
    this.clearAllFieldErrors();
  }

  closeMemberUpgradeModal(): void {
    this.upgradingMembership.set(null);
    this.upgradeTargetPlanId.set(null);
    this.upgradeQuote.set(null);
    this.upgradeQuoteError.set('');
    this.clearAllFieldErrors();
  }

  onMemberUpgradePlanChange(planId: any): void {
    const id = planId ? Number(planId) : null;
    this.upgradeTargetPlanId.set(id);
    this.upgradeQuote.set(null);
    this.upgradeQuoteError.set('');
    this.clearFieldError('upgradePlan');
    const current = this.upgradingMembership();
    if (!id || !current) return;
    this.upgradeQuoteLoading.set(true);
    this.api.quoteMembershipUpgrade(current.id, id).subscribe({
      next: (q) => {
        this.upgradeQuote.set(q);
        this.upgradeDraft.topUpPaid = q.topUpDue;
        this.upgradeQuoteLoading.set(false);
      },
      error: (err) => {
        this.upgradeQuoteError.set(this.api.message(err, 'Could not calculate upgrade cost.'));
        this.upgradeQuoteLoading.set(false);
      }
    });
  }

  async confirmMembershipUpgrade(): Promise<void> {
    this.clearAllFieldErrors();
    const current = this.upgradingMembership();
    const planId = this.upgradeTargetPlanId();
    if (!current) return;
    if (!planId) {
      this.setFieldError('upgradePlan', 'Please select a package to upgrade to');
      this.scrollToFirstError('.upgrade-modal');
      return;
    }
    const quote = this.upgradeQuote();
    if (!quote) {
      this.upgradeQuoteError.set('Please select a valid package and wait for calculation.');
      return;
    }
    const pm = this.upgradeDraft.paymentMethod;
    if (!['CASH', 'OFFLINE'].includes(pm?.toUpperCase()) && !this.upgradeDraft.transactionId?.trim()) {
      this.setFieldError('upgradeTransactionId', 'Transaction ID is required for online payments');
      this.scrollToFirstError('.upgrade-modal');
      return;
    }

    this.busy.set(true);
    try {
      await firstValueFrom(this.api.upgradeMembership(current.id, {
        newPlanId: planId,
        personalTrainerId: this.upgradeDraft.personalTrainerId ? Number(this.upgradeDraft.personalTrainerId) : null,
        topUpPaid: Number(this.upgradeDraft.topUpPaid),
        paymentMethod: pm,
        transactionId: this.upgradeDraft.transactionId?.trim() || null
      }));
      this.notice.set(`Successfully upgraded ${current.memberName}'s membership to ${quote.newPlanName}!`);
      this.closeMemberUpgradeModal();
      await this.refreshMembershipViews();
    } catch (err: any) {
      this.upgradeQuoteError.set(this.api.message(err, 'Failed to upgrade membership.'));
    } finally {
      this.busy.set(false);
    }
  }

}
