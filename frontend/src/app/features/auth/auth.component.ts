import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { COMPLIANCE_COUNTRIES, CountryComplianceOption, LegalDocumentId, resolveComplianceCountry } from '../../core/models';

@Component({
  selector: 'app-auth',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './auth.component.html',
  styleUrl: './auth.component.scss'
})
export class AuthComponent {
  @Input({ required: true }) authMode: 'home' | 'login' | 'forgot-password' | 'register-gym' = 'login';
  @Input({ required: true }) resetStep: 'request' | 'confirm' = 'request';
  @Input() notice = '';
  @Input() error = '';
  @Input() busy = false;
  @Input() developmentResetCode = '';

  @Input() loginEmail = '';
  @Output() loginEmailChange = new EventEmitter<string>();

  @Input() loginPassword = '';
  @Output() loginPasswordChange = new EventEmitter<string>();

  @Input() registerGymDraft = {
    fullName: '',
    email: '',
    phoneNumber: '',
    password: '',
    organizationName: '',
    organizationCode: '',
    branchName: '',
    branchAddress: '',
    subscriptionTier: 'STANDARD' as 'STANDARD' | 'STARTER' | 'PRO' | 'ENTERPRISE' | 'PREMIUM',
    countryCode: 'IN',
    acceptTerms: false
  };

  @Input() resetDraft = {
    email: '',
    code: '',
    newPassword: ''
  };

  readonly complianceCountries = COMPLIANCE_COUNTRIES;

  showLoginPassword = false;
  showRegisterPassword = false;
  showResetPassword = false;

  get selectedCompliance(): CountryComplianceOption {
    return resolveComplianceCountry(this.registerGymDraft.countryCode);
  }

  @Output() login = new EventEmitter<void>();
  @Output() registerGym = new EventEmitter<void>();
  @Output() requestPasswordReset = new EventEmitter<void>();
  @Output() confirmPasswordReset = new EventEmitter<void>();
  @Output() backToHome = new EventEmitter<void>();
  @Output() setAuthMode = new EventEmitter<'home' | 'login' | 'forgot-password' | 'register-gym'>();
  @Output() setResetStep = new EventEmitter<'request' | 'confirm'>();
  @Output() showLogin = new EventEmitter<void>();
  @Output() openLegal = new EventEmitter<LegalDocumentId>();
}
