import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Branch, Dashboard, MemberFilter, Membership, Plan, PtAnalytics, PtProfile, User, View } from '../../../core/models';
import { dateLabel, initials, money } from '../../../core/utils/formatters';

@Component({
  selector: 'app-owner-overview',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './owner-overview.component.html',
  styleUrl: './owner-overview.component.scss'
})
export class OwnerOverviewComponent {
  @Input() busy = false;
  @Input() user: User | null = null;
  @Input() dashboard: Dashboard | null = null;
  @Input() dashboardMetrics: any = null;
  @Input() dashboardMembers: Membership[] = [];
  @Input() dashboardBranchId: number | null = null;
  @Input() dashboardLocationLabel = '';
  @Input() activeBranches: Branch[] = [];
  @Input() activePlans: Plan[] = [];
  @Input() personalTrainers: PtProfile[] = [];
  @Input() ptAnalyticsData: PtAnalytics | null = null;
  @Input() accessResults: Record<number, any> = {};
  @Input() quickAccessMember: Membership | null = null;
  @Input() quickAccessQuery = '';
  @Input() refreshedLabel = '';
  @Input() todayLabel = '';
  @Input() revenueTrendLabel = '';
  @Input() revenueTrendDown = false;

  @Input({ required: true }) canRenewMembership!: (m: Membership) => boolean;
  @Input({ required: true }) termDaysRemainingLabel!: (m: Membership) => string;
  @Input({ required: true }) daysUntil!: (val: string) => number;
  @Input({ required: true }) isMemberExpiring!: (m: Membership) => boolean;
  @Input({ required: true }) isExpired!: (val: string) => boolean;
  @Input({ required: true }) isUpcoming!: (val: string) => boolean;
  @Input({ required: true }) memberStatus!: (m: Membership) => string;

  @Output() selectDashboardBranch = new EventEmitter<string | number>();
  @Output() refreshOverview = new EventEmitter<void>();
  @Output() checkQuickAccess = new EventEmitter<void>();
  @Output() clearQuickAccess = new EventEmitter<void>();
  @Output() focusQuickAccess = new EventEmitter<void>();
  @Output() checkMembershipAccess = new EventEmitter<Membership>();
  @Output() openRenewal = new EventEmitter<Membership>();
  @Output() navigate = new EventEmitter<View>();
  @Output() copyToClipboard = new EventEmitter<{ text: string; label: string }>();
  @Output() setMemberFilter = new EventEmitter<MemberFilter>();
  @Output() quickAccessQueryChange = new EventEmitter<string>();

  money(amount: number): string {
    return money(amount);
  }

  dateLabel(val?: string | null): string {
    return dateLabel(val);
  }

  initials(name: string): string {
    return initials(name);
  }

  firstName(fullName?: string): string {
    return fullName ? fullName.split(' ')[0] : (this.user?.fullName.split(' ')[0] ?? 'there');
  }

  barHeight(value: number): number {
    if (value <= 0) return 0;
    const max = Math.max(this.dashboard?.revenueThisMonth ?? 0, this.dashboard?.revenueLastMonth ?? 0, 1);
    return Math.max(8, Math.round((value / max) * 100));
  }

  onCopy(text: string, label: string): void {
    this.copyToClipboard.emit({ text, label });
  }

  onQuickAccessQueryChange(val: string): void {
    this.quickAccessQuery = val;
    this.quickAccessQueryChange.emit(val);
  }
}
