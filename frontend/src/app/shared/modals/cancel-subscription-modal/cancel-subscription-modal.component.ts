import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-cancel-subscription-modal',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './cancel-subscription-modal.component.html',
  styleUrl: './cancel-subscription-modal.component.scss'
})
export class CancelSubscriptionModalComponent {
  @Input() organizationName = '';
  @Input() subscriptionExpiresAt: string | null = null;
  @Input() immediate = false;
  @Input() reason = '';
  @Input() loading = false;
  @Input() error = '';

  @Output() close = new EventEmitter<void>();
  @Output() immediateChange = new EventEmitter<boolean>();
  @Output() reasonChange = new EventEmitter<string>();
  @Output() confirm = new EventEmitter<void>();
}

