import { Membership } from './membership.model';

export type BranchType = 'COMPANY_OWNED' | 'FRANCHISE';

export interface Branch {
  id: number;
  name: string;
  code: string;
  type: BranchType;
  address: string | null;
  phoneNumber: string | null;
  active: boolean;
  memberCount: number;
}

export interface BranchPayload {
  name: string;
  code: string;
  type: BranchType;
  address: string;
  phoneNumber: string;
  active: boolean;
}

export interface BranchDashboard {
  branchId: number;
  branchName: string;
  branchCode: string;
  branchType: BranchType;
  revenueThisMonth: number;
  revenueLastMonth: number;
  activeMembers: number;
  totalMembers: number;
  expiringWithinSevenDays: number;
  expiringMembers: Membership[];
}

