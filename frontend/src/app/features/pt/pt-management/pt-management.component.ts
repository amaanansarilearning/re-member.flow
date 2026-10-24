import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Branch, PtAnalytics, PtPayout, PtProfile, User } from '../../../core/models';
import { dateLabel, initials, money } from '../../../core/utils/formatters';

@Component({
  selector: 'app-pt-management',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './pt-management.component.html',
  styleUrl: './pt-management.component.scss'
})
export class PtManagementComponent {
  readonly Math = Math;
  showPtPassword = false;

  @Input() busy = false;
  @Input() user: User | null = null;
  @Input() error: string | null = null;
  @Input() ptDraft: any = {};
  @Input() editingPt = false;
  @Input() activeBranches: Branch[] = [];
  @Input() personalTrainers: PtProfile[] = [];
  @Input() filteredPersonalTrainers: PtProfile[] = [];
  @Input() ptAnalyticsData: PtAnalytics | null = null;
  @Input() ptSearchTerm = '';
  @Input() ptStatusFilter: 'ALL' | 'ACTIVE' | 'INACTIVE' | string = 'ALL';
  @Input() activePtCount = 0;
  @Input() inactivePtCount = 0;
  @Input() ptPayouts: PtPayout[] = [];
  @Input() filteredPtPayouts: PtPayout[] = [];
  @Input() ptPayoutFilter: 'ALL' | 'PENDING' | 'PAID' | string = 'ALL';
  @Input() totalPayoutsPending = 0;
  @Input() totalPayoutsSettled = 0;
  @Input() hasFieldError!: (field: string) => boolean;
  @Input() getFieldError!: (field: string) => string;

  @Output() startNewPt = new EventEmitter<void>();
  @Output() cancelPtEdit = new EventEmitter<void>();
  @Output() savePt = new EventEmitter<void>();
  @Output() editPt = new EventEmitter<PtProfile>();
  @Output() togglePtActive = new EventEmitter<PtProfile>();
  @Output() openPtSessionsModal = new EventEmitter<PtProfile>();
  @Output() openPtPayoutModal = new EventEmitter<PtProfile>();
  @Output() markPtPayoutPaidQuick = new EventEmitter<PtPayout>();
  @Output() setPtSearchTerm = new EventEmitter<string>();
  @Output() setPtStatusFilter = new EventEmitter<'ALL' | 'ACTIVE' | 'INACTIVE'>();
  @Output() setPtPayoutFilter = new EventEmitter<'ALL' | 'PENDING' | 'PAID'>();
  @Output() clearFieldError = new EventEmitter<string>();

  money(amount: number): string {
    return money(amount);
  }

  dateLabel(val?: string | null): string {
    return dateLabel(val);
  }

  initials(name: string): string {
    return initials(name);
  }

  onSearchTermChange(val: string): void {
    this.setPtSearchTerm.emit(val);
  }

  onStatusFilterChange(val: any): void {
    this.setPtStatusFilter.emit(val);
  }

  onPayoutFilterChange(val: any): void {
    this.setPtPayoutFilter.emit(val);
  }

  preventNonNumeric(event: KeyboardEvent, allowDecimal = true): void {
    const allowedKeys = ['Backspace', 'Tab', 'Delete', 'ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown', 'Enter', 'Escape', 'Home', 'End'];
    if (allowedKeys.includes(event.key)) return;
    if (event.ctrlKey || event.metaKey) return;
    if (allowDecimal && (event.key === '.' || event.key === ',')) {
      const target = event.target as HTMLInputElement;
      if (target && !target.value.includes('.') && !target.value.includes(',')) return;
    }
    if (!/^[0-9]$/.test(event.key)) {
      event.preventDefault();
    }
  }

  sanitizeNumericInput(event: Event, targetObj?: any, field?: string, isInt = false, max?: number): void {
    const input = event.target as HTMLInputElement;
    if (!input) return;
    let clean = input.value.replace(/[^0-9.]/g, '');
    const firstDot = clean.indexOf('.');
    if (firstDot !== -1) {
      clean = clean.substring(0, firstDot + 1) + clean.substring(firstDot + 1).replace(/\./g, '');
    }
    if (targetObj && field) {
      if (clean === '') {
        targetObj[field] = 0;
        return;
      }
      let num = isInt ? parseInt(clean, 10) : parseFloat(clean);
      if (isNaN(num)) num = 0;
      if (max !== undefined && num > max) num = max;
      targetObj[field] = Math.max(0, num);
    }
  }
}
