import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { User } from '../../../core/models';
import { initials } from '../../../core/utils/formatters';

export type View = 'overview' | 'members' | 'packages' | 'register' | 'team' | 'account' | 'branches' | 'reports' | 'history' | 'pt-management' | 'pt-dashboard' | 'pt-earnings' | 'platform-admin';

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './sidebar.component.html',
  styleUrl: './sidebar.component.scss'
})
export class SidebarComponent {
  @Input({ required: true }) user: User | null = null;
  @Input({ required: true }) view: View = 'overview';
  @Input() hasBrandingAccess = false;
  @Input() isSuperAdmin = false;
  @Input() canUpgradeTier = false;
  @Input() hasPtAccess = false;
  @Input() hasAnalyticsAccess = false;
  @Input() hasHistoryAccess = false;
  @Input() membershipTotal = 0;
  @Input() pendingUpgradeRequestsCount = 0;

  @Output() navigate = new EventEmitter<View>();
  @Output() logout = new EventEmitter<void>();
  @Output() openUpgradeModal = new EventEmitter<'general' | 'pt' | 'history' | 'branding' | 'receipt' | 'analytics'>();
  @Output() openLegal = new EventEmitter<'terms' | 'privacy' | 'disclaimer' | 'acceptable-use'>();

  initials = initials;

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

