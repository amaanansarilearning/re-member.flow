import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Branch, Freeze, Membership, Plan } from '../../../core/models';
import { dateLabel } from '../../../core/utils/formatters';

export type AdjustmentMode = 'freeze' | 'transfer' | 'correct' | 'cancel-freeze' | 'end-freeze';

export interface AdjustmentDraft {
  planId: string | number;
  branchId: string | number | null;
  startDate: string;
  endDate: string;
  listPrice: number;
  discountPercent: number;
  amountPaid: number;
  reason: string;
}

@Component({
  selector: 'app-adjustment-modal',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './adjustment-modal.component.html',
  styleUrl: './adjustment-modal.component.scss'
})
export class AdjustmentModalComponent {
  @Input({ required: true }) member!: Membership;
  @Input({ required: true }) mode: AdjustmentMode | null = null;
  @Input({ required: true }) draft!: AdjustmentDraft;
  @Input() freezeTarget: Freeze | null = null;
  @Input({ required: true }) activeBranches: Branch[] = [];
  @Input({ required: true }) plans: Plan[] = [];
  @Input({ required: true }) today!: string;
  @Input() error = '';
  @Input() busy = false;

  @Output() close = new EventEmitter<void>();
  @Output() submit = new EventEmitter<void>();

  dateLabel = dateLabel;

  hasTransferTarget(member: Membership): boolean {
    return this.activeBranches.some((b) => b.id !== member.branchId);
  }
}
