import { Component, EventEmitter, Input, Output, computed, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RenewalRequest, MemberPackageUpgradeRequest } from '../../../core/models';
import { dateLabel, initials, money } from '../../../core/utils/formatters';

export type HubTypeFilter = 'ALL' | 'RENEWAL' | 'UPGRADE';
export type HubStatusFilter = 'PENDING' | 'APPROVED' | 'REJECTED' | 'ALL';

export interface UnifiedApprovalItem {
  id: number;
  kind: 'RENEWAL' | 'UPGRADE';
  membershipId: number;
  memberName: string;
  memberEmail: string;
  memberPhone?: string | null;
  branchName: string;
  planName: string;
  fromPlanName?: string | null;
  amount: number;
  paymentMethod: string;
  transactionId?: string | null;
  notes?: string | null;
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
  reviewedByName?: string | null;
  reviewedAt?: string | null;
  rejectionReason?: string | null;
  createdAt: string;
  rawRenewal?: RenewalRequest;
  rawUpgrade?: MemberPackageUpgradeRequest;
}

@Component({
  selector: 'app-approvals-hub-modal',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './approvals-hub-modal.component.html',
  styleUrl: './approvals-hub-modal.component.scss'
})
export class ApprovalsHubModalComponent {
  @Input() renewalRequests: RenewalRequest[] = [];
  @Input() memberUpgradeRequests: MemberPackageUpgradeRequest[] = [];
  @Input() userRole: string = 'STAFF';

  @Output() close = new EventEmitter<void>();
  @Output() openRenewalApproval = new EventEmitter<RenewalRequest>();
  @Output() openRenewalReject = new EventEmitter<RenewalRequest>();
  @Output() openMemberUpgradeApproval = new EventEmitter<MemberPackageUpgradeRequest>();
  @Output() openMemberUpgradeReject = new EventEmitter<MemberPackageUpgradeRequest>();
  @Output() copyToClipboard = new EventEmitter<{ text: string; label: string }>();

  // Filter signals
  readonly searchQuery = signal<string>('');
  readonly typeFilter = signal<HubTypeFilter>('ALL');
  readonly statusFilter = signal<HubStatusFilter>('PENDING');
  readonly currentPage = signal<number>(1);
  readonly pageSize = 8;

  money = money;
  dateLabel = dateLabel;
  initials = initials;

  readonly allItems = computed<UnifiedApprovalItem[]>(() => {
    const items: UnifiedApprovalItem[] = [];

    for (const r of this.renewalRequests) {
      items.push({
        id: r.id,
        kind: 'RENEWAL',
        membershipId: r.membershipId,
        memberName: r.memberName,
        memberEmail: r.memberEmail,
        memberPhone: r.memberPhone,
        branchName: r.branchName,
        planName: r.planName,
        amount: r.amount,
        paymentMethod: r.paymentMethod,
        transactionId: r.transactionId,
        notes: r.notes,
        status: r.status,
        reviewedByName: r.reviewedByName,
        reviewedAt: r.reviewedAt,
        rejectionReason: r.rejectionReason,
        createdAt: r.createdAt,
        rawRenewal: r
      });
    }

    for (const u of this.memberUpgradeRequests) {
      items.push({
        id: u.id,
        kind: 'UPGRADE',
        membershipId: u.membershipId,
        memberName: u.memberName,
        memberEmail: u.memberEmail,
        memberPhone: u.memberPhone,
        branchName: u.branchName,
        planName: u.newPlanName,
        fromPlanName: u.currentPlanName,
        amount: u.topUpAmount,
        paymentMethod: u.paymentMethod,
        transactionId: u.transactionId,
        notes: u.notes,
        status: u.status,
        reviewedByName: u.reviewedByName,
        reviewedAt: u.reviewedAt,
        rejectionReason: u.rejectionReason,
        createdAt: u.createdAt,
        rawUpgrade: u
      });
    }

    return items.sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
  });

  readonly pendingCount = computed(() => this.allItems().filter(i => i.status === 'PENDING').length);
  readonly renewalPendingCount = computed(() => this.allItems().filter(i => i.kind === 'RENEWAL' && i.status === 'PENDING').length);
  readonly upgradePendingCount = computed(() => this.allItems().filter(i => i.kind === 'UPGRADE' && i.status === 'PENDING').length);

  readonly filteredItems = computed<UnifiedApprovalItem[]>(() => {
    const q = this.searchQuery().trim().toLowerCase();
    const type = this.typeFilter();
    const status = this.statusFilter();

    return this.allItems().filter(item => {
      // Type filter
      if (type !== 'ALL' && item.kind !== type) return false;

      // Status filter
      if (status !== 'ALL' && item.status !== status) return false;

      // Search query
      if (q) {
        const matchesName = item.memberName?.toLowerCase().includes(q);
        const matchesEmail = item.memberEmail?.toLowerCase().includes(q);
        const matchesPhone = item.memberPhone?.toLowerCase().includes(q);
        const matchesTxn = item.transactionId?.toLowerCase().includes(q);
        const matchesBranch = item.branchName?.toLowerCase().includes(q);
        const matchesPlan = item.planName?.toLowerCase().includes(q);
        if (!matchesName && !matchesEmail && !matchesPhone && !matchesTxn && !matchesBranch && !matchesPlan) {
          return false;
        }
      }

      return true;
    });
  });

  readonly totalPages = computed(() => Math.max(1, Math.ceil(this.filteredItems().length / this.pageSize)));

  readonly paginatedItems = computed(() => {
    const start = (this.currentPage() - 1) * this.pageSize;
    return this.filteredItems().slice(start, start + this.pageSize);
  });

  setTypeFilter(type: HubTypeFilter): void {
    this.typeFilter.set(type);
    this.currentPage.set(1);
  }

  setStatusFilter(status: HubStatusFilter): void {
    this.statusFilter.set(status);
    this.currentPage.set(1);
  }

  onSearchChange(val: string): void {
    this.searchQuery.set(val);
    this.currentPage.set(1);
  }

  goToPage(page: number): void {
    if (page >= 1 && page <= this.totalPages()) {
      this.currentPage.set(page);
    }
  }

  onCopy(text: string, label: string): void {
    this.copyToClipboard.emit({ text, label });
  }

  onReview(item: UnifiedApprovalItem): void {
    if (item.kind === 'RENEWAL' && item.rawRenewal) {
      this.openRenewalApproval.emit(item.rawRenewal);
    } else if (item.kind === 'UPGRADE' && item.rawUpgrade) {
      this.openMemberUpgradeApproval.emit(item.rawUpgrade);
    }
  }

  onDecline(item: UnifiedApprovalItem): void {
    if (item.kind === 'RENEWAL' && item.rawRenewal) {
      this.openRenewalReject.emit(item.rawRenewal);
    } else if (item.kind === 'UPGRADE' && item.rawUpgrade) {
      this.openMemberUpgradeReject.emit(item.rawUpgrade);
    }
  }
}

