export type LegalDocumentId = 'terms' | 'privacy' | 'dpa' | 'disclaimer' | 'acceptable-use';

export interface LegalSection {
  id: string;
  title: string;
  content: string[];
}

export interface LegalDocument {
  id: LegalDocumentId;
  badge: string;
  title: string;
  subtitle: string;
  lastUpdated: string;
  sections: LegalSection[];
  jurisdiction?: string;
  jurisdictionLabel?: string;
  governingLaw?: string;
}
