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

