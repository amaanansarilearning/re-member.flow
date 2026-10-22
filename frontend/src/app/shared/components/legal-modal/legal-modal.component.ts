import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { LegalDocument, LegalDocumentId, LegalSection, COMPLIANCE_COUNTRIES } from '../../../core/models';

@Component({
  selector: 'app-legal-modal',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './legal-modal.component.html',
  styleUrl: './legal-modal.component.scss'
})
export class LegalModalComponent {
  @Input({ required: true }) activeDoc!: LegalDocument;
  @Input({ required: true }) currentTab!: LegalDocumentId;
  @Input({ required: true }) filteredSections!: LegalSection[];
  @Input() searchQuery = '';
  @Input() selectedCountryCode = 'IN';

  readonly complianceCountries = COMPLIANCE_COUNTRIES;

  @Output() close = new EventEmitter<void>();
  @Output() print = new EventEmitter<void>();
  @Output() tabChange = new EventEmitter<LegalDocumentId>();
  @Output() searchQueryChange = new EventEmitter<string>();
  @Output() countryChange = new EventEmitter<string>();
}
