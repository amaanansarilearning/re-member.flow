import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CancellationReason, Membership } from '../../../core/models';

export interface CancellationDraft {
  reason: CancellationReason;
  remarks: string;
  cancellationDate: string;
  otherExplanation: string;
}

@Component({
  selector: 'app-cancellation-modal',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './cancellation-modal.component.html',
  styleUrl: './cancellation-modal.component.scss'
})
export class CancellationModalComponent {
  @Input({ required: true }) member!: Membership;
  @Input({ required: true }) mode: 'term' | 'renewal' = 'term';
  @Input({ required: true }) draft!: CancellationDraft;
  @Input({ required: true }) today!: string;
  @Input() error = '';
  @Input() busy = false;

  @Output() close = new EventEmitter<void>();
  @Output() confirm = new EventEmitter<void>();
}

