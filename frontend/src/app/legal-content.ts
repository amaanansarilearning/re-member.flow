export type { LegalDocument, LegalDocumentId, LegalSection } from './core/models';
import { LegalDocument, LegalDocumentId, LegalSection, resolveComplianceCountry } from './core/models';

export function getLegalDocument(
  docId: LegalDocumentId,
  countryCodeOrJurisdiction?: string | null
): LegalDocument {
  const compliance = resolveComplianceCountry(countryCodeOrJurisdiction);
  const countryName = compliance.name;
  const flag = compliance.flag;
  const framework = compliance.frameworkName;
  const officer = compliance.statutoryOfficerTitle;
  const officerEmail = compliance.officerEmail;
  const governingLaw = compliance.governingLaw;
  const breachSla = compliance.breachNotificationSla;
  const minorAge = compliance.minorAgeThreshold;
  const lastUpdated = 'October 22, 2026';

  switch (docId) {
    case 'terms':
      return {
        id: 'terms',
        badge: `${flag} ${compliance.jurisdictionLabel} · Platform Agreement`,
        title: `Terms of Use & Platform Subscription Agreement (${countryName})`,
        subtitle: `Binding enterprise terms governing use of Re-member Flow software, subscriptions, accounts, and gym operations under ${framework}.`,
        lastUpdated,
        jurisdiction: compliance.jurisdiction,
        jurisdictionLabel: compliance.jurisdictionLabel,
        governingLaw,
        sections: [
          {
            id: 'acceptance',
            title: '1. Acceptance of Terms & Contracting Capacity',
            content: [
              `By accessing, browsing, registering an organization, or logging into the Re-member Flow web application ("Platform", "we", "us", or "our"), you ("Subscriber", "Gym Owner", "Staff User", "Trainer", or "Member") unconditionally agree to be bound by these Terms of Use, our Privacy Policy, our Data Processing Addendum (DPA), and any incorporated schedules.`,
              `These Terms are established under the laws of ${countryName} and are specifically formulated to align with ${governingLaw}.`,
              'If you register an account on behalf of a gym, fitness center, studio, franchise, or commercial business entity, you explicitly represent and warrant that you possess full legal authority and corporate power to bind that entity to these Terms. If you lack such authority or do not agree with any provision herein, you must immediately cease all access and use of the Platform.'
            ]
          },
          {
            id: 'nature-of-service',
            title: '2. Nature of Platform Services & Platform Independence',
            content: [
              'Re-member Flow provides a cloud-based gym management platform designed for health clubs, gym owners, and fitness operations. The Platform enables multi-branch organization oversight, package and membership lifecycle management, access verification, front desk operations, staff permission delegation, personal training scheduling, and automated renewal reminders.',
              'Re-member Flow is purely a technology software provider. We do NOT own, operate, manage, supervise, or inspect any subscriber gym locations, physical facilities, workout floors, or exercise machinery. The physical condition, maintenance, safety, sanitation, and regulatory compliance of any gym premises are the sole, independent responsibility of the respective Gym Organization.'
            ]
          },
          {
            id: 'statutory-compliance',
            title: `3. Regional Statutory Compliance (${framework})`,
            content: [
              `This organization is registered under the ${compliance.jurisdictionLabel} compliance profile.`,
              `DATA ROLES: In operating this platform, the Gym Organization acts as the primary Data Fiduciary / Data Controller regarding all member and staff data. Re-member Flow acts strictly as a Data Processor / Service Provider pursuant to the statutory provisions of ${framework}.`,
              `STATUTORY CONTACT: For all legal, privacy, and regulatory correspondence concerning ${countryName}, contact our designated ${officer} at: ${officerEmail}.`,
              `AGE OF MAJORITY & MINORS: In accordance with statutory rules for ${countryName}, individuals under ${minorAge} years of age are classified as minors. Gym Organizations must obtain verifiable parental or legal guardian consent prior to entering minor profiles into the Platform.`
            ]
          },
          {
            id: 'physical-activity-waiver',
            title: '4. Physical Activity, Health & Bodily Injury Disclaimer (Assumption of Risk)',
            content: [
              'NO MEDICAL OR HEALTHCARE ADVICE: The Platform, its features, access checks, trainer modules, and communications are administrative software tools only and do not constitute medical, diagnostic, physiological, or healthcare advice.',
              'INHERENT RISK OF EXERCISE: Physical conditioning, weight training, cardiovascular exertion, and gym facility usage carry inherent, unavoidable dangers, including but not limited to acute musculoskeletal tears, rhabdomyolysis, cardiovascular incidents, fractures, permanent physical impairment, or death.',
              'VOLUNTARY ASSUMPTION OF RISK: All gym members, personal trainers, staff, and guests utilize gym premises and engage in physical exercises purely at their own voluntary election and assume full personal responsibility for all known and unknown risks.',
              'PLATFORM IMMUNITY FROM PREMISES CLAIMS: To the maximum extent permitted by applicable law, Re-member Flow, its officers, directors, software developers, and affiliates are completely shielded and held harmless from any claims, suits, liabilities, or losses arising from bodily injury, illness, accident, or wrongful death occurring on or in connection with any subscriber gym facility or training session.',
              `CLUB OBLIGATIONS: Gym Owners warrant that their physical clubs hold all required commercial business licenses, maintain comprehensive general liability insurance, and conform to all local safety, fire, electrical, and public health codes applicable in ${countryName}.`
            ]
          },
          {
            id: 'personal-training',
            title: '5. Personal Training (PT) Disclaimers & Independent Status',
            content: [
              'NO EMPLOYMENT OR BROKERAGE RELATIONSHIP: Personal trainers listed or managed in the Platform are either direct employees or independent contractors of the subscriber Gym Organization. Re-member Flow is NOT an employer, joint employer, staffing agency, labor broker, or insurer of any personal trainer.',
              'CREDENTIALS & VETTING: Verification of trainer certifications (e.g., ACE, NASM, ACSM, ISSA, national sports councils), background screenings, CPR/AED certifications, and individual liability coverage is strictly the responsibility of the subscriber Gym Organization.',
              'PAYOUTS & TAXES: The Platform provides calculation tools (such as commission percentage computations and session counters) as administrative conveniences based on user-supplied numbers. Re-member Flow is not an escrow agent, does not handle trainer payroll, and bears zero responsibility for wage disputes, independent contractor misclassifications, or statutory tax withholdings (including TDS, GST, W-2, 1099, or VAT compliance).'
            ]
          },
          {
            id: 'accounts-security',
            title: '6. User Accounts, Access Control & Security',
            content: [
              'The Platform enforces Role-Based Access Control (RBAC) across Owner, Staff, Personal Trainer, Member, and Platform Administrator tiers. Users must only access features and data corresponding to their assigned operational scope.',
              'Users are strictly responsible for maintaining the confidentiality of their login credentials, passwords, and single-use password reset verification codes. Sharing front-desk or staff credentials across multiple individuals is strictly prohibited.',
              'You agree to immediately notify your club administrator or the Platform upon learning of any compromised password, unauthorized session, or security incident. We reserve the right to suspend or revoke credentials suspected of compromise.'
            ]
          },
          {
            id: 'subscriptions-billing',
            title: '7. Platform Subscription Tiers, Activation Codes & Upgrades',
            content: [
              'Certain advanced functionalities (including cross-branch analytics, personal trainer commission management, multi-branch audits, and DPA compliance logs) are reserved for active subscription tiers or authorized commercial activation codes.',
              'Promotional and B2B activation codes issued by the Platform are non-transferable, single-use per organization, and must be redeemed prior to any specified expiration date. The Platform reserves the right to cancel or revoke codes generated fraudulently or in error.',
              'Subscription fees, billing frequencies, and plan entitlements are governed by the active service agreement. The Platform reserves the right to modify subscription fees upon thirty (30) days notice to subscribers.'
            ]
          },
          {
            id: 'member-fees-receipts',
            title: '8. Member Fees, Discounts, Cancellations & Uploaded Receipts',
            content: [
              'INDEPENDENT CLUB PRICING: All package durations (calendar months or custom day counts), membership rates, promotional discounts, freeze rules, and cancellation policies are established independently by each Gym Organization. Re-member Flow is not a party to member agreements and does not mediate disputes regarding membership fees, refunds, or service dissatisfaction.',
              'PAYMENT RECEIPTS: Staff and owners may attach receipt images (JPEG, PNG, or WebP up to 5 MB) as records of offline payments. The uploading user warrants that each image represents a bona fide payment transaction and does not contain unauthorized full primary account numbers (PAN), credit card CVVs, or non-pertinent confidential personal data.'
            ]
          },
          {
            id: 'audit-trails',
            title: '9. Operational Audit Trails & Tamper-Evident History',
            content: [
              'To promote administrative transparency and fraud prevention, the Platform automatically records an immutable audit history detailing operational changes (including package edits, membership date adjustments, manual price corrections, branch transfers, freezes, and cancellations).',
              'These audit records capture timestamps, actor identifiers, reasons, and before/after values. Audit entries cannot be retrospectively altered or deleted by users.'
            ]
          },
          {
            id: 'intellectual-property',
            title: '10. Intellectual Property & Customer Data Ownership',
            content: [
              'PLATFORM INTELLECTUAL PROPERTY: The software, source code, database architecture, user interfaces, branding, visual styling, algorithms, and documentation are the sole and exclusive intellectual property of Re-member Flow and its licensors.',
              'CUSTOMER DATA OWNERSHIP: The Gym Organization retains all ownership rights in its proprietary member lists, branch records, and customer contact information. You grant Re-member Flow a non-exclusive, worldwide, royalty-free license to store, process, transmit, and backup Customer Data solely as necessary to provide and maintain the platform.'
            ]
          },
          {
            id: 'prohibited-conduct',
            title: '11. Acceptable Use & Prohibited Conduct',
            content: [
              'Users agree NOT to: (a) reverse engineer, decompile, or disassemble any part of the Platform; (b) conduct unauthorized security scanning, penetration testing, or stress tests without prior written consent; (c) deploy scrapers, bots, or automated extraction tools; (d) upload malicious software, viruses, or obfuscated payloads; (e) forge headers or manipulate session tokens; (f) utilize the system to transmit unsolicited spam or unlawful content.'
            ]
          },
          {
            id: 'disclaimer-warranties',
            title: '12. Warranty Disclaimers ("AS IS")',
            content: [
              'THE PLATFORM IS PROVIDED ON AN "AS IS" AND "AS AVAILABLE" BASIS, WITHOUT WARRANTIES OF ANY KIND, EITHER EXPRESS, IMPLIED, STATUTORY, OR OTHERWISE. TO THE MAXIMUM EXTENT PERMITTED BY LAW, RE-MEMBER FLOW DISCLAIMS ALL WARRANTIES, INCLUDING MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, TITLE, ACCURACY, AND NON-INFRINGEMENT.',
              'WE DO NOT WARRANT THAT THE SERVICE WILL BE UNINTERRUPTED, TIMELY, SECURE, OR ERROR-FREE, OR THAT STORED DATA WILL NEVER BE SUBJECT TO LOSS OR UNAVAILABILITY. ROUTINE SYSTEM MAINTENANCE, CLOUD INFRASTRUCTURE OUTAGES, OR NETWORK DISRUPTIONS MAY OCCASIONALLY IMPAIR ACCESS.'
            ]
          },
          {
            id: 'limitation-liability',
            title: '13. Limitation of Liability',
            content: [
              'TO THE FULLEST EXTENT PERMITTED BY LAW, IN NO EVENT SHALL RE-MEMBER FLOW, ITS PARENT ENTITY, DIRECTORS, EMPLOYEES, CONTRACTORS, OR AGENTS BE LIABLE FOR ANY INDIRECT, CONSEQUENTIAL, INCIDENTAL, SPECIAL, EXEMPLARY, OR PUNITIVE DAMAGES (INCLUDING LOSS OF PROFITS, LOSS OF REVENUE, LOSS OF GOODWILL, LOSS OF DATA, OR BUSINESS INTERRUPTION), REGARDLESS OF THE LEGAL THEORY.',
              'IN ALL CIRCUMSTANCES, OUR TOTAL AGGREGATE LIABILITY ARISING OUT OF OR RELATING TO THESE TERMS OR THE USE OF THE PLATFORM SHALL BE STRICTLY LIMITED TO THE GREATER OF: (A) THE TOTAL AMOUNT ACTUALLY PAID BY THE SUBSCRIBER TO RE-MEMBER FLOW IN THE TWELVE (12) MONTHS IMMEDIATELY PRECEDING THE OCCURRENCE GIVING RISE TO THE CLAIM; OR (B) ONE HUNDRED UNITED STATES DOLLARS ($100.00 USD) OR LOCAL CURRENCY EQUIVALENT.'
            ]
          },
          {
            id: 'indemnification',
            title: '14. Indemnification by Gym Organization',
            content: [
              'The Gym Organization agrees to defend, indemnify, and hold harmless Re-member Flow, its officers, employees, and software providers from and against all third-party claims, liabilities, damages, judgments, and legal expenses (including reasonable attorney fees) resulting from: (a) bodily injury, death, or property damage occurring at the Subscriber\'s gym facility or during trainer-led workouts; (b) Subscriber\'s violation of these Terms or applicable privacy and consumer protection statutes; (c) any wage, tax, or employment classification disputes with personal trainers or staff; or (d) any billing, refund, or service dispute with gym members.'
            ]
          },
          {
            id: 'governing-law',
            title: `15. Governing Law, Dispute Resolution & Jurisdiction (${countryName})`,
            content: [
              `These Terms and any non-contractual obligations arising out of or in connection with them shall be governed by, and construed in accordance with, ${governingLaw}.`,
              `Any dispute, controversy, or claim arising out of or relating to these Terms shall be submitted to the exclusive jurisdiction of the competent courts of the capital commercial district designated under ${governingLaw}, or resolved through binding commercial arbitration in accordance with applicable statutory arbitration rules.`,
              'The parties mutually agree that any proceedings will be conducted on an individual basis and not as a class action or representative proceeding.'
            ]
          },
          {
            id: 'contact',
            title: '16. Legal Contacts & Statutory Officer',
            content: [
              `For statutory compliance notices under ${framework}, contact our ${officer}:`,
              `• Email: ${officerEmail}`,
              `• General Legal Counsel: legal@rememberflow.com`,
              '• Support Portal: Built-in ticketing system via application dashboard'
            ]
          }
        ]
      };

    case 'privacy':
      return {
        id: 'privacy',
        badge: `${flag} ${compliance.jurisdictionLabel} · Data Protection Notice`,
        title: `Privacy Policy & Data Protection Notice (${countryName})`,
        subtitle: `Disclosures regarding data collection, fiduciary duties, processing bases, and individual rights under ${framework}.`,
        lastUpdated,
        jurisdiction: compliance.jurisdiction,
        jurisdictionLabel: compliance.jurisdictionLabel,
        governingLaw,
        sections: [
          {
            id: 'scope-and-roles',
            title: '1. Scope & Legal Roles (Data Controller/Fiduciary vs. Data Processor)',
            content: [
              `This Privacy Policy governs the processing of personal data across the Re-member Flow software platform under the legal framework of ${framework} in ${countryName}.`,
              'DATA CONTROLLER / DATA FIDUCIARY: When gym owners register an organization and manage member lists, branch records, trainer assignments, and membership finances, the Gym Organization acts as the Data Controller (or Data Fiduciary under India DPDP Act 2023). The Gym Organization determines the purpose and means of collecting member data.',
              'DATA PROCESSOR / SERVICE PROVIDER: Re-member Flow acts strictly as a Data Processor (or Data Intermediary / Service Provider) processing personal data solely on documented instructions from the Gym Organization pursuant to our Data Processing Addendum (DPA).',
              'DIRECT ACCOUNT DATA: Re-member Flow acts as Data Controller only for the business contact details of gym owners who directly subscribe to our platform.'
            ]
          },
          {
            id: 'categories-of-data',
            title: '2. Categories of Personal Data Processed',
            content: [
              'MEMBER & STAFF PROFILES: Full name, phone number, email address, assigned branch, assigned role, membership status, and member PIN.',
              'MEMBERSHIP & SUBSCRIPTION METRICS: Plan names, package validity dates, start/end timestamps, freeze intervals, cancellations, discounts, and payments.',
              'OFFLINE PAYMENT PROOF: Uploaded image receipts (JPEG, PNG, WebP) of point-of-sale, bank transfer, or cash payments. The platform never stores unmasked credit card PANs or CVVs.',
              'PERSONAL TRAINING LOGS: Trainer assignments, scheduled sessions, workout notes, and completion records.',
              'SECURITY & AUDIT TRAILS: Salted BCrypt password hashes, signed 12-hour JWT tokens, IP access logs, and tamper-evident administrative audit records.'
            ]
          },
          {
            id: 'lawful-bases',
            title: `3. Lawful Bases for Processing (${framework})`,
            content: [
              'PERFORMANCE OF CONTRACT: Processing is necessary to provide access to facilities, verify membership validity, send renewal notices, and log training sessions.',
              'LEGITIMATE BUSINESS USES & COMPLIANCE: Maintaining audit logs for financial accounting, fraud prevention, and system security.',
              'NOTICE & EXPLICIT CONSENT: Transparent consent given at the point of member onboarding and voluntary health readiness disclosures.',
              `STATUTORY SAFEGUARDS: Adherence to ${compliance.dataResidencyNote}.`
            ]
          },
          {
            id: 'minor-protection',
            title: `4. Children & Minors Data Protection (${minorAge} Years Threshold)`,
            content: [
              `Under ${framework}, persons under ${minorAge} years of age are protected as minors.`,
              'Gym Organizations MUST obtain verifiable parental or guardian consent before creating profiles for minors.',
              'Behavioral tracking, targeted advertising, or harmful profiling of children is strictly prohibited across all tiers of the Platform.'
            ]
          },
          {
            id: 'security-and-retention',
            title: '5. Technical Security Measures & Data Retention',
            content: [
              'ENCRYPTION IN TRANSIT & AT REST: All web and API traffic is strictly encrypted using TLS 1.2/1.3. Sensitive credentials and password reset OTPs are securely hashed using BCrypt.',
              'MULTI-TENANT ISOLATION: Strict database query partitioning ensures data of one gym organization is never visible or accessible to another.',
              'DATA RETENTION: Member data is maintained for the life of the gym subscription. Upon contract expiration, data is preserved for a 30-day export grace period before permanent purge, except where statutory financial retention (up to 7 years) is required by law.'
            ]
          },
          {
            id: 'data-subject-rights',
            title: `6. Data Subject / Data Principal Rights (${countryName})`,
            content: [
              `Under ${framework}, individuals possess enforceable statutory rights:`,
              '• RIGHT TO ACCESS: Summary of personal data processed and processing activities undertaken.',
              '• RIGHT TO CORRECTION & ERASURE: Correction of inaccurate personal data and erasure ("Right to be Forgotten") when purpose is fulfilled.',
              '• RIGHT OF GRIEVANCE REDRESSAL: Access to a designated statutory officer to address concerns within statutory time limits.',
              '• RIGHT TO NOMINATE: Right to designate a representative in case of death or incapacity (DPDP § 14).',
              '• RIGHT TO WITHDRAW CONSENT: Easy mechanism to revoke previously granted voluntary consents.'
            ]
          },
          {
            id: 'breach-notification',
            title: `7. Breach Notification Obligations (${breachSla})`,
            content: [
              `Re-member Flow maintains rigorous incident response procedures. In the event of a confirmed personal data breach affecting gym records, Re-member Flow will notify the affected Gym Organization without undue delay.`,
              `Statutory notification SLA: ${breachSla}.`
            ]
          },
          {
            id: 'dpo-redressal',
            title: `8. Statutory Officer & Contact Details`,
            content: [
              `To exercise your rights or file a grievance under ${framework}, contact:`,
              `• Designated Officer: ${officer}`,
              `• Dedicated Email: ${officerEmail}`,
              `• Privacy Team: privacy@rememberflow.com`,
              `Responses will be furnished in accordance with statutory timeframes mandated under ${governingLaw}.`
            ]
          }
        ]
      };

    case 'dpa':
      return {
        id: 'dpa',
        badge: `${flag} ${compliance.jurisdictionLabel} · Data Processing Addendum`,
        title: `Data Processing Addendum (DPA) (${countryName})`,
        subtitle: `Binding B2B Data Processor agreement between Re-member Flow (Processor) and Gym Organization (Controller/Fiduciary) pursuant to ${framework}.`,
        lastUpdated,
        jurisdiction: compliance.jurisdiction,
        jurisdictionLabel: compliance.jurisdictionLabel,
        governingLaw,
        sections: [
          {
            id: 'preamble-scope',
            title: '1. Preamble & Roles of the Parties',
            content: [
              `This Data Processing Addendum ("DPA") supplements the Re-member Flow Terms of Use between the Gym Organization ("Customer", "Data Controller", or "Data Fiduciary") and Re-member Flow ("Service Provider", "Data Processor").`,
              `This DPA is executed in strict compliance with the statutory provisions of ${framework} and applies to all processing of Customer Personal Data conducted through the Platform.`,
              'In the event of any conflict between the main Terms of Use and this DPA regarding personal data protection, the terms of this DPA shall govern.'
            ]
          },
          {
            id: 'processor-obligations',
            title: '2. Obligations & Instructions of the Data Processor',
            content: [
              'PROCESSING ON DOCUMENTED INSTRUCTIONS: Re-member Flow shall process Customer Personal Data solely on documented instructions from the Customer, including with respect to data transfers, unless required to do so by applicable statutory law.',
              'CONFIDENTIALITY: Re-member Flow ensures that all personnel authorized to process Customer Personal Data are under appropriate statutory or contractual confidentiality obligations.',
              'PURPOSE LIMITATION: Re-member Flow shall never process Customer Personal Data for secondary commercial purposes, marketing, advertising, or selling/sharing.'
            ]
          },
          {
            id: 'security-measures-art32',
            title: '3. Technical & Organizational Measures (TOMs)',
            content: [
              'Re-member Flow implements and maintains technical and organizational measures to ensure a level of security appropriate to the risk, including:',
              '• Pseudonymization, tokenization, and encryption of personal data in transit (TLS 1.3) and passwords at rest (salted BCrypt).',
              '• Strict logical database multi-tenancy preventing unauthorized cross-tenant data access.',
              '• Role-Based Access Controls (RBAC) separating administrative, staff, trainer, and member capabilities.',
              '• Tamper-evident operational audit logs recording all modifications to memberships, packages, and payments.',
              '• Automated daily backups with encrypted offsite replication.'
            ]
          },
          {
            id: 'subprocessors',
            title: '4. Sub-Processors',
            content: [
              'Customer provides general authorization for Re-member Flow to engage vetted infrastructure sub-processors (cloud compute providers, managed database services, transactional SMTP mail services).',
              'Re-member Flow imposes on each sub-processor data protection obligations no less protective than those established in this DPA.',
              'Re-member Flow remains fully liable to the Customer for the performance of each sub-processor\'s obligations.'
            ]
          },
          {
            id: 'incident-notification',
            title: `5. Security Incident & Breach Notification (${breachSla})`,
            content: [
              `Re-member Flow will notify the Customer without undue delay upon confirming a personal data breach affecting Customer Personal Data.`,
              `SLA: ${breachSla}.`,
              'The notification shall describe: (a) the nature of the security incident; (b) the categories and approximate number of data subjects affected; (c) the likely consequences; and (d) remedial measures implemented or proposed.'
            ]
          },
          {
            id: 'controller-assistance',
            title: '6. Assistance with Data Subject Rights & Regulatory Audits',
            content: [
              'ASSISTANCE WITH REQUESTS: Re-member Flow provides built-in tools (CSV data export, profile rectification, member cancellation) enabling the Customer to fulfill requests from Data Principals / Data Subjects under applicable privacy laws.',
              'AUDIT & COMPLIANCE VERIFICATION: Upon reasonable written notice, Re-member Flow will provide Customer with relevant summary security certifications and audit reports to demonstrate compliance with this DPA.'
            ]
          },
          {
            id: 'data-return-deletion',
            title: '7. Data Deletion & Return upon Termination',
            content: [
              'Upon termination of the subscription agreement, Re-member Flow shall, at the Customer\'s choice, securely return or delete all Customer Personal Data within thirty (30) days, unless applicable statutory law requires retention of certain financial or accounting records (up to 7 years).'
            ]
          },
          {
            id: 'cross-border',
            title: `8. Cross-Border Data Transfers & Statutory Safeguards`,
            content: [
              `Data transfers are governed by ${compliance.dataResidencyNote}.`,
              'Where data transfers cross national borders, the parties agree to be bound by standard contractual clauses, international data transfer agreements, or statutory whitelist mechanisms approved by the competent supervisory authority.'
            ]
          }
        ]
      };

    case 'disclaimer':
      return {
        id: 'disclaimer',
        badge: `${flag} ${compliance.jurisdictionLabel} · Health & Safety Waiver`,
        title: `Health, Fitness & Physical Liability Waiver (${countryName})`,
        subtitle: `Important health disclosures, medical clearance notices, and assumption of physical risk under ${governingLaw}.`,
        lastUpdated,
        jurisdiction: compliance.jurisdiction,
        jurisdictionLabel: compliance.jurisdictionLabel,
        governingLaw,
        sections: [
          {
            id: 'medical-clearance',
            title: '1. Medical Clearance & Physical Activity Readiness (PAR-Q)',
            content: [
              'CONSULT A PHYSICIAN BEFORE EXERCISE: Strenuous physical exertion, weightlifting, resistance training, cardiovascular intervals, and athletic movement place significant stress on the human body. All individuals are strongly advised to consult a licensed medical physician and complete a Physical Activity Readiness Questionnaire (PAR-Q) before beginning any fitness program or utilizing gym equipment.',
              'PRE-EXISTING CONDITIONS: Anyone with a history of cardiovascular disease, chest pain, high blood pressure, dizziness, joint disease, spinal conditions, respiratory disorders, or pregnancy must obtain explicit medical clearance prior to engaging in physical activity.'
            ]
          },
          {
            id: 'assumption-of-risk',
            title: '2. Express Voluntary Assumption of Physical Risk',
            content: [
              'Gym members, personal trainers, staff, and visitors acknowledge that physical exercise and gym facility environments carry inherent, unavoidable dangers, including but not limited to:',
              '• Muscle strains, ligament sprains, tendon tears, and rhabdomyolysis.',
              '• Broken bones, joint dislocations, spinal injuries, and head trauma.',
              '• Cardiovascular distress, heat exhaustion, dehydration, stroke, cardiac arrest, or sudden death.',
              '• Slip, trip, and fall hazards on wet surfaces, locker rooms, or around free weights.',
              '• Equipment malfunctions, cable snaps, or dropping of heavy barbell weights.',
              'By entering a gym facility and participating in fitness activities, users voluntarily and knowingly assume all such risks, both known and unknown.'
            ]
          },
          {
            id: 'platform-release',
            title: '3. Complete Release of Platform Liability',
            content: [
              'Re-member Flow is a software provider and does NOT own, manage, maintain, or inspect gym facilities, exercise equipment, dumbbells, weight machines, or locker rooms.',
              'Under no circumstances shall Re-member Flow, its parent company, developers, or affiliates be liable for any physical injury, medical emergency, chronic condition aggravation, or wrongful death occurring at any subscriber facility or during any workout session organized through this software.',
              `Gym operators in ${countryName} are solely responsible for ensuring equipment safety, regular machine inspections, emergency first aid readiness (including accessible AEDs), and sanitary facilities.`
            ]
          },
          {
            id: 'trainer-guidance',
            title: '4. Personal Training Guidance & Form Disclaimer',
            content: [
              'Personal trainers and gym staff provide training guidance independently of Re-member Flow. The software platform does not formulate, review, or endorse workout routines, nutritional plans, or lifting regimens.',
              'Members are responsible for listening to their own bodies, exercising within their personal physical limits, and promptly discontinuing any activity that causes pain, nausea, dizziness, or acute distress.'
            ]
          }
        ]
      };

    case 'acceptable-use':
      return {
        id: 'acceptable-use',
        badge: `${flag} ${compliance.jurisdictionLabel} · System Integrity`,
        title: `Acceptable Use & System Integrity Policy (${countryName})`,
        subtitle: `Rules governing proper conduct, cybersecurity compliance, and platform integrity under ${governingLaw}.`,
        lastUpdated,
        jurisdiction: compliance.jurisdiction,
        jurisdictionLabel: compliance.jurisdictionLabel,
        governingLaw,
        sections: [
          {
            id: 'core-principles',
            title: '1. Core Principles of System Integrity',
            content: [
              'Re-member Flow provides mission-critical operational tools for gym facilities and members. We enforce strict technical and behavioral standards to maintain the confidentiality, availability, and integrity of all systems and user records.'
            ]
          },
          {
            id: 'prohibited-activities',
            title: '2. Prohibited Security & Technical Conduct',
            content: [
              'Users must NOT engage in any of the following activities:',
              '• VULNERABILITY EXPLOITATION: Probing, scanning, fuzzing, or testing the vulnerability of our APIs, servers, or web applications without explicit written permission.',
              '• DENIAL OF SERVICE: Flooding, brute-forcing, distributed denial of service (DDoS) attacks, or generating excessive API requests designed to degrade service performance.',
              '• MALICIOUS PAYLOADS: Uploading, transmitting, or injecting viruses, trojans, worms, malicious macros, or obfuscated executable scripts.',
              '• AUTHENTICATION BYPASS: Attempting to bypass role boundaries, forge JWT signatures, intercept tokens, or access records belonging to another gym organization.',
              '• AUTOMATED SCRAPING: Using headless browsers, scrapers, crawlers, or bots to harvest data from the application.'
            ]
          },
          {
            id: 'receipt-guidelines',
            title: '3. Uploaded Receipt & Content Standards',
            content: [
              'RECEIPT PHOTO COMPLIANCE: The receipt upload feature is strictly for genuine documentation of customer payment transactions (such as printed POS receipts, cash slips, or UPI/bank transfer screenshots).',
              'PROHIBITED UPLOADS: Uploading photos that contain full credit card numbers, CVVs, inappropriate or offensive imagery, defamatory content, or third-party copyright-infringing materials is strictly prohibited.',
              'VIOLATIONS: Accounts found uploading fraudulent or malicious files will face immediate termination and potential reporting to financial or law enforcement authorities.'
            ]
          },
          {
            id: 'enforcement',
            title: '4. Monitoring, Audit Trails & Enforcement',
            content: [
              'All administrative actions, password changes, membership edits, and login attempts are logged and monitored.',
              `We reserve the right to immediately suspend accounts, revoke tokens, and block IP addresses that violate this policy, and to cooperate fully with law enforcement authorities in ${countryName} investigating cybercrime.`
            ]
          }
        ]
      };
  }
}

// Default export for backwards compatibility
export const ALL_LEGAL_DOCUMENTS: Record<LegalDocumentId, LegalDocument> = {
  terms: getLegalDocument('terms', 'IN'),
  privacy: getLegalDocument('privacy', 'IN'),
  dpa: getLegalDocument('dpa', 'IN'),
  disclaimer: getLegalDocument('disclaimer', 'IN'),
  'acceptable-use': getLegalDocument('acceptable-use', 'IN')
};

export const LEGAL_TERMS_OF_USE = ALL_LEGAL_DOCUMENTS.terms;
export const LEGAL_PRIVACY_POLICY = ALL_LEGAL_DOCUMENTS.privacy;
export const LEGAL_HEALTH_DISCLAIMER = ALL_LEGAL_DOCUMENTS.disclaimer;
export const LEGAL_ACCEPTABLE_USE = ALL_LEGAL_DOCUMENTS['acceptable-use'];
