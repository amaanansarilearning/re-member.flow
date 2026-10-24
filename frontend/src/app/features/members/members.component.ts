import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AdjustmentMode, Freeze, MemberFilter, Membership, MembershipGroup, User, View } from '../../core/models';
import { dateLabel, initials, money } from '../../core/utils/formatters';

@Component({
  selector: 'app-members',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './members.component.html',
  styleUrl: './members.component.scss'
})
export class MembersComponent {
  @Input() busy = false;
  @Input() user: User | null = null;
  @Input() view: View | string = 'members';
  @Input() activeMembersCount = 0;
  @Input() expiringMembersCount = 0;
  @Input() cancelledMembersCount = 0;
  @Input() membershipTotal = 0;
  @Input() membershipGroups: MembershipGroup[] = [];
  @Input() membershipPageIndex = 0;
  @Input() membershipTotalPages = 0;
  @Input() memberFilter: MemberFilter = 'all';
  @Input() accessResults: Record<number, any> = {};
  @Input() searchTerm = '';

  @Input({ required: true }) canCancelFreeze!: (freeze: Freeze) => boolean;
  @Input({ required: true }) canCancelRenewal!: (member: Membership) => boolean;
  @Input({ required: true }) canEndFreezeEarly!: (freeze: Freeze) => boolean;
  @Input({ required: true }) canFreezeMembership!: (member: Membership) => boolean;
  @Input({ required: true }) canRenewMembership!: (member: Membership) => boolean;
  @Input({ required: true }) canSendRenewalReminder!: (member: Membership) => boolean;
  @Input({ required: true }) canUpgradeMembership!: (member: Membership) => boolean;
  @Input({ required: true }) hasPendingUpgrade!: (member: Membership) => boolean;
  @Input({ required: true }) hasPendingRenewal!: (member: Membership) => boolean;
  @Input({ required: true }) freezeStatus!: (freeze: Freeze) => string;
  @Input({ required: true }) groupState!: (group: MembershipGroup) => string;
  @Input({ required: true }) currentTerm!: (group: MembershipGroup) => Membership;
  @Input({ required: true }) historyTerms!: (group: MembershipGroup) => Membership[];
  @Input({ required: true }) historyTermStatus!: (group: MembershipGroup, term: Membership) => string;
  @Input({ required: true }) hasTransferTarget!: (member: Membership) => boolean;
  @Input({ required: true }) isExpired!: (val: string) => boolean;
  @Input({ required: true }) isMemberExpiring!: (member: Membership) => boolean;
  @Input({ required: true }) isMemberHistoryExpanded!: (memberId: number) => boolean;
  @Input({ required: true }) isUpcoming!: (val: string) => boolean;
  @Input({ required: true }) termDaysRemainingLabel!: (member: Membership) => string;
  @Input({ required: true }) ptSessionProgress!: (member: Membership) => number;

  @Output() refreshMembersDirectory = new EventEmitter<void>();
  @Output() navigate = new EventEmitter<View>();
  @Output() exportReport = new EventEmitter<string>();
  @Output() selectMemberFilter = new EventEmitter<MemberFilter>();
  @Output() searchMembers = new EventEmitter<void>();
  @Output() searchTermChange = new EventEmitter<string>();
  @Output() moveMembershipPage = new EventEmitter<number>();
  @Output() toggleMemberHistory = new EventEmitter<number>();
  @Output() checkMembershipAccess = new EventEmitter<Membership>();
  @Output() sendManualReminder = new EventEmitter<Membership>();
  @Output() openRenewal = new EventEmitter<Membership>();
  @Output() openMemberUpgradeModal = new EventEmitter<Membership>();
  @Output() openAdjustment = new EventEmitter<{ mode: AdjustmentMode; member: Membership; freeze: Freeze | null }>();
  @Output() cancelMembership = new EventEmitter<Membership>();
  @Output() cancelScheduledRenewal = new EventEmitter<Membership>();
  @Output() printMembershipReceipt = new EventEmitter<Membership>();
  @Output() copyToClipboard = new EventEmitter<{ text: string; label: string }>();

  money(amount: number): string {
    return money(amount);
  }

  dateLabel(val?: string | null): string {
    return dateLabel(val);
  }

  initials(name: string): string {
    return initials(name);
  }

  onSearchTermChange(val: string): void {
    this.searchTerm = val;
    this.searchTermChange.emit(val);
    this.searchMembers.emit();
  }

  onClearSearch(): void {
    this.searchTerm = '';
    this.searchTermChange.emit('');
    this.searchMembers.emit();
  }

  onResetSearchAndFilter(): void {
    this.searchTerm = '';
    this.searchTermChange.emit('');
    this.selectMemberFilter.emit('all');
  }

  onCopy(text: string, label: string): void {
    this.copyToClipboard.emit({ text, label });
  }

  onOpenAdjustment(mode: AdjustmentMode, member: Membership, freeze: Freeze | null = null): void {
    this.openAdjustment.emit({ mode, member, freeze });
  }
}
