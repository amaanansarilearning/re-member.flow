import { Branch } from './branch.model';
import { Plan } from './plan.model';
import { Membership } from './membership.model';
import { User } from './auth.model';
import { ActionHistory } from './history.model';

export interface SubscriptionStatus {
  organizationId: number;
  organizationName: string;
  organizationCode: string;
  organizationLogo?: string | null;
  subscriptionTier: 'STANDARD' | 'STARTER' | 'PRO' | 'ENTERPRISE' | 'PREMIUM';
  subscriptionStatus: 'ACTIVE' | 'TRIAL' | 'EXPIRED' | 'CANCELLED' | 'SUSPENDED';
  expiresAt: string | null;
  brandingEnabled?: boolean;
  receiptUploadEnabled?: boolean;
  historyEnabled: boolean;
  ptEnabled: boolean;
  analyticsEnabled?: boolean;
  enabledFeatures: string[];
  maxBranches?: number;
  maxStaff?: number;
  maxMembers?: number;
  daysRemaining?: number;
}

export interface ActivationCode {
  id: number;
  code: string;
  tier: 'STANDARD' | 'STARTER' | 'PRO' | 'ENTERPRISE' | 'PREMIUM';
  durationDays: number | null;
  status: 'AVAILABLE' | 'REDEEMED' | 'REVOKED' | 'EXPIRED';
  maxRedemptions: number;
  redemptionCount: number;
  notes: string | null;
  redeemedByOrgId: number | null;
  redeemedByOrgName: string | null;
  redeemedByEmail: string | null;
  redeemedAt: string | null;
  codeExpiresAt: string | null;
  createdAt: string;
  createdBy: string | null;
}

export interface UpgradeRequest {
  id: number;
  organizationId: number;
  organizationName: string;
  organizationCode: string;
  requesterEmail: string;
  requesterName: string | null;
  requesterPhone: string | null;
  targetTier: 'STANDARD' | 'STARTER' | 'PRO' | 'ENTERPRISE' | 'PREMIUM';
  durationDays: number;
  billingPlanName: string;
  paymentMethod: string;
  gstNumber: string | null;
  billingAddress: string | null;
  notes: string | null;
  status: 'PENDING' | 'APPROVED_KEY_ISSUED' | 'APPROVED_DIRECT_UPGRADE' | 'REJECTED';
  issuedActivationCode: string | null;
  reviewedBy: string | null;
  reviewedAt: string | null;
  rejectionReason: string | null;
  createdAt: string;
  estimatedTopUp?: number | null;
}

export interface UpgradeRequestPayload {
  billingPlan: string;
  durationDays: number;
  paymentMethod: string;
  gstNumber?: string;
  billingAddress?: string;
  notes?: string;
  targetTier?: string;
  estimatedTopUp?: number;
}

export interface SaasUpgradeQuote {
  currentTier: 'STANDARD' | 'STARTER' | 'PRO' | 'ENTERPRISE' | 'PREMIUM';
  targetTier: 'STANDARD' | 'STARTER' | 'PRO' | 'ENTERPRISE' | 'PREMIUM';
  currentMonthlyPrice: number;
  targetMonthlyPrice: number;
  remainingDays: number;
  estimatedTopUp: number;
  effectiveDate: string;
}

export interface PlatformMetrics {
  totalGyms: number;
  activePremiumGyms: number;
  standardGyms: number;
  totalMembersAcrossGyms: number;
  totalBranches: number;
  availableCodes: number;
}

export interface PlatformOrganization {
  id: number;
  name: string;
  code: string;
  logoData?: string | null;
  subscriptionTier: 'STANDARD' | 'STARTER' | 'PRO' | 'ENTERPRISE' | 'PREMIUM';
  subscriptionStatus: 'ACTIVE' | 'TRIAL' | 'EXPIRED' | 'CANCELLED' | 'SUSPENDED';
  subscriptionExpiresAt: string | null;
  createdAt: string;
  ownerEmail: string | null;
  ownerName: string | null;
  branchCount: number;
  memberCount: number;
}

export interface TenantBackup {
  organizationId: number;
  organizationName: string;
  organizationCode: string;
  subscriptionTier: string;
  subscriptionStatus: string;
  exportedAt: string;
  exportedBy: string;
  branches: Branch[];
  packages: Plan[];
  memberships: Membership[];
  staff: User[];
  auditLogs: ActionHistory[];
}

