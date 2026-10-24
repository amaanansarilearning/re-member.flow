import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Branch, Plan, PtProfile, Role } from '../../core/models';
import { UpgradeFeatureKey } from '../branches/branches.component';

export interface MemberDraft {
  fullName: string;
  email: string;
  phoneNumber: string;
  password: string;
  planId: string | number;
  branchId: string | number;
  personalTrainerId: string | number;
  discountPercent: number;
  startDate: string;
  waiverAcknowledged: boolean;
  paymentMethod: string;
  transactionId: string;
}

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './register.component.html',
  styleUrl: './register.component.scss'
})
export class RegisterComponent {
  showMemberPassword = false;
  @Input() userRole?: Role;
  @Input() userBranchName?: string | null;
  @Input({ required: true }) memberDraft!: MemberDraft;
  @Input({ required: true }) activePlans: Plan[] = [];
  @Input({ required: true }) personalTrainers: PtProfile[] = [];
  @Input({ required: true }) activeBranches: Branch[] = [];
  @Input({ required: true }) hasReceiptUploadAccess: boolean = false;
  @Input() receiptFile: File | null = null;
  @Input() error: string = '';
  @Input() busy: boolean = false;
  @Input() selectedPlan: Plan | null = null;
  @Input() selectedPt: PtProfile | null = null;
  @Input({ required: true }) discountAmount: number = 0;
  @Input({ required: true }) finalPrice: number = 0;
  @Input({ required: true }) previewEndDate: string = '';
  @Input({ required: true }) hasFieldError!: (field: string) => boolean;
  @Input({ required: true }) getFieldError!: (field: string) => string;
  @Input({ required: true }) money!: (val: number) => string;
  @Input({ required: true }) planDurationLabel!: (plan: Plan) => string;

  @Output() navigate = new EventEmitter<'members'>();
  @Output() registerMember = new EventEmitter<void>();
  @Output() clearFieldError = new EventEmitter<string>();
  @Output() selectReceipt = new EventEmitter<Event>();
  @Output() removeReceipt = new EventEmitter<HTMLInputElement>();
  @Output() openUpgradeModal = new EventEmitter<UpgradeFeatureKey>();
  @Output() openLegal = new EventEmitter<'terms' | 'privacy' | 'disclaimer' | 'acceptable-use'>();
}

