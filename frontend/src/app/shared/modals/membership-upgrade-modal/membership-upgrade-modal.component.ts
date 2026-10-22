import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Membership, MembershipUpgradeQuote, Plan, PtProfile } from '../../../core/models';
import { dateLabel, money } from '../../../core/utils/formatters';
import { daysUntil, isExpired, isUpcoming } from '../../../core/utils/date-utils';

export interface UpgradeMembershipDraft {
  topUpPaid: number;
  paymentMethod: string;
  transactionId: string;
  personalTrainerId: number | null;
  notes?: string;
}

@Component({
  selector: 'app-membership-upgrade-modal',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './membership-upgrade-modal.component.html',
  styleUrl: './membership-upgrade-modal.component.scss'
})
export class MembershipUpgradeModalComponent {
  @Input({ required: true }) member!: Membership;
  @Input({ required: true }) activePlans: Plan[] = [];
  @Input() targetPlanId: number | null = null;
  @Input() selectedUpgradePlan: Plan | null | undefined = null;
  @Input() quoteLoading = false;
  @Input() quote: MembershipUpgradeQuote | null = null;
  @Input() quoteError = '';
  @Input() personalTrainers: PtProfile[] = [];
  @Input({ required: true }) draft!: UpgradeMembershipDraft;
  @Input() busy = false;
  @Input() fieldErrors: Record<string, string> = {};
  @Input() userRole = 'STAFF';

  @Output() close = new EventEmitter<void>();
  @Output() targetPlanChange = new EventEmitter<number | null>();
  @Output() confirm = new EventEmitter<void>();
  @Output() clearField = new EventEmitter<string>();

  money = money;
  dateLabel = dateLabel;

  planDurationLabel(plan: Plan): string {
    return plan.durationDays !== null
      ? `${plan.durationDays} ${plan.durationDays === 1 ? 'day' : 'days'}`
      : `${plan.durationMonths} ${plan.durationMonths === 1 ? 'month' : 'months'}`;
  }

  termDaysRemainingLabel(member: Membership): string {
    if (member.status === 'CANCELLED') return 'Term cancelled';
    if (isUpcoming(member.startDate)) return `Starts in ${daysUntil(member.startDate)}d`;
    if (isExpired(member.endDate)) return 'Term ended';
    const left = daysUntil(member.endDate);
    if (left === 0) return 'Ends today';
    if (left === 1) return '1 day left';
    return `${left} days left`;
  }

  hasFieldError(field: string): boolean {
    return !!this.fieldErrors[field];
  }

  getFieldError(field: string): string {
    return this.fieldErrors[field] || '';
  }
}

