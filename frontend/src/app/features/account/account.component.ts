import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Membership, UpgradeRequest, User } from '../../core/models';
import { initials } from '../../core/utils/formatters';
import { UpgradeFeatureKey } from '../branches/branches.component';

export type AccountNavigateTarget =
  | 'overview'
  | 'members'
  | 'packages'
  | 'register'
  | 'team'
  | 'account'
  | 'branches'
  | 'reports'
  | 'history'
  | 'pt-management'
  | 'pt-dashboard'
  | 'pt-earnings'
  | 'platform-admin';

export interface ProfileDraft {
  fullName: string;
  email: string;
  phoneNumber: string;
}

export interface PasswordDraft {
  currentPassword: string;
  newPassword: string;
}

export interface BrandingDraft {
  name: string;
}

@Component({
  selector: 'app-account',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './account.component.html',
  styleUrl: './account.component.scss'
})
export class AccountComponent {
  @Input() user: User | null = null;
  @Input({ required: true }) profileDraft!: ProfileDraft;
  @Input({ required: true }) passwordDraft!: PasswordDraft;
  @Input({ required: true }) brandingDraft!: BrandingDraft;
  @Input() logoPreview: string | null = null;
  @Input() brandingSuccessNotice: string = '';
  @Input() brandingSaving: boolean = false;
  @Input({ required: true }) memberships: Membership[] = [];
  @Input({ required: true }) ptTrainees: Membership[] = [];
  @Input({ required: true }) myUpgradeRequests: UpgradeRequest[] = [];
  @Input() busy: boolean = false;
  @Input({ required: true }) isPremium: boolean = false;
  @Input({ required: true }) hasBrandingAccess: boolean = false;
  @Input({ required: true }) hasReceiptUploadAccess: boolean = false;
  @Input({ required: true }) hasHistoryAccess: boolean = false;
  @Input({ required: true }) hasPtAccess: boolean = false;
  @Input({ required: true }) hasAnalyticsAccess: boolean = false;
  @Input({ required: true }) tierDisplayName!: (tier?: string) => string;
  @Input({ required: true }) tierBadgeLabel!: (tier?: string) => string;
  @Input({ required: true }) tierPillClass!: (tier?: string) => string;
  @Input({ required: true }) termDaysRemainingLabel!: (m: Membership) => string;
  @Input({ required: true }) dateLabel!: (date: string) => string;
  @Input({ required: true }) money!: (val: number) => string;
  @Input({ required: true }) canUpgradeMembership!: (m: Membership) => boolean;
  @Input({ required: true }) canRenewMembership!: (m: Membership) => boolean;
  @Input({ required: true }) canFreezeMembership!: (m: Membership) => boolean;

  @Output() saveProfile = new EventEmitter<void>();
  @Output() changePassword = new EventEmitter<void>();
  @Output() saveBranding = new EventEmitter<void>();
  @Output() logoSelected = new EventEmitter<Event>();
  @Output() removeBrandingLogo = new EventEmitter<void>();
  @Output() openUpgradeModal = new EventEmitter<UpgradeFeatureKey>();
  @Output() openMemberUpgradeModal = new EventEmitter<Membership>();
  @Output() openRenewal = new EventEmitter<Membership>();
  @Output() openAdjustment = new EventEmitter<{ mode: 'freeze'; membership: Membership }>();
  @Output() printMembershipReceipt = new EventEmitter<Membership>();
  @Output() navigate = new EventEmitter<AccountNavigateTarget>();
  @Output() openCancelModal = new EventEmitter<void>();
  @Output() openRedeemModal = new EventEmitter<void>();
  @Output() redeemCode = new EventEmitter<string>();
  @Output() reactivateSubscription = new EventEmitter<void>();
  @Output() openLegal = new EventEmitter<'terms' | 'privacy' | 'disclaimer' | 'acceptable-use'>();

  initials = initials;
}
