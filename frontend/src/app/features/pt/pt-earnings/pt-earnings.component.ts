import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { PtEarning, PtPayout } from '../../../core/models';
import { dateLabel, initials, money } from '../../../core/utils/formatters';

@Component({
  selector: 'app-pt-earnings',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './pt-earnings.component.html',
  styleUrl: './pt-earnings.component.scss'
})
export class PtEarningsComponent {
  @Input() totalPtGrossRevenue = 0;
  @Input() totalPtNetCommission = 0;
  @Input() pendingPtEarningsTotal = 0;
  @Input() paidPtEarningsTotal = 0;
  @Input() ptEarnings: PtEarning[] = [];
  @Input() filteredPtEarnings: PtEarning[] = [];
  @Input() ptPayouts: PtPayout[] = [];
  @Input() ptEarningFilter: 'ALL' | 'PENDING' | 'SETTLED' | string = 'ALL';

  @Output() setPtEarningFilter = new EventEmitter<'ALL' | 'PENDING' | 'SETTLED'>();

  money(amount: number): string {
    return money(amount);
  }

  dateLabel(val?: string | null): string {
    return dateLabel(val);
  }

  initials(name: string): string {
    return initials(name);
  }

  onFilterChange(filter: any): void {
    this.setPtEarningFilter.emit(filter);
  }
}

