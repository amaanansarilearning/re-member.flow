import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActionHistory, RenewalReminder } from '../../core/models';
import { initials } from '../../core/utils/formatters';

export type AuditChange = { field: string; before: string; after: string };
export type AuditFilterType = 'ALL' | 'MEMBERSHIP' | 'PACKAGE' | 'BRANCH' | 'STAFF' | 'PT';
export type ReminderStatusFilterType = 'ALL' | 'SENT' | 'PENDING' | 'FAILED';

@Component({
  selector: 'app-history',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './history.component.html',
  styleUrl: './history.component.scss'
})
export class HistoryComponent {
  @Input({ required: true }) historyTab: 'audit' | 'email' = 'audit';
  @Input({ required: true }) actionHistory: ActionHistory[] = [];
  @Input({ required: true }) renewalReminders: RenewalReminder[] = [];
  @Input() auditFilter: AuditFilterType = 'ALL';
  @Input() auditMembershipCount = 0;
  @Input() auditPackageCount = 0;
  @Input() auditBranchCount = 0;
  @Input() auditStaffCount = 0;
  @Input() auditPtCount = 0;
  @Input() auditSearchTerm = '';
  @Input({ required: true }) filteredAuditHistory: ActionHistory[] = [];
  @Input() reminderStatusFilter: ReminderStatusFilterType = 'ALL';
  @Input() reminderSearchTerm = '';
  @Input({ required: true }) filteredRenewalReminders: RenewalReminder[] = [];
  @Input() sentRemindersCount = 0;
  @Input() pendingRemindersCount = 0;
  @Input() failedRemindersCount = 0;
  @Input({ required: true }) getAuditChanges!: (entry: ActionHistory) => AuditChange[];

  @Output() historyTabChange = new EventEmitter<'audit' | 'email'>();
  @Output() auditFilterChange = new EventEmitter<AuditFilterType>();
  @Output() auditSearchTermChange = new EventEmitter<string>();
  @Output() reminderStatusFilterChange = new EventEmitter<ReminderStatusFilterType>();
  @Output() reminderSearchTermChange = new EventEmitter<string>();

  initials = initials;

  auditActionLabel(action: string): string {
    const labels: Record<string, string> = {
      MEMBERSHIP_CREATED: 'Membership created',
      MEMBERSHIP_RENEWED: 'Renewal scheduled',
      MEMBERSHIP_CANCELLED: 'Membership cancelled',
      MEMBERSHIP_FREEZE_SCHEDULED: 'Freeze scheduled',
      MEMBERSHIP_FREEZE_CANCELLED: 'Freeze cancelled',
      MEMBERSHIP_FREEZE_ENDED: 'Freeze ended early',
      MEMBERSHIP_TRANSFERRED: 'Branch transferred',
      MEMBERSHIP_CORRECTED: 'Record adjusted',
      PLAN_CREATED: 'Package created',
      PLAN_UPDATED: 'Package updated',
      PLAN_ARCHIVED: 'Package archived',
      PLAN_RESTORED: 'Package restored',
      BRANCH_CREATED: 'Branch created',
      BRANCH_UPDATED: 'Branch updated',
      BRANCH_STATUS_TOGGLED: 'Branch status changed',
      STAFF_CREATED: 'Staff invited',
      STAFF_UPDATED: 'Staff profile updated',
      STAFF_DEACTIVATED: 'Staff deactivated',
      PT_PROFILE_CREATED: 'Trainer profile created',
      PT_PROFILE_UPDATED: 'Trainer profile updated',
      PT_SESSION_BOOKED: 'PT session scheduled',
      PT_SESSION_COMPLETED: 'PT session completed',
      PT_SESSION_MISSED: 'PT session marked missed',
      PT_SESSION_CANCELLED: 'PT session cancelled',
      PT_PAYOUT_CREATED: 'Trainer payout generated'
    };
    return labels[action] || action.replace(/_/g, ' ').toLowerCase();
  }

  auditActionClass(action: string): string {
    if (!action) return 'action-info';
    if (action.includes('CREATED') || action.includes('ENROLLED') || action.includes('RESTORED') || action.includes('ACTIVE') || action.includes('COMPLETED') || action.includes('PAID') || action.includes('SETTLED')) return 'action-positive';
    if (action.includes('CANCELLED') || action.includes('DELETED') || action.includes('ARCHIVED')) return 'action-negative';
    if (action.includes('FROZEN') || action.includes('CORRECTED') || action.includes('TRANSFERRED') || action.includes('UPDATED') || action.includes('MISSED')) return 'action-warning';
    return 'action-info';
  }

  auditChanges(entry: ActionHistory): AuditChange[] {
    return this.getAuditChanges ? this.getAuditChanges(entry) : [];
  }
}
