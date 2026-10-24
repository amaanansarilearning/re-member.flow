import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivationCode, PlatformMetrics, PlatformOrganization, UpgradeRequest } from '../../core/models';
import { dateLabel } from '../../core/utils/formatters';

@Component({
  selector: 'app-platform-admin',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './platform-admin.component.html',
  styleUrl: './platform-admin.component.scss'
})
export class PlatformAdminComponent {
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

