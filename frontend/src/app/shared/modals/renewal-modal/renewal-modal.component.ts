import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Branch, Membership, Plan, PtProfile, Role } from '../../../core/models';
import { dateLabel, money } from '../../../core/utils/formatters';

export interface RenewalDraft {
  planId: string | number;
  branchId: string | number | null;
  personalTrainerId: number | string | null;
  discountPercent: number;
  startDate: string;
  paymentMethod: string;
  transactionId: string;
  notes?: string;
}

@Component({
  selector: 'app-renewal-modal',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './renewal-modal.component.html',
  styleUrl: './renewal-modal.component.scss'
})
export class RenewalModalComponent {
  @Input({ required: true }) member!: Membership;
  @Input({ required: true }) draft!: RenewalDraft;
  @Input({ required: true }) activePlans: Plan[] = [];
  @Input({ required: true }) activeBranches: Branch[] = [];
  @Input({ required: true }) personalTrainers: PtProfile[] = [];
  @Input() userRole: Role = 'OWNER';
  @Input() targetBranchName: string | null = null;
  @Input({ required: true }) minimumStartDate!: string;
  @Input({ required: true }) totalAmount = 0;
  @Input({ required: true }) endDate = '';
  @Input() selectedPlan: Plan | undefined = undefined;
  @Input() error = '';
  @Input() busy = false;

  @Output() close = new EventEmitter<void>();
  @Output() confirm = new EventEmitter<void>();
  @Output() planChange = new EventEmitter<string | number>();

  onPlanChange(value: string | number): void {
    this.planChange.emit(value);
  }

  money = money;
  dateLabel = dateLabel;

  planDurationLabel(plan: Plan): string {
    return plan.durationDays !== null
      ? `${plan.durationDays} ${plan.durationDays === 1 ? 'day' : 'days'}`
      : `${plan.durationMonths} ${plan.durationMonths === 1 ? 'month' : 'months'}`;
  }
}
