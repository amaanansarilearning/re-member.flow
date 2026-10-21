import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

export interface GenerateCodeDraft {
  tier: string;
  durationDays: number | null;
  notes: string;
}

@Component({
  selector: 'app-generate-code-modal',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './generate-code-modal.component.html',
  styleUrl: './generate-code-modal.component.scss'
})
export class GenerateCodeModalComponent {
  @Input({ required: true }) draft!: GenerateCodeDraft;
  @Input() loading = false;
  @Input() error = '';

  @Output() close = new EventEmitter<void>();
  @Output() confirm = new EventEmitter<void>();
  @Output() presetDuration = new EventEmitter<number | null>();

  setPreset(days: number | null): void {
    this.draft.durationDays = days;
    this.presetDuration.emit(days);
  }
}

