export type MemberFilter = 'all' | 'active' | 'expiring' | 'cancelled';
export type AdjustmentMode = 'freeze' | 'transfer' | 'correct' | 'cancel-freeze' | 'end-freeze';

export interface Freeze {
  id: number;
  startDate: string;
  endDate: string;
  reason: string;
  createdBy: string;
  createdAt: string;
  cancelledAt: string | null;
  endedEarlyAt: string | null;
}

export interface Membership {
  id: number;
  memberName: string;
  email: string;
  phoneNumber: string | null;
  planId: number;
  planName: string;
  branchId: number | null;
  branchName: string | null;
  startDate: string;
  endDate: string;
  listPrice: number;
  discountPercent: number;
  amountPaid: number;
  status: 'ACTIVE' | 'CANCELLED' | 'UPGRADED';
  upgradedFromId?: number | null;
  renewalStartDate: string | null;
  createdAt: string;
  lastActivityAt: string;
  cancelledAt: string | null;
  planDurationMonths: number | null;
  planDurationDays: number | null;
  frozen: boolean;
  freezes: Freeze[];
  personalTrainerId?: number | null;
  personalTrainerName?: string | null;
  ptSessionsIncluded?: number | null;
  ptSessionsUsed?: number;
  memberPin?: string | null;
  paymentMethod?: string;
  transactionId?: string | null;
}

export interface MembershipGroup {
  memberId: number;
  memberName: string;
  email: string;
  phoneNumber: string | null;
  terms: Membership[];
}

export interface MembershipPage {
  members: MembershipGroup[];
  page: number;
  size: number;
  totalMembers: number;
  totalPages: number;
}

export interface MembershipAccess {
  allowed: boolean;
  status: string;
  checkedOn: string;
  message: string;
}

export interface MembershipPayload {
  fullName: string;
  email: string;
  phoneNumber: string;
  password: string;
  planId: number;
  branchId: number | null;
  personalTrainerId?: number | null;
  discountPercent: number;
  startDate: string;
  receiptImageData?: string | null;
  paymentMethod?: string;
  transactionId?: string | null;
}

export interface RenewalPayload {
  planId: number;
  branchId: number | null;
  personalTrainerId?: number | null;
  discountPercent: number;
  startDate: string;
  paymentMethod?: string;
  transactionId?: string | null;
}

export interface MembershipUpgradeQuote {
  totalDays: number;
  remainingDays: number;
  oldDailyRate: number;
  newDailyRate: number;
  creditAmount: number;
  newPlanProrataCost: number;
  topUpDue: number;
  currentPlanName: string;
  currentPlanPrice: number;
  newPlanName: string;
  newPlanPrice: number;
  hasPt: boolean;
}

export interface MembershipUpgradePayload {
  newPlanId: number;
  personalTrainerId?: number | null;
  topUpPaid: number;
  paymentMethod?: string;
  transactionId?: string | null;
}

export type CancellationReason = 'NO_LONGER_INTERESTED' | 'RELOCATED' | 'MEDICAL' | 'FINANCIAL' | 'SERVICE_ISSUE' | 'OTHER';

export interface CancellationPayload {
  reason: CancellationReason;
  remarks: string;
  cancellationDate: string;
  otherExplanation: string;
}

export interface BulkMemberImportRow {
  fullName: string;
  email: string;
  phoneNumber?: string;
  packageName?: string;
  branchCode?: string;
  startDate?: string;
  paymentMethod?: string;
}

export interface BulkMemberImportResult {
  totalRequested: number;
  importedCount: number;
  errors: string[];
}


export type RenewalRequestStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export interface RenewalRequest {
  id: number;
  membershipId: number;
  memberId: number;
  memberName: string;
  memberEmail: string;
  memberPhone?: string | null;
  planId: number;
  planName: string;
  planPrice: number;
  branchId: number;
  branchName: string;
  personalTrainerId?: number | null;
  personalTrainerName?: string | null;
  requestedStartDate: string;
  paymentMethod: string;
  transactionId?: string | null;
  notes?: string | null;
  amount: number;
  discountPercent: number;
  status: RenewalRequestStatus;
  reviewedByName?: string | null;
  reviewedAt?: string | null;
  rejectionReason?: string | null;
  resultingMembershipId?: number | null;
  createdAt: string;
}

export interface RenewalSubmissionPayload {
  planId: number;
  personalTrainerId?: number | null;
  startDate: string;
  paymentMethod: string;
  transactionId?: string | null;
  notes?: string | null;
}

export interface RenewalApprovalPayload {
  discountPercent?: number;
  paymentMethod?: string;
  transactionId?: string | null;
  notes?: string | null;
}

export interface RenewalRejectionPayload {
  reason: string;
}


export type MemberUpgradeRequestStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export interface MemberPackageUpgradeRequest {
  id: number;
  membershipId: number;
  memberId: number;
  memberName: string;
  memberEmail: string;
  memberPhone?: string | null;
  currentPlanId?: number | null;
  currentPlanName?: string | null;
  newPlanId: number;
  newPlanName: string;
  newPlanPrice: number;
  branchId: number;
  branchName: string;
  personalTrainerId?: number | null;
  personalTrainerName?: string | null;
  topUpAmount: number;
  paymentMethod: string;
  transactionId?: string | null;
  notes?: string | null;
  status: MemberUpgradeRequestStatus;
  reviewedByName?: string | null;
  reviewedAt?: string | null;
  rejectionReason?: string | null;
  resultingMembershipId?: number | null;
  createdAt: string;
}

export interface MemberUpgradeSubmissionPayload {
  newPlanId: number;
  personalTrainerId?: number | null;
  paymentMethod: string;
  transactionId?: string | null;
  notes?: string | null;
}

export interface MemberPackageUpgradeApprovalPayload {
  paymentMethod?: string;
  transactionId?: string | null;
  notes?: string | null;
}

export interface MemberPackageUpgradeRejectionPayload {
  reason: string;
}


