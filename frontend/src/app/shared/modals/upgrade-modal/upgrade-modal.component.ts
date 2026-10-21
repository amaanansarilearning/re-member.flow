import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { SaasUpgradeQuote, UpgradeRequest, User } from '../../../core/models';
import { money } from '../../../core/utils/formatters';

export type UpgradeTargetTier = 'STARTER' | 'PRO' | 'ENTERPRISE';
export type UpgradeModalTab = 'request' | 'compare' | 'redeem';
export type UpgradeBillingPlan = 'ANNUAL' | 'HALF_YEAR' | 'MONTHLY';
export type UpgradePaymentMethod = 'BANK_WIRE' | 'UPI_QR' | 'CORPORATE_INVOICE';

@Component({
  selector: 'app-upgrade-modal',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './upgrade-modal.component.html',
  styleUrl: './upgrade-modal.component.scss'
})
export class UpgradeModalComponent {
  @Input({ required: true }) targetTier: UpgradeTargetTier = 'PRO';
  @Input({ required: true }) tab: UpgradeModalTab = 'request';
  @Input({ required: true }) billingPlan: UpgradeBillingPlan = 'ANNUAL';
  @Input() pendingRequest: UpgradeRequest | null = null;
  @Input() quote: SaasUpgradeQuote | null = null;
  @Input({ required: true }) quoteAmount = 0;
  @Input({ required: true }) quoteMonthlyEquivalent = '';
  @Input({ required: true }) quoteEstimatedExpiry = '';
  @Input() paymentMethod: UpgradePaymentMethod = 'BANK_WIRE';
  @Input() gstNumber = '';
  @Input() billingAddress = '';
  @Input() notes = '';
  @Input() redeemCode = '';
  @Input() submitting = false;
  @Input() redeeming = false;
  @Input() user: User | null = null;
  @Input() error = '';

  @Output() close = new EventEmitter<void>();
  @Output() targetTierChange = new EventEmitter<UpgradeTargetTier>();
  @Output() tabChange = new EventEmitter<UpgradeModalTab>();
  @Output() billingPlanChange = new EventEmitter<UpgradeBillingPlan>();
  @Output() paymentMethodChange = new EventEmitter<UpgradePaymentMethod>();
  @Output() gstNumberChange = new EventEmitter<string>();
  @Output() billingAddressChange = new EventEmitter<string>();
  @Output() notesChange = new EventEmitter<string>();
  @Output() redeemCodeChange = new EventEmitter<string>();
  @Output() submitUpgrade = new EventEmitter<void>();
  @Output() submitRedeem = new EventEmitter<void>();

  money = money;
  copiedField = '';

  tierBadgeLabel(tier?: string): string {
    switch (tier) {
      case 'STARTER': return '⭐ STARTER';
      case 'PRO': return '⚡ PRO';
      case 'ENTERPRISE': return '👑 ENTERPRISE';
      default: return '★ COMMERCIAL';
    }
  }

  tierDisplayName(tier?: string): string {
    switch (tier) {
      case 'STARTER': return 'Starter';
      case 'PRO': return 'Pro';
      case 'ENTERPRISE': return 'Enterprise';
      default: return 'Pro';
    }
  }

  selectTier(tier: UpgradeTargetTier): void {
    this.targetTier = tier;
    this.targetTierChange.emit(tier);
    this.tab = 'request';
    this.tabChange.emit('request');
  }

  copySettlement(field: string, text: string): void {
    if (typeof navigator !== 'undefined' && navigator.clipboard) {
      void navigator.clipboard.writeText(text);
    }
    this.copiedField = field;
    setTimeout(() => {
      if (this.copiedField === field) {
        this.copiedField = '';
      }
    }, 2000);
  }

  async pasteRedeem(): Promise<void> {
    if (typeof navigator !== 'undefined' && navigator.clipboard && navigator.clipboard.readText) {
      try {
        const text = await navigator.clipboard.readText();
        if (text) {
          const trimmed = text.trim();
          this.redeemCode = trimmed;
          this.redeemCodeChange.emit(trimmed);
        }
      } catch {
        // clipboard access denied or unavailable
      }
    }
  }
}
