export interface PtProfile {
  id: number;
  userId: number;
  fullName: string;
  email: string;
  phoneNumber: string | null;
  branchId: number | null;
  branchName: string | null;
  specializations: string | null;
  bio: string | null;
  sessionFeePerHour: number;
  commissionPercent: number;
  active: boolean;
  activeMembers: number;
  createdAt: string;
}

export interface PtRegistrationPayload {
  fullName: string;
  email: string;
  phoneNumber: string;
  password: string;
  branchId: number | null;
  specializations: string;
  bio: string;
  sessionFeePerHour: number;
  commissionPercent: number;
}

export interface PtUpdatePayload {
  branchId: number | null;
  specializations: string;
  bio: string;
  sessionFeePerHour: number;
  commissionPercent: number;
}

export interface PtAvailability {
  id: number;
  dayOfWeek: string;
  startTime: string;
  endTime: string;
  effectiveFrom: string | null;
  effectiveTo: string | null;
}

export interface PtAvailabilityPayload {
  dayOfWeek: string;
  startTime: string;
  endTime: string;
  effectiveFrom?: string | null;
  effectiveTo?: string | null;
}

export type PtSessionStatus = 'TENTATIVE' | 'BOOKED' | 'COMPLETED' | 'CANCELLED' | 'MISSED';

export interface PtSession {
  id: number;
  ptId: number;
  ptName: string;
  membershipId: number;
  memberId: number;
  memberName: string;
  scheduledDate: string;
  startTime: string;
  endTime: string;
  status: PtSessionStatus;
  notes: string | null;
  cancelReason: string | null;
  completedAt: string | null;
  sessionFeeSnapshot: number;
  commissionRateSnapshot: number;
  createdAt: string;
  verificationMethod?: string | null;
}

export interface PtSessionBookPayload {
  membershipId: number;
  scheduledDate: string;
  startTime: string;
  endTime: string;
  notes?: string;
}

export interface PtSessionUpdatePayload {
  scheduledDate: string;
  startTime: string;
  endTime: string;
  notes?: string;
}

export interface PtEarning {
  id: number;
  sessionId: number;
  sessionDate: string;
  memberName: string;
  grossRevenue: number;
  ptCommission: number;
  gymRetained: number;
  commissionRate: number;
  payoutId: number | null;
  createdAt: string;
}

export interface PtPayout {
  id: number;
  ptId: number;
  ptName: string;
  periodFrom: string;
  periodTo: string;
  totalEarnings: number;
  status: 'PENDING' | 'PAID';
  paidAt: string | null;
  paidByName: string | null;
  notes: string | null;
  createdAt: string;
}

export interface PtPayoutCreatePayload {
  periodFrom: string;
  periodTo: string;
  notes?: string;
}

export interface PtAnalytics {
  activePts: number;
  totalPts: number;
  totalPtGrossRevenue: number;
  totalPtCommission: number;
  totalGymRetained: number;
  pendingPayouts: number;
  sessionsCompleted: number;
  sessionsCancelled: number;
  sessionsMissed: number;
  activeTrainees: number;
  contractedPtRevenue: number;
  allocatedSessions: number;
  remainingSessions: number;
}

