import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { PtProfile, PtSession } from '../../../core/models';
import { dateLabel, money } from '../../../core/utils/formatters';

export type PtSessionFilter = 'ALL' | 'BOOKED' | 'COMPLETED' | 'MISSED' | 'CANCELLED';

@Component({
  selector: 'app-pt-sessions-modal',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './pt-sessions-modal.component.html',
  styleUrl: './pt-sessions-modal.component.scss'
})
export class PtSessionsModalComponent {
  @Input({ required: true }) pt!: PtProfile;
  @Input({ required: true }) sessions: PtSession[] = [];
  @Input() filter: PtSessionFilter = 'ALL';
  @Input() totalSessionsCount = 0;
  @Input() loading = false;
  @Input() error = '';
  @Input() busy = false;

  @Output() close = new EventEmitter<void>();
  @Output() filterChange = new EventEmitter<PtSessionFilter>();
  @Output() refresh = new EventEmitter<number>();
  @Output() complete = new EventEmitter<PtSession>();
  @Output() markMissed = new EventEmitter<PtSession>();
  @Output() cancel = new EventEmitter<PtSession>();

  money = money;
  dateLabel = dateLabel;

  roundCommission(fee: number, rate: number): number {
    return Math.round((fee * rate) / 100);
  }
}
