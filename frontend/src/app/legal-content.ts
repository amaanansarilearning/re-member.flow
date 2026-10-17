export interface LegalSection {
  id: string;
  title: string;
  content: string[];
}

export interface LegalDocument {
  id: 'terms' | 'privacy' | 'disclaimer' | 'acceptable-use';
  badge: string;
  title: string;
  subtitle: string;
  lastUpdated: string;
  sections: LegalSection[];
}

export const LEGAL_TERMS_OF_USE: LegalDocument = {
  id: 'terms',
  badge: 'SaaS Agreement',
  title: 'Terms of Use & SaaS Agreement',
  subtitle: 'Binding terms governing use of Re-member Flow software, subscriptions, accounts, and gym operations.',
  lastUpdated: 'October 17, 2026',
  sections: [
    {
      id: 'acceptance',
      title: '1. Acceptance of Terms & Contracting Capacity',
      content: [
        'By accessing, browsing, registering an organization, or logging into the Re-member Flow web application ("Platform", "we", "us", or "our"), you ("Subscriber", "Gym Owner", "Staff User", "Trainer", or "Member") unconditionally agree to be bound by these Terms of Use, our Privacy Policy, and any incorporated schedules.',
        'If you register an account on behalf of a gym, fitness center, studio, franchise, or commercial business entity, you explicitly represent and warrant that you possess full legal authority and corporate power to bind that entity to these Terms. If you lack such authority or do not agree with any provision herein, you must immediately cease all access and use of the Platform.'
      ]
    },
    {
      id: 'nature-of-service',
      title: '2. Nature of SaaS Services & Platform Independence',
      content: [
        'Re-member Flow provides a cloud-based software-as-a-service (SaaS) management portal designed for health clubs, gym owners, and fitness operations. The Platform enables multi-branch organization oversight, package and membership lifecycle management, access verification, front desk operations, staff permission delegation, personal training scheduling, and automated renewal reminders.',
        'Re-member Flow is purely a technology software provider. We do NOT own, operate, manage, supervise, or inspect any subscriber gym locations, physical facilities, workout floors, or exercise machinery. The physical condition, maintenance, safety, sanitation, and regulatory compliance of any gym premises are the sole, independent responsibility of the respective Gym Organization.'
      ]
    },
    {
      id: 'physical-activity-waiver',
      title: '3. Physical Activity, Health & Bodily Injury Disclaimer (Assumption of Risk)',
      content: [
        'NO MEDICAL OR HEALTHCARE ADVICE: The Platform, its features, access checks, trainer modules, and communications are administrative software tools only and do not constitute medical, diagnostic, physiological, or healthcare advice.',
        'INHERENT RISK OF EXERCISE: Physical conditioning, weight training, cardiovascular exertion, and gym facility usage carry inherent, unavoidable dangers, including but not limited to acute musculoskeletal tears, rhabdomyolysis, cardiovascular incidents, fractures, permanent physical impairment, or death.',
        'VOLUNTARY ASSUMPTION OF RISK: All gym members, personal trainers, staff, and guests utilize gym premises and engage in physical exercises purely at their own voluntary election and assume full personal responsibility for all known and unknown risks.',
        'PLATFORM IMMUNITY FROM PREMISES CLAIMS: To the maximum extent permitted by applicable law, Re-member Flow, its officers, directors, software developers, and affiliates are completely shielded and held harmless from any claims, suits, liabilities, or losses arising from bodily injury, illness, accident, or wrongful death occurring on or in connection with any subscriber gym facility or training session.',
        'CLUB OBLIGATIONS: Gym Owners warrant that their physical clubs hold all required commercial business licenses, maintain comprehensive general liability insurance, and conform to all local safety, fire, electrical, and public health codes.'
      ]
    },
    {
      id: 'personal-training',
      title: '4. Personal Training (PT) Disclaimers & Independent Status',
      content: [
        'NO EMPLOYMENT OR BROKERAGE RELATIONSHIP: Personal trainers listed or managed in the Platform are either direct employees or independent contractors of the subscriber Gym Organization. Re-member Flow is NOT an employer, joint employer, staffing agency, labor broker, or insurer of any personal trainer.',
        'CREDENTIALS & VETTING: Verification of trainer certifications (e.g., ACE, NASM, ACSM, ISSA), background screenings, CPR/AED certifications, and individual liability coverage is strictly the responsibility of the subscriber Gym Organization.',
        'PAYOUTS & TAXES: The Platform provides calculation tools (such as commission percentage computations and session counters) as administrative conveniences based on user-supplied numbers. Re-member Flow is not an escrow agent, does not handle trainer payroll, and bears zero responsibility for wage disputes, independent contractor misclassifications, or statutory tax withholdings (including TDS, GST, W-2, or 1099 compliance).'
      ]
    },
    {
      id: 'accounts-security',
      title: '5. User Accounts, Access Control & Security',
      content: [
        'The Platform enforces Role-Based Access Control (RBAC) across Owner, Staff, Personal Trainer, Member, and Platform Administrator tiers. Users must only access features and data corresponding to their assigned operational scope.',
        'Users are strictly responsible for maintaining the confidentiality of their login credentials, passwords, and single-use password reset verification codes. Sharing front-desk or staff credentials across multiple individuals is prohibited.',
        'You agree to immediately notify your club administrator or the Platform upon learning of any compromised password, unauthorized session, or security incident. We reserve the right to suspend or revoke credentials suspected of compromise.'
      ]
    },
    {
      id: 'subscriptions-billing',
      title: '6. Platform Subscription Tiers, Activation Codes & Upgrades',
      content: [
        'Certain advanced functionalities (including cross-branch analytics, personal trainer commission management, and deep audit history) are reserved for premium subscription tiers or authorized commercial activation codes.',
        'Promotional and B2B activation codes issued by the Platform are non-transferable, single-use per organization, and must be redeemed prior to any specified expiration date. The Platform reserves the right to cancel or revoke codes generated fraudulently or in error.',
        'Subscription fees, billing frequencies, and plan entitlements are governed by the active service agreement. The Platform reserves the right to modify subscription fees upon thirty (30) days notice to subscribers.'
      ]
    },
    {
      id: 'member-fees-receipts',
      title: '7. Member Fees, Discounts, Cancellations & Uploaded Receipts',
      content: [
        'INDEPENDENT CLUB PRICING: All package durations (calendar months or custom day counts), membership rates, promotional discounts, freeze rules, and cancellation policies are established independently by each Gym Organization. Re-member Flow is not a party to member agreements and does not mediate disputes regarding membership fees, refunds, or service dissatisfaction.',
        'PAYMENT RECEIPTS: Staff and owners may attach receipt images (JPEG, PNG, or WebP up to 5 MB) as records of offline payments. The uploading user warrants that each image represents a bona fide payment transaction and does not contain unauthorized full primary account numbers (PAN), credit card CVVs, or non-pertinent confidential personal data.'
      ]
    },
    {
      id: 'audit-trails',
      title: '8. Operational Audit Trails & Tamper-Evident History',
      content: [
        'To promote administrative transparency and fraud prevention, the Platform automatically records an immutable audit history detailing operational changes (including package edits, membership date adjustments, manual price corrections, branch transfers, freezes, and cancellations).',
        'These audit records capture timestamps, actor identifiers, reasons, and before/after values. Audit entries cannot be retrospectively altered or deleted by users.'
      ]
    },
    {
      id: 'intellectual-property',
      title: '9. Intellectual Property & Customer Data Ownership',
      content: [
        'PLATFORM INTELLECTUAL PROPERTY: The software, source code, database architecture, user interfaces, branding, visual styling, algorithms, and documentation are the sole and exclusive intellectual property of Re-member Flow and its licensors.',
        'CUSTOMER DATA OWNERSHIP: The Gym Organization retains all ownership rights in its proprietary member lists, branch records, and customer contact information. You grant Re-member Flow a non-exclusive, worldwide, royalty-free license to store, process, transmit, and backup Customer Data solely as necessary to provide and maintain the SaaS platform.'
      ]
    },
    {
      id: 'prohibited-conduct',
      title: '10. Acceptable Use & Prohibited Conduct',
      content: [
        'Users agree NOT to: (a) reverse engineer, decompile, or disassemble any part of the Platform; (b) conduct unauthorized security scanning, penetration testing, or stress tests without prior written consent; (c) deploy scrapers, bots, or automated extraction tools; (d) upload malicious software, viruses, or obfuscated payloads; (e) forge headers or manipulate session tokens; (f) utilize the system to transmit unsolicited spam or unlawful content.'
      ]
    },
    {
      id: 'disclaimer-warranties',
      title: '11. Warranty Disclaimers ("AS IS")',
      content: [
        'THE PLATFORM IS PROVIDED ON AN "AS IS" AND "AS AVAILABLE" BASIS, WITHOUT WARRANTIES OF ANY KIND, EITHER EXPRESS, IMPLIED, STATUTORY, OR OTHERWISE. TO THE MAXIMUM EXTENT PERMITTED BY LAW, RE-MEMBER FLOW DISCLAIMS ALL WARRANTIES, INCLUDING MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, TITLE, ACCURACY, AND NON-INFRINGEMENT.',
        'WE DO NOT WARRANT THAT THE SERVICE WILL BE UNINTERRUPTED, TIMELY, SECURE, OR ERROR-FREE, OR THAT STORED DATA WILL NEVER BE SUBJECT TO LOSS OR UNAVAILABILITY. ROUTINE SYSTEM MAINTENANCE, CLOUD INFRASTRUCTURE OUTAGES, OR NETWORK DISRUPTIONS MAY OCCASIONALLY IMPAIR ACCESS.'
      ]
    },
    {
      id: 'limitation-liability',
      title: '12. Limitation of Liability',
      content: [
        'TO THE FULLEST EXTENT PERMITTED BY LAW, IN NO EVENT SHALL RE-MEMBER FLOW, ITS PARENT ENTITY, DIRECTORS, EMPLOYEES, CONTRACTORS, OR AGENTS BE LIABLE FOR ANY INDIRECT, CONSEQUENTIAL, INCIDENTAL, SPECIAL, EXEMPLARY, OR PUNITIVE DAMAGES (INCLUDING LOSS OF PROFITS, LOSS OF REVENUE, LOSS OF GOODWILL, LOSS OF DATA, OR BUSINESS INTERRUPTION), REGARDLESS OF THE LEGAL THEORY.',
        'IN ALL CIRCUMSTANCES, OUR TOTAL AGGREGATE LIABILITY ARISING OUT OF OR RELATING TO THESE TERMS OR THE USE OF THE PLATFORM SHALL BE STRICTLY LIMITED TO THE GREATER OF: (A) THE TOTAL AMOUNT ACTUALLY PAID BY THE SUBSCRIBER TO RE-MEMBER FLOW IN THE TWELVE (12) MONTHS IMMEDIATELY PRECEDING THE OCCURRENCE GIVING RISE TO THE CLAIM; OR (B) ONE HUNDRED UNITED STATES DOLLARS ($100.00 USD) OR LOCAL CURRENCY EQUIVALENT.'
      ]
    },
    {
      id: 'indemnification',
      title: '13. Indemnification by Gym Organization',
      content: [
        'The Gym Organization agrees to defend, indemnify, and hold harmless Re-member Flow, its officers, employees, and software providers from and against all third-party claims, liabilities, damages, judgments, and legal expenses (including reasonable attorney fees) resulting from: (a) bodily injury, death, or property damage occurring at the Subscriber\'s gym facility or during trainer-led workouts; (b) Subscriber\'s violation of these Terms or applicable privacy and consumer protection statutes; (c) any wage, tax, or employment classification disputes with personal trainers or staff; or (d) any billing, refund, or service dispute with gym members.'
      ]
    },
    {
      id: 'termination',
      title: '14. Term, Suspension & Termination',
      content: [
        'The Platform may suspend or terminate any user or organization account immediately if we reasonably suspect fraud, illegal activity, security compromise, or material breach of these Terms.',
        'Upon subscription termination, the subscriber will be granted a standard thirty (30) day window to export customer reports and member lists, after which production records may be scheduled for secure deletion according to our data retention schedule.'
      ]
    },
    {
      id: 'governing-law',
      title: '15. Governing Law, Dispute Resolution & Severability',
      content: [
        'These Terms shall be governed by and construed in accordance with the substantive laws applicable in the jurisdiction of the Platform\'s principal place of operation, without regard to choice of law doctrines.',
        'Any dispute arising from or related to these Terms shall be resolved exclusively through binding individual arbitration or before the competent courts of the Platform\'s primary jurisdiction. The parties mutually waive any right to initiate or participate in class-action lawsuits.',
        'If any provision of these Terms is found to be invalid or unenforceable, that provision shall be severed and the remaining terms shall remain in full force and effect.'
      ]
    },
    {
      id: 'contact',
      title: '16. Legal Contacts & Inquiries',
      content: [
        'For questions regarding these Terms of Use, legal notices, or commercial licensing agreements, please contact: legal@gymflow.io or via the verified support portal in the application.'
      ]
    }
  ]
};

