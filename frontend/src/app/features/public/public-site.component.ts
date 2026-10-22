import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { LegalDocumentId } from '../../core/models';

@Component({
  selector: 'app-public-site',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './public-site.component.html',
  styleUrl: './public-site.component.scss'
})
export class PublicSiteComponent {
  @Input() pricingCycle: 'ANNUAL' | 'MONTHLY' = 'ANNUAL';

  @Output() signIn = new EventEmitter<void>();
  @Output() registerGym = new EventEmitter<void>();
  @Output() togglePricingCycle = new EventEmitter<void>();
  @Output() openLegal = new EventEmitter<LegalDocumentId>();
}
