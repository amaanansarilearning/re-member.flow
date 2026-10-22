import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MemberPackageUpgradeRequest } from '../../../core/models';
import { dateLabel, money } from '../../../core/utils/formatters';

@Component({
  selector: 'app-member-upgrade-approval-modal',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './member-upgrade-approval-modal.component.html',
  styleUrl: './member-upgrade-approval-modal.component.scss'
})
export class MemberUpgradeApprovalModalComponent {
  @Input({ required: true }) request!: MemberPackageUpgradeRequest;
  @Input() paymentMethod = 'UPI';
  @Input() transactionId = '';
  @Input() notes = '';
  @Input() error = '';
  @Input() busy = false;

  @Output() close = new EventEmitter<void>();
  @Output() confirm = new EventEmitter<void>();
  @Output() reject = new EventEmitter<void>();
  @Output() paymentMethodChange = new EventEmitter<string>();
  @Output() transactionIdChange = new EventEmitter<string>();
  @Output() notesChange = new EventEmitter<string>();

  money = money;
  dateLabel = dateLabel;
}