export const LEGAL_PRIVACY_POLICY: LegalDocument = {
  id: 'privacy',
  badge: 'Privacy & Data Protection',
  title: 'Privacy Policy & Data Processing Notice',
  subtitle: 'Transparent disclosures regarding how we collect, process, safeguard, and retain personal information.',
  lastUpdated: 'October 17, 2026',
  sections: [
    {
      id: 'scope-and-roles',
      title: '1. Scope & Legal Roles (Controller vs. Processor)',
      content: [
        'This Privacy Policy applies to personal data processed across the Re-member Flow application. Our processing is designed to comply with international data protection standards, including the General Data Protection Regulation (GDPR), UK GDPR, California Consumer Privacy Act (CCPA/CPRA), and the India Digital Personal Data Protection (DPDP) Act 2023.',
        'DATA CONTROLLER FOR DIRECT USERS: When gym owners register their organization, create platform administrator accounts, or visit our marketing pages, Re-member Flow acts as the Data Controller of that account information.',
        'DATA PROCESSOR FOR GYM MEMBERS & STAFF: When a Gym Organization stores member profiles, front-desk staff records, and personal trainer session logs, the Gym Organization acts as the Data Controller, and Re-member Flow acts as the Data Processor (or Service Provider). We process member records solely pursuant to the instructions and operational needs of the respective Gym Organization.'
      ]
    },
    {
      id: 'data-collected',
      title: '2. Categories of Personal Data Collected',
      content: [
        'ACCOUNT & CONTACT DATA: Full name, email address, telephone number, user role (Owner, Staff, Personal Trainer, Member, Super Admin), and cryptographic password hash (salted BCrypt).',
        'ORGANIZATION & BRANCH DATA: Gym/facility legal name, organization unique slug/code, branch names, physical street addresses, contact phone numbers, and operational timezones (e.g. Asia/Kolkata).',
        'MEMBERSHIP & FINANCIAL METRICS: Selected package name, duration (months or days), membership start and paid-through dates, promotional discount percentages, calculated amount paid, freeze intervals (dates and reasons), cancellation timestamps and stated reasons.',
        'PAYMENT RECEIPT ATTACHMENTS: Optional payment receipts uploaded as images (JPEG, PNG, or WebP up to 5 MB) to document offline card, UPI, or cash payments. Receipt images are stored in isolated binary storage and only accessible to authorized gym staff.',
        'PERSONAL TRAINING RECORDS: Assigned trainer profiles, scheduled training sessions, workout focus notes, completion timestamps, and trainer commission rates.',
        'TECHNICAL & SECURITY LOGS: IP addresses, browser user-agent strings, authentication tokens, session timestamps, and tamper-evident audit history records.'
      ]
    },
    {
      id: 'processing-purposes',
      title: '3. Legal Bases & Purposes for Processing',
      content: [
        'PERFORMANCE OF CONTRACT: Processing is essential to deliver core gym management services, authenticate logins, calculate membership validity, provide real-time access checks, and schedule personal training sessions.',
        'LEGITIMATE BUSINESS INTERESTS: Maintaining system security, detecting unauthorized access attempts, generating aggregated business analytics for gym owners, and logging audit trails to prevent fraud.',
        'LEGAL & REGULATORY OBLIGATIONS: Maintaining financial and tax transaction records, preserving proof of payment receipts, and responding to lawful statutory process.',
        'CONSENT: Voluntary disclosures such as health readiness acknowledgments and voluntary communication preferences.'
      ]
    },
    {
      id: 'automated-notifications',
      title: '4. Automated Reminders & Transactional Emails',
      content: [
        'RENEWAL REMINDERS: The Platform runs an automated background scheduler that evaluates upcoming membership expirations and automatically sends renewal reminder notices at 7 days, 3 days, and 1 day prior to expiration to the member, gym owner, and assigned branch staff.',
        'TRANSACTIONAL COMMUNICATIONS: The application transmits enrollment confirmation summaries, optional receipt image attachments, and single-use password reset OTP codes (which expire automatically after 10 minutes) via configured SMTP servers.',
        'NO SPAM: We never transmit third-party advertising or unrelated promotional messages through these operational channels.'
      ]
    },
    {
      id: 'data-sharing',
      title: '5. Data Sharing, Disclosures & Sub-Processors',
      content: [
        'INTERNAL GYM DISCLOSURES: Member data is accessible only to verified owners and staff members assigned to the respective gym organization and branch.',
        'INFRASTRUCTURE SUB-PROCESSORS: We utilize vetted enterprise cloud infrastructure providers (including Amazon Web Services / cloud virtual servers for compute and managed MySQL storage, and secure SMTP email relay services). All sub-processors are bound by strict Data Processing Agreements (DPAs).',
        'NO SALE OR RENT OF DATA: Re-member Flow has NEVER sold, rented, leased, or monetized personal information to third-party data brokers, marketers, or advertisers, and will never do so.',
        'LEGAL COMPLIANCE DISCLOSURES: We may disclose personal data if required by law, subpoena, or valid court order, or when reasonably necessary to protect the safety of individuals or prevent physical harm.'
      ]
    },
    {
      id: 'security-measures',
      title: '6. Technical & Organizational Data Security',
      content: [
        'CRYPTOGRAPHIC HASHING: Passwords and sensitive recovery codes are hashed at rest using BCrypt with randomized salts. Cleartext passwords are never persisted.',
        'SIGNED JWT AUTHENTICATION: Application sessions are governed by signed JSON Web Tokens (JWT) utilizing HMAC-SHA256 with 12-hour expiration limits. Password changes, resets, or account deletions immediately invalidate active tokens.',
        'ENCRYPTION IN TRANSIT: All application traffic is encrypted in transit using Transport Layer Security (TLS 1.2 or TLS 1.3). Public HTTP traffic is strictly prohibited in production.',
        'MULTI-TENANT ISOLATION: Strict database query boundaries and Role-Based Access Controls guarantee that staff from one branch or gym organization cannot view or access data from another.'
      ]
    },
    {
      id: 'retention-and-deletion',
      title: '7. Data Retention & Deletion Schedule',
      content: [
        'ACTIVE ACCOUNTS: Personal data and membership records are retained for the duration of the subscriber\'s active subscription.',
        'FINANCIAL AUDIT ARCHIVES: Membership records, receipts, and audit history logs are retained for up to seven (7) years to satisfy statutory tax, accounting, and anti-fraud record-keeping mandates.',
        'BRANCH DEACTIVATION: Branches are marked inactive rather than permanently purged to safeguard the historical contractual records of past members.',
        'ACCOUNT PURGING: Upon confirmed termination of an organization account and expiration of the export grace period, production records are permanently deleted or irreversibly anonymized.'
      ]
    },
    {
      id: 'user-rights',
      title: '8. Data Subject Rights & Choices',
      content: [
        'Under applicable privacy statutes (GDPR, CCPA/CPRA, DPDP Act 2023), you may possess the following rights:',
        '• RIGHT TO ACCESS: Request confirmation and a copy of the personal data held about you.',
        '• RIGHT TO RECTIFICATION: Request correction of inaccurate, incomplete, or outdated personal information.',
        '• RIGHT TO ERASURE: Request deletion of your personal data ("Right to be Forgotten") where no continuing legal or tax justification exists.',
        '• RIGHT TO DATA PORTABILITY: Export your records in a structured, machine-readable format (CSV export options are provided for memberships, revenue, packages, and cancellations).',
        '• RIGHT TO WITHDRAW CONSENT: Revoke consent previously granted for optional communications.',
        'NOTICE FOR GYM MEMBERS: Because your Gym Organization is the Data Controller of your member profile, requests for record modification or deletion should be directed primarily to your club\'s management.'
      ]
    },
    {
      id: 'cookies-storage',
      title: '9. Cookies & Browser Local Storage Policy',
      content: [
        'The Platform does NOT deploy third-party advertising cookies, social media tracking pixels, or cross-site behavioral tracking scripts.',
        'We utilize essential browser localStorage and sessionStorage strictly for:',
        '• Storing the active signed authentication bearer token (gym_auth_token) and basic user profile metadata.',
        '• Tracking security session timeout flags (gym.session-expired).',
        '• Persisting user interface preferences and recording cookie notice acknowledgment (gym_cookie_consent_accepted).',
        'These storage items are strictly necessary for the application to function securely.'
      ]
    },
    {
      id: 'children',
      title: '10. Minors & Children\'s Data Protection',
      content: [
        'The Platform is not directed to children under 13 years of age (or under 16/18 where specified by local privacy statutes). Gym Organizations that enroll minors must obtain verified written consent from a parent or legal guardian prior to registering the minor\'s profile in the system.'
      ]
    },
    {
      id: 'dpo-contact',
      title: '11. Privacy Inquiries & Data Protection Officer',
      content: [
        'If you have questions, concerns, or requests regarding this Privacy Policy or wish to exercise your data protection rights, please contact our Data Protection Officer at: privacy@rememberflow.com or via post to Re-member Flow Privacy Office. We respond to all verified requests within thirty (30) days.'
      ]
    }
  ]
};

