import { Component, EventEmitter, Input, Output, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
  ActivationCode,
  PlatformGymDetail,
  PlatformMetrics,
  PlatformOrganization,
  UpgradeRequest
} from '../../core/models';
import { dateLabel } from '../../core/utils/formatters';
import { GymApiService } from '../../gym-api.service';
import { firstValueFrom, timeout } from 'rxjs';

@Component({
  selector: 'app-platform-admin',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './platform-admin.component.html',
  styleUrl: './platform-admin.component.scss'
})
export class PlatformAdminComponent {
  private readonly api = inject(GymApiService);

  @Input() busy = false;
  @Input() platformMetrics: PlatformMetrics | null = null;
  @Input() platformOrganizations: PlatformOrganization[] = [];
  @Input() platformCodes: ActivationCode[] = [];
  @Input() platformUpgradeRequests: UpgradeRequest[] = [];
  @Input() filteredPlatformUpgradeRequests: UpgradeRequest[] = [];
  @Input() pendingUpgradeRequestsCount = 0;
  @Input() copiedPlatformKey = '';
  @Input() copiedCode: string | null = null;
  @Input() directUpgradingId: number | null = null;
  @Input() issuingKeyId: number | null = null;
  @Input() platformTab: 'metrics' | 'organizations' | 'codes' | 'upgrade-requests' | string = 'metrics';
  @Input() platformUpgradeRequestFilter = 'ALL';
  @Input() platformUpgradeSearch = '';
  @Input() platformUpgradeViewMode: 'cards' | 'table' = 'cards';
  @Input() platformOrgSearch = '';

  @Output() openGenerateCodeModal = new EventEmitter<void>();
  @Output() loadPlatformData = new EventEmitter<void>();
  @Output() updateOrgTier = new EventEmitter<{ id: number; tier: 'STANDARD' | 'STARTER' | 'PRO' | 'ENTERPRISE' | 'PREMIUM' | string }>();
  @Output() extendOrgSubscription = new EventEmitter<{ id: number; days: number }>();
  @Output() toggleOrgSuspension = new EventEmitter<PlatformOrganization>();
  @Output() copyActivationCode = new EventEmitter<string>();
  @Output() copyPlatformKey = new EventEmitter<string>();
  @Output() revokeLicenseKey = new EventEmitter<number>();
  @Output() setPlatformTab = new EventEmitter<'metrics' | 'organizations' | 'codes' | 'upgrade-requests'>();
  @Output() setPlatformUpgradeFilter = new EventEmitter<string>();
  @Output() setPlatformUpgradeSearch = new EventEmitter<string>();
  @Output() setPlatformUpgradeViewMode = new EventEmitter<'cards' | 'table'>();
  @Output() platformOrgSearchChange = new EventEmitter<string>();
  @Output() approveRequestWithKey = new EventEmitter<UpgradeRequest>();
  @Output() approveRequestDirect = new EventEmitter<UpgradeRequest>();
  @Output() openRejectModal = new EventEmitter<UpgradeRequest>();

  // Directory filter state
  orgFilter: 'ALL' | 'HEALTHY' | 'MODERATE' | 'AT_RISK' | 'EXPIRING' | 'UPSELL' = 'ALL';

  // 360 Deep-dive modal state
  selectedOrgForDeepDive: PlatformGymDetail | null = null;
  loadingDeepDive = false;
  deepDiveError = '';
  deepDiveTab: 'decision' | 'facilities' | 'plans' | 'pt' | 'logs' | 'financials' = 'decision';

  setTab(tab: 'metrics' | 'organizations' | 'codes' | 'upgrade-requests'): void {
    this.setPlatformTab.emit(tab);
  }

  onOrgSearchChange(val: string): void {
    this.platformOrgSearchChange.emit(val);
    this.loadPlatformData.emit();
  }

  onUpgradeSearchChange(val: string): void {
    this.setPlatformUpgradeSearch.emit(val);
  }

  onUpdateOrgTier(id: number, tier: any): void {
    this.updateOrgTier.emit({ id, tier });
  }

  onExtendOrgSubscription(id: number, days: number): void {
    this.extendOrgSubscription.emit({ id, days });
  }

  async openDeepDive(orgId: number): Promise<void> {
    this.loadingDeepDive = true;
    this.deepDiveError = '';
    this.deepDiveTab = 'decision';
    try {
      this.selectedOrgForDeepDive = await firstValueFrom(
        this.api.platformOrganizationDetails(orgId).pipe(timeout(15000))
      );
    } catch (err: any) {
      console.error('Failed to load gym 360 deep-dive analytics:', err);
      if (err?.name === 'TimeoutError') {
        this.deepDiveError = 'The request timed out after 15 seconds while compiling 360° analytics. Please try again.';
      } else {
        this.deepDiveError = this.api.message(err, 'Failed to load gym 360° deep-dive analytics.');
      }
    } finally {
      this.loadingDeepDive = false;
    }
  }

