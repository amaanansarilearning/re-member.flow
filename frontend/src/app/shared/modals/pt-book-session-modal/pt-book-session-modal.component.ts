import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Membership } from '../../../core/models';

@Component({
  selector: 'app-pt-book-session-modal',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './pt-book-session-modal.component.html',
  styleUrl: './pt-book-session-modal.component.scss'
})
export class PtBookSessionModalComponent {
  @Input({ required: true }) trainee!: Membership;
  @Input() scheduledDate = '';
  @Input() startTime = '';
  @Input() endTime = '';
  @Input() notes = '';
  @Input() minDate = '';
  @Input() busy = false;
  @Input() error = '';

  @Output() close = new EventEmitter<void>();
  @Output() scheduledDateChange = new EventEmitter<string>();
  @Output() startTimeChange = new EventEmitter<string>();
  @Output() endTimeChange = new EventEmitter<string>();
  @Output() notesChange = new EventEmitter<string>();
  @Output() submit = new EventEmitter<void>();
}

