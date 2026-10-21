import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-redeem-modal',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './redeem-modal.component.html',
  styleUrl: './redeem-modal.component.scss'
})
export class RedeemModalComponent {
  @Input({ required: true }) code = '';
  @Input() loading = false;
  @Input() error = '';

  @Output() close = new EventEmitter<void>();
  @Output() codeChange = new EventEmitter<string>();
  @Output() confirm = new EventEmitter<void>();
}