  closeDeepDive(): void {
    this.selectedOrgForDeepDive = null;
    this.deepDiveError = '';
  }

  setDeepDiveTab(tab: 'decision' | 'facilities' | 'plans' | 'pt' | 'logs' | 'financials'): void {
    this.deepDiveTab = tab;
  }

  get filteredOrganizations(): PlatformOrganization[] {
    let list = this.platformOrganizations;
    if (this.orgFilter === 'HEALTHY') list = list.filter(o => o.healthStatus === 'HEALTHY');
    else if (this.orgFilter === 'MODERATE') list = list.filter(o => o.healthStatus === 'MODERATE');
    else if (this.orgFilter === 'AT_RISK') list = list.filter(o => o.healthStatus === 'AT_RISK');
    else if (this.orgFilter === 'EXPIRING') list = list.filter(o => o.decisionInsights?.some(i => i.includes('Renewal') || i.includes('expires') || i.includes('Approaching')));
    else if (this.orgFilter === 'UPSELL') list = list.filter(o => o.decisionInsights?.some(i => i.includes('Upsell') || i.includes('Expansion') || i.includes('Opportunity')));
    return list;
  }

  get topGymsByRevenue(): PlatformOrganization[] {
    return [...this.platformOrganizations]
      .sort((a, b) => (Number(b.totalRevenue || 0)) - (Number(a.totalRevenue || 0)))
      .slice(0, 5);
  }

  get topGymsByMembers(): PlatformOrganization[] {
    return [...this.platformOrganizations]
      .sort((a, b) => (Number(b.activeMemberCount || 0)) - (Number(a.activeMemberCount || 0)))
      .slice(0, 5);
  }

  get atRiskGyms(): PlatformOrganization[] {
    return this.platformOrganizations.filter(o =>
      o.healthStatus === 'AT_RISK' ||
      o.subscriptionStatus === 'SUSPENDED' ||
      o.subscriptionStatus === 'EXPIRED' ||
      o.decisionInsights?.some(i => i.includes('Renewal') || i.includes('expires') || i.includes('Approaching') || i.includes('Inactive'))
    ).slice(0, 6);
  }

  get upsellGyms(): PlatformOrganization[] {
    return this.platformOrganizations.filter(o =>
      o.decisionInsights?.some(i => i.includes('Upsell') || i.includes('Expansion') || i.includes('Opportunity'))
    ).slice(0, 6);
  }

  get ptAdoptionPercent(): number {
    if (!this.platformOrganizations.length) return 0;
    const withPt = this.platformOrganizations.filter(o => (o.ptTrainerCount || 0) > 0).length;
    return Math.round((withPt / this.platformOrganizations.length) * 100);
  }

  get multiBranchPercent(): number {
    if (!this.platformOrganizations.length) return 0;
    const multi = this.platformOrganizations.filter(o => (o.branchCount || 0) > 1).length;
    return Math.round((multi / this.platformOrganizations.length) * 100);
  }

  get customBrandingPercent(): number {
    if (!this.platformOrganizations.length) return 0;
    const branded = this.platformOrganizations.filter(o => o.customBrandingActive).length;
    return Math.round((branded / this.platformOrganizations.length) * 100);
  }

  healthBadgeClass(score?: number): string {
    if (score == null) return 'health-mid';
    if (score >= 75) return 'health-high';
    if (score >= 45) return 'health-mid';
    return 'health-low';
  }

  healthStatusLabel(status?: string, score?: number): string {
    if (score == null) return status || 'ACTIVE';
    return `${score}% · ${status || 'HEALTHY'}`;
  }

  formatCurrency(val?: number): string {
    if (val == null) return '₹0';
    return '₹' + Number(val).toLocaleString('en-IN', { maximumFractionDigits: 0 });
  }

  dateLabel(dateStr?: string | null): string {
    return dateLabel(dateStr);
  }

  tierBadgeLabel(tier?: string): string {
    switch (tier) {
      case 'STARTER': return '⭐ STARTER';
      case 'PRO': return '⚡ PRO';
      case 'ENTERPRISE':
      case 'PREMIUM': return '👑 ENTERPRISE';
      default: return 'STANDARD';
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
}
