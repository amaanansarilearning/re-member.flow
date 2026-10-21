import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Branch, Plan, PtPackageType, Role } from '../../core/models';

export type PlanFilterType = 'active' | 'archived' | 'all';
export type PlanTypeFilterType = 'ALL' | 'STANDARD' | 'COMBINED' | 'PT_ONLY';
export type PackageViewMode = 'cards' | 'matrix';

export interface PlanDraft {
  id?: number;
  name: string;
  description: string;
  price: number;
  durationMode: 'months' | 'days';
  durationMonths: number;
  durationDays: number;
  branchId: string;
  active: boolean;
  ptPackageType: PtPackageType;
  includedPtSessions: number;
  ptFeeIncluded: number;
}

@Component({
  selector: 'app-packages',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './packages.component.html',
  styleUrl: './packages.component.scss'
})
export class PackagesComponent {
  @Input() userRole?: Role;
  @Input({ required: true }) editingPlan: boolean = false;
  @Input({ required: true }) planDraft!: PlanDraft;
  @Input({ required: true }) activeBranches: Branch[] = [];
  @Input({ required: true }) planPtSessionRate: number = 0;
  @Input({ required: true }) planPtValidationError: string = '';
  @Input({ required: true }) planFilter: PlanFilterType = 'active';
  @Input({ required: true }) planTypeFilter: PlanTypeFilterType = 'ALL';
  @Input({ required: true }) packageViewMode: PackageViewMode = 'cards';
  @Input({ required: true }) visiblePlans: Plan[] = [];
  @Input({ required: true }) activePlans: Plan[] = [];
  @Input({ required: true }) plans: Plan[] = [];
  @Input() busy: boolean = false;
  @Input({ required: true }) hasFieldError!: (field: string) => boolean;
  @Input({ required: true }) getFieldError!: (field: string) => string;
  @Input({ required: true }) money!: (val: number) => string;
  @Input({ required: true }) planDurationLabel!: (plan: Plan) => string;
  @Input({ required: true }) monthlyEquivalent!: (plan: Plan) => number | null;
  @Input({ required: true }) countForPlan!: (planId: number) => number;

  @Output() startNewPlan = new EventEmitter<void>();
  @Output() closeEditingPlan = new EventEmitter<void>();
  @Output() savePlan = new EventEmitter<void>();
  @Output() clearFieldError = new EventEmitter<string>();
  @Output() planFilterChange = new EventEmitter<PlanFilterType>();
  @Output() planTypeFilterChange = new EventEmitter<PlanTypeFilterType>();
  @Output() packageViewModeChange = new EventEmitter<PackageViewMode>();
  @Output() enrollWithPlan = new EventEmitter<Plan>();
  @Output() editPlan = new EventEmitter<Plan>();

  readonly Math = Math;
}

