import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { App } from './app';
import { ActionHistory, GymApiService, Membership, SubscriptionStatus } from './gym-api.service';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideHttpClient()],
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  }, 25000);

  it('should show the public home before authentication', async () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    await fixture.whenStable();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.public-hero h1')?.textContent).toContain('Re-member Flow');
    expect(compiled.querySelector('.public-platform')).toBeTruthy();
    expect(compiled.querySelector('.login-box')).toBeNull();
  });

  it('opens sign-in from the public home', async () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    await fixture.whenStable();
    const compiled = fixture.nativeElement as HTMLElement;
    compiled.querySelector<HTMLButtonElement>('.public-signin')?.click();
    fixture.detectChanges();
    expect(compiled.querySelector('.login-box h2')?.textContent).toContain('Welcome back.');
  });

  it('clears stale errors when returning to sign-in', () => {
    const app = TestBed.createComponent(App).componentInstance;
    app.error.set('The security token is missing or expired.');
    app.notice.set('Old session notice.');

    app.showLogin();

    expect(app.authMode()).toBe('login');
    expect(app.error()).toBe('');
    expect(app.notice()).toBe('');
  });

  it('allows renewing a cancelled membership even when its original start date is in the future', () => {
    const app = TestBed.createComponent(App).componentInstance;
    const startDate = app.addDays(app.todayDate(), 10);
    const cancelledMembership = membership({ status: 'CANCELLED', startDate, endDate: app.addDays(startDate, 30) });

    expect(app.canRenewMembership(cancelledMembership)).toBe(true);
  });

  it('does not allow renewal when another active membership exists for the same member', () => {
    const app = TestBed.createComponent(App).componentInstance;
    const startDate = app.addDays(app.todayDate(), 10);
    const cancelledMembership = membership({ status: 'CANCELLED', startDate, endDate: app.addDays(startDate, 30) });
    app.memberships.set([cancelledMembership, membership({
      id: 2,
      status: 'ACTIVE',
      startDate: app.todayDate(),
      endDate: app.addDays(app.todayDate(), 30)
    })]);

    expect(app.canRenewMembership(cancelledMembership)).toBe(false);
  });

  it('does not offer renewal from a cancelled term while a scheduled term remains active', () => {
    const app = TestBed.createComponent(App).componentInstance;
    const today = app.todayDate();
    const cancelledMembership = membership({
      status: 'CANCELLED',
      startDate: app.addDays(today, 5),
      endDate: app.addDays(today, 35)
    });
    app.memberships.set([cancelledMembership, membership({
      id: 2,
      status: 'ACTIVE',
      startDate: today,
      endDate: app.addDays(today, 30),
      renewalStartDate: app.addDays(today, 31)
    })]);

    expect(app.canRenewMembership(cancelledMembership)).toBe(false);
  });

  it('uses the latest in-term membership and sorts history by last action time', () => {
    const app = TestBed.createComponent(App).componentInstance;
    const today = app.todayDate();
    const older = membership({
      id: 1,
      startDate: app.addDays(today, -2),
      endDate: app.addDays(today, 3),
      lastActivityAt: `${today}T12:00:00Z`
    });
    const current = membership({
      id: 2,
      startDate: app.addDays(today, -1),
      endDate: app.addDays(today, 4),
      lastActivityAt: `${today}T08:00:00Z`
    });
    const cancelledFuture = membership({
      id: 3,
      status: 'CANCELLED',
      startDate: app.addDays(today, 5),
      endDate: app.addDays(today, 10),
      lastActivityAt: `${today}T10:00:00Z`
    });
    const group = {
      memberId: 1,
      memberName: 'Test Member',
      email: 'member@example.com',
      phoneNumber: null,
      terms: [older, current, cancelledFuture]
    };

    expect(app.currentTerm(group).id).toBe(current.id);
    expect(app.historyTerms(group).map((term) => term.id)).toEqual([1, 3, 2]);
    expect(app.historyTermStatus(group, older)).toBe('Superseded');
  });

  it('distinguishes scheduled, active, cancelled, and early-ended freezes', () => {
    const app = TestBed.createComponent(App).componentInstance;
    const today = app.todayDate();
    const future = {
      id: 1, startDate: app.addDays(today, 2), endDate: app.addDays(today, 4),
      reason: 'Trip', createdBy: 'owner@example.com', createdAt: today,
      cancelledAt: null, endedEarlyAt: null
    };
    const earlyEnded = {
      id: 2, startDate: app.addDays(today, -2), endDate: app.addDays(today, 5),
      reason: 'Recovery', createdBy: 'owner@example.com', createdAt: today,
      cancelledAt: null, endedEarlyAt: today
    };
    const active = {
      id: 4, startDate: today, endDate: app.addDays(today, 2),
      reason: 'Pause', createdBy: 'owner@example.com', createdAt: today,
      cancelledAt: null, endedEarlyAt: null
    };
    const cancelled = {
      id: 3, startDate: app.addDays(today, 1), endDate: app.addDays(today, 3),
      reason: 'Cancelled trip', createdBy: 'owner@example.com', createdAt: today,
      cancelledAt: new Date().toISOString(), endedEarlyAt: null
    };

    expect(app.freezeStatus(future)).toBe('Scheduled');
    expect(app.canCancelFreeze(future)).toBe(true);
    expect(app.freezeStatus(active)).toBe('In progress');
    expect(app.canEndFreezeEarly(active)).toBe(true);
    expect(app.freezeStatus(earlyEnded)).toBe(`Ended early on ${app.dateLabel(today)}`);
    expect(app.canEndFreezeEarly(earlyEnded)).toBe(false);
    expect(app.freezeStatus(cancelled)).toBe('Cancelled before start');
    expect(app.canCancelFreeze(cancelled)).toBe(false);
  });

  it('does not allow renewing a membership that already has a renewal scheduled', () => {
    const app = TestBed.createComponent(App).componentInstance;
    const cancelledMembership = membership({
      status: 'CANCELLED',
      startDate: app.addDays(app.todayDate(), 10),
      endDate: app.addDays(app.todayDate(), 40),
      renewalStartDate: app.addDays(app.todayDate(), 41)
    });

    expect(app.canRenewMembership(cancelledMembership)).toBe(false);
  });

  it('filters overview metrics and recent members by the selected branch', () => {
    const app = TestBed.createComponent(App).componentInstance;
    app.dashboard.set({
      revenueThisMonth: 300,
      revenueLastMonth: 250,
      activeMembers: 3,
      totalMembers: 5,
      expiringWithinSevenDays: 1,
      expiringMembers: [],
      branches: [
        {
          branchId: 1,
          branchName: 'North',
          branchCode: 'NORTH',
          branchType: 'COMPANY_OWNED',
          revenueThisMonth: 100,
          revenueLastMonth: 80,
          activeMembers: 1,
          totalMembers: 2,
          expiringWithinSevenDays: 0,
          expiringMembers: []
        },
        {
          branchId: 2,
          branchName: 'South',
          branchCode: 'SOUTH',
          branchType: 'FRANCHISE',
          revenueThisMonth: 200,
          revenueLastMonth: 170,
          activeMembers: 2,
          totalMembers: 3,
          expiringWithinSevenDays: 1,
          expiringMembers: []
        }
      ]
    });
    app.overviewRecentMemberships.set([
      membership({ id: 1, branchId: 1, branchName: 'North' }),
      membership({ id: 2, branchId: 2, branchName: 'South' })
    ]);

    app.selectDashboardBranch(2);

    expect(app.dashboardMetrics()?.revenueThisMonth).toBe(200);
    expect(app.dashboardMembers().map((row) => row.id)).toEqual([2]);
    expect(app.revenueTrendLabel()).toBe('↗ 18%');
    app.selectDashboardBranch('all');
    app.dashboard.update((dashboard) => dashboard ? { ...dashboard, revenueLastMonth: 0 } : dashboard);
    expect(app.revenueTrendLabel()).toBe('NEW');
  });

  it('renders readable audit field changes without losing names or reasons', () => {
    const app = TestBed.createComponent(App).componentInstance;
    const entry: ActionHistory = {
      id: 1,
      actorId: 1,
      actorName: 'Gym Owner',
      actorEmail: 'owner@gym.local',
      action: 'MEMBERSHIP_FROZEN',
      entityType: 'MEMBERSHIP',
      entityId: 12,
      reason: 'Test for Freeze',
      beforeData: JSON.stringify({ memberName: 'ANSARI AMAAN', status: 'ACTIVE', frozen: false }),
      afterData: JSON.stringify({ memberName: 'ANSARI AMAAN', status: 'ACTIVE', frozen: true }),
      occurredAt: new Date().toISOString()
    };

    expect(app.auditActionLabel(entry.action)).toBe('Membership frozen');
    expect(app.auditChanges(entry)).toEqual([
      { field: 'Frozen', before: 'No', after: 'Yes' }
    ]);
  });

  it('keeps zero revenue bars visually empty', () => {
    const app = TestBed.createComponent(App).componentInstance;
    app.dashboard.set({
      revenueThisMonth: 0,
      revenueLastMonth: 0,
      activeMembers: 0,
      totalMembers: 0,
      expiringWithinSevenDays: 0,
      expiringMembers: [],
      branches: []
    });

    expect(app.barHeight(0)).toBe(0);
  });

  it('correctly gates premium features for STANDARD vs PREMIUM gyms', () => {
    const app = TestBed.createComponent(App).componentInstance;

    // 1. When user belongs to a STANDARD gym
    app.user.set({
      id: 1,
      fullName: 'Standard Owner',
      email: 'standard@gym.local',
      phoneNumber: null,
      role: 'OWNER',
      branchId: 1,
      branchName: 'Main Branch',
      organizationId: 2,
      organizationName: 'Standard Gym',
      subscriptionTier: 'STANDARD',
      enabledFeatures: ['CORE_MANAGEMENT']
    });

    expect(app.isPremium()).toBe(false);
    expect(app.hasPtAccess()).toBe(false);
    expect(app.hasHistoryAccess()).toBe(false);

    // Navigating to pt-management should be intercepted and open the upgrade modal
    app.view.set('overview');
    app.navigate('pt-management');
    expect(app.view()).toBe('overview');
    expect(app.upgradeModalOpen()).toBe(true);
    expect(app.upgradeFeatureTarget()).toBe('pt');

    app.closeUpgradeModal();
    expect(app.upgradeModalOpen()).toBe(false);

    // Navigating to history should also open the upgrade modal with 'history' target
    app.navigate('history');
    expect(app.view()).toBe('overview');
    expect(app.upgradeModalOpen()).toBe(true);
    expect(app.upgradeFeatureTarget()).toBe('history');

    // 2. When user belongs to a PREMIUM PRO gym
    app.user.set({
      id: 1,
      fullName: 'Premium Owner',
      email: 'premium@gym.local',
      phoneNumber: null,
      role: 'OWNER',
      branchId: 1,
      branchName: 'Main Branch',
      organizationId: 1,
      organizationName: 'Form Premium Gym',
      subscriptionTier: 'PREMIUM',
      enabledFeatures: ['CORE_MANAGEMENT', 'PERSONAL_TRAINERS', 'ACTION_HISTORY']
    });

    expect(app.isPremium()).toBe(true);
    expect(app.hasPtAccess()).toBe(true);
    expect(app.hasHistoryAccess()).toBe(true);

    // Navigating to pt-management now succeeds without opening modal
    app.closeUpgradeModal();
    app.navigate('pt-management');
    expect(app.view()).toBe('pt-management');
    expect(app.upgradeModalOpen()).toBe(false);

    // Navigating to history now succeeds
    app.navigate('history');
    expect(app.view()).toBe('history');
    expect(app.upgradeModalOpen()).toBe(false);
  });

  it('upgrades subscription tier and unlocks premium access', async () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    const api = TestBed.inject(GymApiService);

    const mockSubStatus: SubscriptionStatus = {
      organizationId: 2,
      organizationName: 'Standard Gym',
      organizationCode: 'STD',
      subscriptionTier: 'PREMIUM',
      subscriptionStatus: 'ACTIVE',
      expiresAt: null,
      ptEnabled: true,
      historyEnabled: true,
      enabledFeatures: ['CORE_MANAGEMENT', 'PERSONAL_TRAINERS', 'ACTION_HISTORY']
    };

    vi.spyOn(api, 'redeemActivationCode').mockReturnValue(of({
      ...mockSubStatus,
      daysRemaining: 365
    }));

    app.user.set({
      id: 2,
      fullName: 'Standard Owner',
      email: 'owner@standard.local',
      phoneNumber: null,
      role: 'OWNER',
      branchId: 1,
      branchName: 'Main Branch',
      organizationId: 2,
      organizationName: 'Standard Gym',
      subscriptionTier: 'STANDARD',
      enabledFeatures: ['CORE_MANAGEMENT']
    });

    app.openUpgradeModal('pt');
    expect(app.upgradeModalOpen()).toBe(true);
    expect(app.upgradeFeatureTarget()).toBe('pt');

    app.redeemCodeInput.set('GF-PRO-TEST123');
    await app.submitRedeemCode();

    expect(api.redeemActivationCode).toHaveBeenCalledWith('GF-PRO-TEST123');
    expect(app.user()?.subscriptionTier).toBe('PREMIUM');
    expect(app.isPremium()).toBe(true);
    expect(app.upgradeModalOpen()).toBe(false);
    expect(app.view()).toBe('pt-management');
  });

  it('submits B2B PRO upgrade request and updates notice', async () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    const api = TestBed.inject(GymApiService);

    const mockUpgradeReq = {
      id: 101,
      organizationId: 2,
      organizationName: 'Standard Gym',
      organizationCode: 'STDFIT',
      requesterEmail: 'owner@standard.local',
      requesterName: 'Standard Owner',
      requesterPhone: null,
      targetTier: 'PREMIUM' as const,
      durationDays: 365,
      billingPlanName: 'Annual PRO (365 Days)',
      paymentMethod: 'BANK_WIRE',
      gstNumber: '29ABCDE1234F1Z5',
      billingAddress: 'Bangalore',
      notes: 'Transfer ref 9988',
      status: 'PENDING' as const,
      issuedActivationCode: null,
      reviewedBy: null,
      reviewedAt: null,
      rejectionReason: null,
      createdAt: new Date().toISOString()
    };

    vi.spyOn(api, 'requestUpgrade').mockReturnValue(of(mockUpgradeReq));
    vi.spyOn(api, 'myUpgradeRequests').mockReturnValue(of([mockUpgradeReq]));

    app.user.set({
      id: 2,
      fullName: 'Standard Owner',
      email: 'owner@standard.local',
      phoneNumber: null,
      role: 'OWNER',
      branchId: 1,
      branchName: 'Main Branch',
      organizationId: 2,
      organizationName: 'Standard Gym',
      subscriptionTier: 'STANDARD',
      enabledFeatures: ['CORE_MANAGEMENT']
    });

    app.setUpgradeBillingPlan('ANNUAL');
    app.setUpgradePaymentMethod('BANK_WIRE');
    app.upgradeGstNumber.set('29ABCDE1234F1Z5');
    app.upgradeBillingAddress.set('Bangalore');
    app.upgradeNotes.set('Transfer ref 9988');

    await app.submitUpgradeRequest();

    expect(api.requestUpgrade).toHaveBeenCalledWith({
      targetTier: 'PRO',
      billingPlan: 'Annual Pro (365 Days)',
      durationDays: 365,
      paymentMethod: 'BANK_WIRE',
      gstNumber: '29ABCDE1234F1Z5',
      billingAddress: 'Bangalore',
      notes: 'Transfer ref 9988'
    });
    expect(app.notice()).toContain('Your Pro upgrade request has been submitted');
    expect(app.myUpgradeRequests().length).toBe(1);
    expect(app.pendingMyUpgradeRequest()?.id).toBe(101);
  });

  it('switches tabs and auto-fills redeem code from deep-link hash', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;

    app.openUpgradeModal('general');
    expect(app.upgradeModalTab()).toBe('request');

    app.setUpgradeModalTab('redeem');
    expect(app.upgradeModalTab()).toBe('redeem');

    // Simulate #redeem?code=GF-PRO-ABCD-1234
    try {
      window.location.hash = '#redeem?code=GF-PRO-ABCD-1234';
      app.checkUrlHash();
      expect(app.redeemCodeInput()).toBe('GF-PRO-ABCD-1234');
      expect(app.upgradeModalTab()).toBe('redeem');
      expect(app.upgradeModalOpen()).toBe(true);
    } finally {
      window.location.hash = '';
    }
  });

  it('opens legal modal with specific tab and closes on escape key', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;

    expect(app.legalModalOpen()).toBe(false);

    app.openLegal('privacy');
    expect(app.legalModalOpen()).toBe(true);
    expect(app.legalModalTab()).toBe('privacy');
    expect(app.activeLegalDoc().title).toContain('Privacy Policy');

    app.onEscapePress();
    expect(app.legalModalOpen()).toBe(false);
  });

  it('filters legal sections by search query', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;

    app.openLegal('terms');
    expect(app.filteredLegalSections().length).toBeGreaterThan(0);

    app.legalSearchQuery.set('rhabdomyolysis');
    const filtered = app.filteredLegalSections();
    expect(filtered.length).toBe(1);
    expect(filtered[0].id).toBe('physical-activity-waiver');

    app.legalSearchQuery.set('xyznonexistentterm');
    expect(app.filteredLegalSections().length).toBe(0);
  });

  it('blocks gym registration if terms and privacy policy are not accepted', async () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;

    app.registerGymDraft = {
      fullName: 'Owner Test',
      email: 'owner@testgym.com',
      phoneNumber: '+1234567890',
      password: 'SecurePassword123!',
      organizationName: 'Test Gym Club',
      organizationCode: 'TESTGYM',
      branchName: 'Main Branch',
      branchAddress: '123 Fitness St',
      subscriptionTier: 'STANDARD',
      acceptTerms: false
    };

    await app.registerGym();
    expect(app.error()).toContain('Please review and accept the Terms of Use');
  });

  it('blocks member registration if health waiver is not acknowledged', async () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;

    app.plans.set([{
      id: 1,
      name: 'Monthly Plan',
      description: 'Standard plan',
      price: 50,
      durationDays: 30,
      durationMonths: null,
      branchId: null,
      branchName: null,
      active: true,
      usageCount: 0,
      ptPackageType: 'NONE',
      includedPtSessions: 0,
      ptFeeIncluded: 0
    }]);

    app.memberDraft = {
      fullName: 'New Member',
      email: 'member@test.com',
      phoneNumber: '+1234567890',
      password: 'Password123!',
      planId: '1',
      branchId: '1',
      personalTrainerId: '',
      discountPercent: 0,
      startDate: app.todayDate(),
      waiverAcknowledged: false,
      paymentMethod: 'CASH',
      transactionId: ''
    };

    await app.registerMember();
    expect(app.hasFieldError('memberWaiver')).toBe(true);
    expect(app.error()).toContain('Please fix the required fields marked in red below');
  });

  it('dismisses cookie consent and saves to localStorage', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;

    app.dismissCookieConsent();
    expect(app.cookieConsentDismissed()).toBe(true);
    expect(localStorage.getItem('gym_cookie_consent_accepted')).toBe('true');
  });

  function membership(overrides: Partial<Membership> = {}): Membership {
    const today = new Date().toISOString().slice(0, 10);
    return {
      id: 1,
      memberName: 'Test Member',
      email: 'member@example.com',
      phoneNumber: null,
      planId: 1,
      planName: 'Monthly',
      branchId: 1,
      branchName: 'Main branch',
      startDate: today,
      endDate: today,
      listPrice: 50,
      discountPercent: 0,
      amountPaid: 50,
      status: 'ACTIVE',
      renewalStartDate: null,
      cancelledAt: null,
      lastActivityAt: today,
      planDurationMonths: 1,
      planDurationDays: null,
      frozen: false,
      freezes: [],
      createdAt: today,
      ...overrides
    };
  }
});
