import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RenewalRequest } from '../../../core/models';
import { dateLabel, money } from '../../../core/utils/formatters';

@Component({
  selector: 'app-renewal-approval-modal',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './renewal-approval-modal.component.html',
  styleUrl: './renewal-approval-modal.component.scss'
})
export class RenewalApprovalModalComponent {
  @Input({ required: true }) request!: RenewalRequest;
  @Input() discountPercent = 0;
  @Input() paymentMethod = 'UPI';
  @Input() transactionId = '';
  @Input() totalAmount = 0;
  @Input() error = '';
  @Input() busy = false;

  @Output() close = new EventEmitter<void>();
  @Output() confirm = new EventEmitter<void>();
  @Output() reject = new EventEmitter<void>();
  @Output() discountChange = new EventEmitter<number>();
  @Output() paymentMethodChange = new EventEmitter<string>();
  @Output() transactionIdChange = new EventEmitter<string>();

  money = money;
  dateLabel = dateLabel;
}

