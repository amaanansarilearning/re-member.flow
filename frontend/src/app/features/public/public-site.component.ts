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

  mobileMenuOpen = false;
  activeFaqIndex: number | null = 0;
  showComparisonMatrix = false;
  activeFeatureCategory: 'all' | 'memberships' | 'trainers' | 'analytics' | 'branches' = 'all';

  toggleMobileMenu(): void {
    this.mobileMenuOpen = !this.mobileMenuOpen;
  }

  closeMobileMenu(): void {
    this.mobileMenuOpen = false;
  }

  toggleFaq(index: number): void {
    this.activeFaqIndex = this.activeFaqIndex === index ? null : index;
  }

  toggleComparisonMatrix(): void {
    this.showComparisonMatrix = !this.showComparisonMatrix;
  }

  setActiveFeatureCategory(cat: 'all' | 'memberships' | 'trainers' | 'analytics' | 'branches'): void {
    this.activeFeatureCategory = cat;
  }
}
