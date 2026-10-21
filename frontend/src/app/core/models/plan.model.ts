export type PtPackageType = 'NONE' | 'PT_ONLY' | 'COMBINED';

export interface Plan {
  id: number;
  name: string;
  description: string;
  price: number;
  durationMonths: number | null;
  durationDays: number | null;
  branchId: number | null;
  branchName: string | null;
  active: boolean;
  usageCount: number;
  ptPackageType?: PtPackageType;
  includedPtSessions?: number | null;
  ptFeeIncluded?: number | null;
}

export interface PlanPayload {
  name: string;
  description: string;
  price: number;
  durationMonths: number | null;
  durationDays: number | null;
  branchId: number | null;
  active: boolean;
  ptPackageType?: PtPackageType;
  includedPtSessions?: number | null;
  ptFeeIncluded?: number | null;
}

