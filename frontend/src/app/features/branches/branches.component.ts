import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Branch, BranchType, Role } from '../../core/models';

export interface BranchDraft {
  id?: number;
  name: string;
  code: string;
  type: BranchType;
  address?: string;
  phoneNumber?: string;
  active: boolean;
}

export type BranchFilterType = 'ALL' | 'COMPANY_OWNED' | 'FRANCHISE' | 'ACTIVE';
export type UpgradeFeatureKey = 'history' | 'general' | 'pt' | 'branding' | 'receipt' | 'analytics';

@Component({
  selector: 'app-branches',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './branches.component.html',
  styleUrl: './branches.component.scss'
})
export class BranchesComponent {
  @Input({ required: true }) branches: Branch[] = [];
  @Input({ required: true }) maxBranchesDisplay: string | number = '1';
  @Input({ required: true }) activeBranches: Branch[] = [];
  @Input({ required: true }) companyOwnedCount: number = 0;
  @Input({ required: true }) franchiseCount: number = 0;
  @Input({ required: true }) totalBranchMembers: number = 0;
  @Input({ required: true }) branchFilter: BranchFilterType = 'ALL';
  @Input({ required: true }) filteredBranches: Branch[] = [];
  @Input({ required: true }) editingBranch: boolean = false;
  @Input({ required: true }) branchDraft!: BranchDraft;
  @Input({ required: true }) enterpriseBranchPromptOpen: boolean = false;
  @Input() userRole?: Role;
  @Input() subscriptionTier?: string;
  @Input() busy: boolean = false;
  @Input({ required: true }) hasFieldError!: (field: string) => boolean;
  @Input({ required: true }) getFieldError!: (field: string) => string;
  @Input({ required: true }) tierBadgeLabel!: (tier?: string) => string;

  @Output() startNewBranch = new EventEmitter<void>();
  @Output() branchFilterChange = new EventEmitter<BranchFilterType>();
  @Output() closeEditingBranch = new EventEmitter<void>();
  @Output() saveBranch = new EventEmitter<void>();
  @Output() clearFieldError = new EventEmitter<string>();
  @Output() toggleBranchActive = new EventEmitter<Branch>();
  @Output() viewBranchInOverview = new EventEmitter<number>();
  @Output() editBranch = new EventEmitter<Branch>();
  @Output() closeEnterpriseBranchPrompt = new EventEmitter<void>();
  @Output() openUpgradeModal = new EventEmitter<UpgradeFeatureKey>();
}
