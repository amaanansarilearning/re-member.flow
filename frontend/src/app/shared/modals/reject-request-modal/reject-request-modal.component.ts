import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-reject-request-modal',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './reject-request-modal.component.html',
  styleUrl: './reject-request-modal.component.scss'
})
export class RejectRequestModalComponent {
  @Input({ required: true }) requestId!: number;
  @Input() title = '';
  @Input() eyebrow = 'WORKFLOW OPERATIONS';
  @Input() subtitle = 'Specify reason for audit trail and member notification';
  @Input() reason = '';
  @Input() loading = false;

  @Output() close = new EventEmitter<void>();
  @Output() reasonChange = new EventEmitter<string>();
  @Output() confirm = new EventEmitter<void>();
}

