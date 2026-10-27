export type Role = 'OWNER' | 'STAFF' | 'MEMBER' | 'PERSONAL_TRAINER' | 'SUPER_ADMIN';

export interface User {
  id: number;
  fullName: string;
  email: string;
  phoneNumber: string | null;
  role: Role;
  branchId: number | null;
  branchName: string | null;
  organizationId?: number | null;
  organizationName?: string | null;
  organizationLogo?: string | null;
  countryCode?: string | null;
  legalJurisdiction?: string | null;
  grievanceOfficerEmail?: string | null;
  dpoEmail?: string | null;
  subscriptionTier?: 'STANDARD' | 'STARTER' | 'PRO' | 'ENTERPRISE' | 'PREMIUM';
  subscriptionStatus?: 'ACTIVE' | 'TRIAL' | 'EXPIRED' | 'CANCELLED' | 'SUSPENDED';
  subscriptionExpiresAt?: string | null;
  enabledFeatures?: string[];
  memberPin?: string | null;
}

export interface Session {
  token: string;
  user: User;
}

export interface CountryComplianceOption {
  code: string;
  name: string;
  flag: string;
  jurisdiction: string;
  jurisdictionLabel: string;
  frameworkName: string;
  governingLaw: string;
  statutoryOfficerTitle: string;
  officerEmail: string;
  minorAgeThreshold: number;
  breachNotificationSla: string;
  dataResidencyNote: string;
}

export const COMPLIANCE_COUNTRIES: CountryComplianceOption[] = [
  {
    code: 'IN',
    name: 'India',
    flag: '🇮🇳',
    jurisdiction: 'INDIA_DPDP',
    jurisdictionLabel: 'India DPDP Act 2023',
    frameworkName: 'Digital Personal Data Protection Act, 2023',
    governingLaw: 'Laws of India & Digital Personal Data Protection Act 2023',
    statutoryOfficerTitle: 'Grievance Redressal Officer (DPDP § 8(10))',
    officerEmail: 'grievance@rememberflow.com',
    minorAgeThreshold: 18,
    breachNotificationSla: 'Immediate statutory reporting to Data Protection Board of India and affected Data Principals',
    dataResidencyNote: 'Local data processing compliance & no unauthorized cross-border transfers to restricted territories'
  },
  {
    code: 'US',
    name: 'United States',
    flag: '🇺🇸',
    jurisdiction: 'US_CCPA',
    jurisdictionLabel: 'US CCPA / CPRA & State Privacy Laws',
    frameworkName: 'California Consumer Privacy Act (CCPA/CPRA) & US Privacy Standards',
    governingLaw: 'Federal Laws of the United States & State of California',
    statutoryOfficerTitle: 'Chief Privacy Officer (US Compliance)',
    officerEmail: 'privacy@rememberflow.com',
    minorAgeThreshold: 13,
    breachNotificationSla: 'Prompt notice without unreasonable delay pursuant to applicable state statutes',
    dataResidencyNote: 'Service Provider certification: Re-member Flow does not sell or share personal information'
  },
  {
    code: 'GB',
    name: 'United Kingdom',
    flag: '🇬🇧',
    jurisdiction: 'UK_GDPR',
    jurisdictionLabel: 'UK GDPR & Data Protection Act 2018',
    frameworkName: 'UK General Data Protection Regulation (UK GDPR)',
    governingLaw: 'Laws of England and Wales & UK Data Protection Act 2018',
    statutoryOfficerTitle: 'Data Protection Officer (UK DPO & ICO Liaison)',
    officerEmail: 'dpo@rememberflow.com',
    minorAgeThreshold: 13,
    breachNotificationSla: 'Mandatory 72-hour notice to the Information Commissioner’s Office (ICO)',
    dataResidencyNote: 'UK International Data Transfer Agreement (IDTA) & adequacy regulations compliant'
  },
  {
    code: 'EU',
    name: 'European Union (GDPR)',
    flag: '🇪🇺',
    jurisdiction: 'EU_GDPR',
    jurisdictionLabel: 'EU General Data Protection Regulation (GDPR)',
    frameworkName: 'Regulation (EU) 2016/679 (EU GDPR)',
    governingLaw: 'Laws of the European Union & Lead Supervisory Authority',
    statutoryOfficerTitle: 'Data Protection Officer (EU DPO - Art. 37 GDPR)',
    officerEmail: 'dpo@rememberflow.com',
    minorAgeThreshold: 16,
    breachNotificationSla: 'Article 33 mandatory 72-hour notification to Lead Supervisory Authority',
    dataResidencyNote: 'EU-approved Standard Contractual Clauses (SCCs Module 2 - Controller to Processor)'
  },
  {
    code: 'CA',
    name: 'Canada',
    flag: '🇨🇦',
    jurisdiction: 'CANADA_PIPEDA',
    jurisdictionLabel: 'Canada PIPEDA & Quebec Law 25',
    frameworkName: 'Personal Information Protection and Electronic Documents Act (PIPEDA)',
    governingLaw: 'Federal Laws of Canada and Provincial Privacy Statutes',
    statutoryOfficerTitle: 'Privacy Compliance Officer (OPC Liaison)',
    officerEmail: 'privacy-canada@rememberflow.com',
    minorAgeThreshold: 14,
    breachNotificationSla: 'Mandatory breach notification to OPC and affected individuals for real risk of significant harm',
    dataResidencyNote: 'Accountability principle applied across all cloud data transfers'
  },
  {
    code: 'AU',
    name: 'Australia',
    flag: '🇦🇺',
    jurisdiction: 'AUSTRALIA_PRIVACY_ACT',
    jurisdictionLabel: 'Australia Privacy Act 1988 (APPs)',
    frameworkName: 'Privacy Act 1988 (Cth) & Australian Privacy Principles',
    governingLaw: 'Commonwealth of Australia Privacy Act 1988',
    statutoryOfficerTitle: 'Privacy Officer (OAIC Liaison)',
    officerEmail: 'privacy-au@rememberflow.com',
    minorAgeThreshold: 15,
    breachNotificationSla: 'Notifiable Data Breaches (NDB) scheme report to OAIC without delay',
    dataResidencyNote: 'Strict APP 8 cross-border disclosure accountability'
  },
  {
    code: 'AE',
    name: 'United Arab Emirates',
    flag: '🇦🇪',
    jurisdiction: 'UAE_PDPL',
    jurisdictionLabel: 'UAE PDPL (Federal Decree-Law No. 45/2021)',
    frameworkName: 'UAE Personal Data Protection Law (PDPL)',
    governingLaw: 'Federal Laws of the United Arab Emirates & UAE Data Office',
    statutoryOfficerTitle: 'Data Protection Officer (UAE Data Office Liaison)',
    officerEmail: 'compliance-uae@rememberflow.com',
    minorAgeThreshold: 18,
    breachNotificationSla: 'Immediate statutory notification to the UAE Data Office upon becoming aware of a breach',
    dataResidencyNote: 'Compliance with cross-border data transfer controls under Executive Regulations'
  },
  {
    code: 'GLOBAL',
    name: 'Global / Other International',
    flag: '🌐',
    jurisdiction: 'GLOBAL_STANDARD',
    jurisdictionLabel: 'International Privacy Standard',
    frameworkName: 'ISO/IEC 27701 & International Privacy Framework',
    governingLaw: 'International Commercial Law & Standard Cloud Terms',
    statutoryOfficerTitle: 'Global Data Protection Officer',
    officerEmail: 'privacy@rememberflow.com',
    minorAgeThreshold: 16,
    breachNotificationSla: 'Expeditious breach notice within 72 hours of verification',
    dataResidencyNote: 'Encrypted multi-region cloud security adhering to SOC 2 / ISO 27001 controls'
  }
];

