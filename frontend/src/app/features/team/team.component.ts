import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Branch, User } from '../../core/models';
import { initials } from '../../core/utils/formatters';
import { UpgradeFeatureKey } from '../branches/branches.component';

export interface StaffDraft {
  fullName: string;
  email: string;
  phoneNumber: string;
  password: string;
  branchId: string;
}

export interface StaffEditDraft {
  id: number;
  fullName: string;
  email: string;
  phoneNumber: string;
  branchId: string | number;
  password?: string;
}

@Component({
  selector: 'app-team',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './team.component.html',
  styleUrl: './team.component.scss'
})
export class TeamComponent {
  showStaffPassword = false;
  showStaffEditPassword = false;
  @Input({ required: true }) staff: User[] = [];
  @Input({ required: true }) filteredStaff: User[] = [];
  @Input({ required: true }) maxStaffDisplay: string | number = '3';
  @Input({ required: true }) hasReachedStaffLimit: boolean = false;
  @Input({ required: true }) activeBranches: Branch[] = [];
  @Input({ required: true }) staffDraft!: StaffDraft;
  @Input({ required: true }) staffSearchTerm: string = '';
  @Input({ required: true }) editingStaff: boolean = false;
  @Input({ required: true }) staffEditDraft!: StaffEditDraft;
  @Input() subscriptionTier?: string;
  @Input() busy: boolean = false;
  @Input({ required: true }) hasFieldError!: (field: string) => boolean;
  @Input({ required: true }) getFieldError!: (field: string) => string;
  @Input({ required: true }) tierBadgeLabel!: (tier?: string) => string;

  @Output() openUpgradeModal = new EventEmitter<UpgradeFeatureKey>();
  @Output() createStaff = new EventEmitter<void>();
  @Output() clearFieldError = new EventEmitter<string>();
  @Output() staffSearchTermChange = new EventEmitter<string>();
  @Output() openEditStaffModal = new EventEmitter<User>();
  @Output() deleteStaff = new EventEmitter<User>();
  @Output() closeEditStaffModal = new EventEmitter<void>();
  @Output() saveStaffEdit = new EventEmitter<void>();

  initials = initials;
}

