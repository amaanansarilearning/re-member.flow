import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

export interface PtPayoutDraft {
  ptId: number;
  ptName: string;
  periodFrom: string;
  periodTo: string;
  notes: string;
}

@Component({
  selector: 'app-pt-payout-modal',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './pt-payout-modal.component.html',
  styleUrl: './pt-payout-modal.component.scss'
})
export class PtPayoutModalComponent {
  @Input({ required: true }) draft!: PtPayoutDraft;
  @Input() error = '';
  @Input() busy = false;

  @Output() close = new EventEmitter<void>();
  @Output() submit = new EventEmitter<void>();
}