export const LEGAL_HEALTH_DISCLAIMER: LegalDocument = {
  id: 'disclaimer',
  badge: 'Health & Safety Waiver',
  title: 'Health, Fitness & Physical Liability Waiver',
  subtitle: 'Important health disclosures, medical clearance notices, and assumption of physical risk.',
  lastUpdated: 'October 17, 2026',
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
        'Gym operators are solely responsible for ensuring equipment safety, regular machine inspections, emergency first aid readiness (including accessible AEDs), and sanitary facilities.'
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

export const LEGAL_ACCEPTABLE_USE: LegalDocument = {
  id: 'acceptable-use',
  badge: 'System Integrity',
  title: 'Acceptable Use & System Integrity Policy',
  subtitle: 'Rules governing proper conduct, cybersecurity compliance, and system integrity.',
  lastUpdated: 'October 17, 2026',
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
        'We reserve the right to immediately suspend accounts, revoke tokens, and block IP addresses that violate this policy, and to cooperate fully with law enforcement agencies in investigating cybercrime.'
      ]
    }
  ]
};

export const ALL_LEGAL_DOCUMENTS: Record<'terms' | 'privacy' | 'disclaimer' | 'acceptable-use', LegalDocument> = {
  terms: LEGAL_TERMS_OF_USE,
  privacy: LEGAL_PRIVACY_POLICY,
  disclaimer: LEGAL_HEALTH_DISCLAIMER,
  'acceptable-use': LEGAL_ACCEPTABLE_USE
};

