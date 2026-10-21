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

