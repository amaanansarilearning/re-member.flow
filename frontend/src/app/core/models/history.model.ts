export interface ActionHistory {
  id: number;
  actorId: number;
  actorName: string;
  actorEmail: string;
  action: string;
  entityType: string;
  entityId: number;
  reason: string | null;
  beforeData: string | null;
  afterData: string | null;
  occurredAt: string;
}

export interface RenewalReminder {
  id: number;
  membershipId: number;
  memberName: string;
  recipientEmail: string;
  daysBefore: number;
  status: string;
  attempts: number;
  lastAttemptAt: string | null;
  nextAttemptAt: string;
  sentAt: string | null;
  lastError: string | null;
}

