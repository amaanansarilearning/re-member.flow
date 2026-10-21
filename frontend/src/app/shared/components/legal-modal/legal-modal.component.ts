import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { LegalDocument, LegalSection } from '../../../core/models';

@Component({
  selector: 'app-legal-modal',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './legal-modal.component.html',
  styleUrl: './legal-modal.component.scss'
})
export class LegalModalComponent {
  @Input({ required: true }) activeDoc!: LegalDocument;
  @Input({ required: true }) currentTab!: 'terms' | 'privacy' | 'disclaimer' | 'acceptable-use';
  @Input({ required: true }) filteredSections!: LegalSection[];
  @Input() searchQuery = '';

  @Output() close = new EventEmitter<void>();
  @Output() print = new EventEmitter<void>();
  @Output() tabChange = new EventEmitter<'terms' | 'privacy' | 'disclaimer' | 'acceptable-use'>();
  @Output() searchQueryChange = new EventEmitter<string>();
}

