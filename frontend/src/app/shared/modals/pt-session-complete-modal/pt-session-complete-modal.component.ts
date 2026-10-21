import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { PtSession, Role } from '../../../core/models';
import { dateLabel, money } from '../../../core/utils/formatters';

@Component({
  selector: 'app-pt-session-complete-modal',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './pt-session-complete-modal.component.html',
  styleUrl: './pt-session-complete-modal.component.scss'
})
export class PtSessionCompleteModalComponent {
  @Input({ required: true }) session!: PtSession;
  @Input() userRole?: Role;
  @Input() pin = '';
  @Input() notes = '';
  @Input() busy = false;
  @Input() error = '';

  @Output() close = new EventEmitter<void>();
  @Output() pinChange = new EventEmitter<string>();
  @Output() notesChange = new EventEmitter<string>();
  @Output() submit = new EventEmitter<boolean>();

  money = money;
  dateLabel = dateLabel;

  roundCommission(fee: number, rate: number): number {
    return Math.round((fee * rate) / 100);
  }
}