export function resolveComplianceCountry(countryCodeOrJurisdiction?: string | null): CountryComplianceOption {
  if (!countryCodeOrJurisdiction) {
    return COMPLIANCE_COUNTRIES[0]; // IN by default
  }
  const clean = countryCodeOrJurisdiction.trim().toUpperCase();
  const foundByCode = COMPLIANCE_COUNTRIES.find(c => c.code === clean);
  if (foundByCode) return foundByCode;
  const foundByJurisdiction = COMPLIANCE_COUNTRIES.find(c => c.jurisdiction === clean);
  if (foundByJurisdiction) return foundByJurisdiction;

  // EU country codes map to EU
  const euCodes = ['DE', 'FR', 'ES', 'IT', 'NL', 'IE', 'SE', 'PL', 'BE', 'AT', 'PT', 'DK', 'FI'];
  if (euCodes.includes(clean)) {
    return COMPLIANCE_COUNTRIES.find(c => c.code === 'EU')!;
  }

  return COMPLIANCE_COUNTRIES[0];
}

export interface GymRegistrationOtpRequest {
  email: string;
  organizationCode?: string;
}

export interface GymRegistrationOtpResponse {
  message: string;
  developmentCode?: string | null;
}

export interface GymRegistrationPayload {
  fullName: string;
  email: string;
  phoneNumber?: string;
  password: string;
  organizationName: string;
  organizationCode: string;
  branchName?: string;
  branchAddress?: string;
  subscriptionTier?: 'STANDARD' | 'STARTER' | 'PRO' | 'ENTERPRISE' | 'PREMIUM';
  countryCode?: string;
  verificationCode?: string;
}

export interface ProfilePayload {
  fullName: string;
  email: string;
  phoneNumber: string;
}

export interface ResetRequestResponse {
  message: string;
  developmentCode: string | null;
}

export interface StaffPayload {
  fullName: string;
  email: string;
  phoneNumber: string;
  password: string;
  branchId: number;
}
