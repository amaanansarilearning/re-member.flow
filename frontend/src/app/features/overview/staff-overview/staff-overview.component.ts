import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MemberFilter, Membership, MembershipGroup, Plan, PtProfile, User, View } from '../../../core/models';
import { dateLabel, initials, money } from '../../../core/utils/formatters';

@Component({
  selector: 'app-staff-overview',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './staff-overview.component.html',
  styleUrl: './staff-overview.component.scss'
})
export class StaffOverviewComponent {
  @Input() busy = false;
  @Input() user: User | null = null;
  @Input() todayLabel = '';
  @Input() refreshedLabel = '';
  @Input() activePlans: Plan[] = [];
  @Input() personalTrainers: PtProfile[] = [];
  @Input() dashboardMetrics: any = null;
  @Input() dashboardMembers: Membership[] = [];
  @Input() staffExpiringMembersList: Membership[] = [];
  @Input() staffRecentMembersList: Membership[] = [];
  @Input() activeMembersCount = 0;
  @Input() expiringMembersCount = 0;
  @Input() membershipTotal = 0;
  @Input() membershipGroups: MembershipGroup[] = [];
  @Input() accessResults: Record<number, any> = {};
  @Input() quickAccessMember: Membership | null = null;
  @Input() quickAccessQuery = '';

  @Input({ required: true }) canRenewMembership!: (m: Membership) => boolean;
  @Input({ required: true }) canUpgradeMembership!: (m: Membership) => boolean;
  @Input({ required: true }) termDaysRemainingLabel!: (m: Membership) => string;
  @Input({ required: true }) daysUntil!: (val: string) => number;
  @Input({ required: true }) isMemberExpiring!: (m: Membership) => boolean;
  @Input({ required: true }) isExpired!: (val: string) => boolean;
  @Input({ required: true }) isUpcoming!: (val: string) => boolean;
  @Input({ required: true }) memberStatus!: (m: Membership) => string;

  @Output() refreshOverview = new EventEmitter<void>();
  @Output() navigate = new EventEmitter<View>();
  @Output() focusQuickAccess = new EventEmitter<void>();
  @Output() setMemberFilter = new EventEmitter<MemberFilter>();
  @Output() checkQuickAccess = new EventEmitter<void>();
  @Output() clearQuickAccess = new EventEmitter<void>();
  @Output() checkMembershipAccess = new EventEmitter<Membership>();
  @Output() openRenewal = new EventEmitter<Membership>();
  @Output() openMemberUpgradeModal = new EventEmitter<Membership>();
  @Output() copyToClipboard = new EventEmitter<{ text: string; label: string }>();
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

  onCopy(text: string, label: string): void {
    this.copyToClipboard.emit({ text, label });
  }

  onQuickAccessQueryChange(val: string): void {
    this.quickAccessQuery = val;
    this.quickAccessQueryChange.emit(val);
  }
}

