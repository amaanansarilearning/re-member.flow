import { BranchDashboard } from './branch.model';
import { Membership } from './membership.model';

export interface PackagePerformance {
  packageId: number;
  packageName: string;
  memberships: number;
  revenue: number;
}

export interface Report {
  from: string;
  to: string;
  memberships: number;
  cancellations: number;
  revenue: number;
  packages: PackagePerformance[];
}

export interface AnalyticsPoint {
  month: string;
  memberships: number;
  newMembers: number;
  renewals: number;
  revenue: number;
  newRevenue: number;
  renewalRevenue: number;
  discountsGiven: number;
  recordedPriceVariance: number;
}

export interface PackageAnalytics {
  packageId: number;
  packageName: string;
  memberships: number;
  newMembers: number;
  renewals: number;
  revenue: number;
  salesSharePercent: number;
  averageSale: number;
  discountsGiven: number;
  recordedPriceVariance: number;
  averageDiscountPercent: number;
}

export interface RenewingMember {
  memberId: number;
  memberName: string;
  memberEmail: string;
  terms: number;
  renewals: number;
  renewalYears: number;
  firstRenewalYear: number | null;
  lastRenewalYear: number | null;
  renewedInEveryObservedCalendarYear: boolean;
  lifetimeRevenue: number;
  latestRenewalInPeriod: string;
}

export interface TaxAnalytics {
  grossRecordedSales: number;
  estimatedTaxableSales: number;
  estimatedGstIncluded: number;
  estimatedCgst: number;
  estimatedSgst: number;
  estimatedIgst: number;
  recordedSalesExcludingEstimatedGst: number;
  estimatedOutputGst: number;
  cgstRate: number;
  sgstRate: number;
  igstRate: number;
  estimated: boolean;
}

export interface Analytics {
  from: string;
  to: string;
  totalMembershipsSold: number;
  newMembers: number;
  membershipRenewals: number;
  renewingMembers: number;
  activeMemberships: number;
  activeMembers: number;
  expiredOrCancelledMemberships: number;
  membersLeft: number;
  openingMembers: number;
  retentionRate: number;
  churnRate: number;
  membershipGrowthPercent: number | null;
  newMemberSalesGrowthPercent: number | null;
  totalRevenue: number;
  newMembershipRevenue: number;
  renewalRevenue: number;
  averageSale: number;
  averageRenewalSale: number;
  totalListPrice: number;
  discountsGiven: number;
  recordedPriceVariance: number;
  renewalRevenueSharePercent: number;
  revenueGrowthPercent: number | null;
  tax: TaxAnalytics;
  trend: AnalyticsPoint[];
  packagePerformance: PackageAnalytics[];
  renewingMemberDetails: RenewingMember[];
  revenueIsRecordedMembershipAmounts: boolean;
}

export interface Dashboard {
  revenueThisMonth: number;
  revenueLastMonth: number;
  activeMembers: number;
  totalMembers: number;
  expiringWithinSevenDays: number;
  expiringMembers: Membership[];
  branches: BranchDashboard[];
}

