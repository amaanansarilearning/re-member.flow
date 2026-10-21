import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Membership, PtProfile, PtSession, User } from '../../../core/models';
import { dateLabel, initials, money } from '../../../core/utils/formatters';

@Component({
  selector: 'app-pt-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './pt-dashboard.component.html',
  styleUrl: './pt-dashboard.component.scss'
})
export class PtDashboardComponent {
  readonly Math = Math;

  @Input() busy = false;
  @Input() user: User | null = null;
  @Input() myPtProfile: PtProfile | null = null;
  @Input() upcomingPtSessions: PtSession[] = [];
  @Input() ptTrainees: Membership[] = [];
  @Input() filteredPtSessionsWithDate: PtSession[] = [];
  @Input() ptSessionDateFilter: 'ALL' | 'TODAY' | 'UPCOMING' | string = 'ALL';
  @Input() ptSessionStatusFilter = 'all';

  @Output() quickStartSessionNow = new EventEmitter<Membership>();
  @Output() openBookSessionModal = new EventEmitter<Membership>();
  @Output() completePtSessionQuick = new EventEmitter<PtSession>();
  @Output() missedPtSessionQuick = new EventEmitter<PtSession>();
  @Output() cancelPtSessionQuick = new EventEmitter<PtSession>();
  @Output() setPtSessionDateFilter = new EventEmitter<'ALL' | 'TODAY' | 'UPCOMING'>();
  @Output() setPtSessionStatusFilter = new EventEmitter<string>();
  @Output() copyToClipboard = new EventEmitter<{ text: string; label: string }>();

  money(amount: number): string {
    return money(amount);
  }

  dateLabel(val?: string | null): string {
    return dateLabel(val);
  }

  initials(name: string): string {
    return initials(name);
  }

  ptSessionProgress(member: Membership): number {
    if (!member.ptSessionsIncluded || member.ptSessionsIncluded <= 0) return 0;
    const used = member.ptSessionsUsed ?? 0;
    return Math.min(100, Math.round((used / member.ptSessionsIncluded) * 100));
  }

  onDateFilterChange(filter: any): void {
    this.setPtSessionDateFilter.emit(filter);
  }

  onStatusFilterChange(filter: any): void {
    this.setPtSessionStatusFilter.emit(filter);
  }

  onCopy(text: string, label: string): void {
    this.copyToClipboard.emit({ text, label });
  }
}

