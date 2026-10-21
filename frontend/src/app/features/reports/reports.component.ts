import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Analytics, Branch, PtAnalytics, PtProfile, User, View } from '../../core/models';
import { initials, money, moneyPrecise } from '../../core/utils/formatters';

@Component({
  selector: 'app-reports',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './reports.component.html',
  styleUrl: './reports.component.scss'
})
export class ReportsComponent {
  @Input() busy = false;
  @Input() user: User | null = null;
  @Input() analyticsData: Analytics | null = null;
  @Input() ptAnalyticsData: PtAnalytics | null = null;
  @Input() branches: Branch[] = [];
  @Input() personalTrainers: PtProfile[] = [];
  @Input() analyticsPeriod: 'month' | 'quarter' | 'six-months' | 'twelve-months' | 'custom' | string = 'month';
  @Input() analyticsTab: 'executive' | 'pt' | 'packages' | 'retention' | 'tax' | string = 'executive';
  @Input() reportFrom = '';
  @Input() reportTo = '';
  @Input() reportBranchId = '';
  @Input() exportMenuOpen = false;
  @Input() ptNoteDismissed = false;

  @Output() loadReport = new EventEmitter<void>();
  @Output() selectAnalyticsPeriod = new EventEmitter<'month' | 'quarter' | 'six-months' | 'twelve-months' | 'custom'>();
  @Output() markCustomAnalyticsPeriod = new EventEmitter<void>();
  @Output() setAnalyticsTab = new EventEmitter<'executive' | 'pt' | 'packages' | 'retention' | 'tax'>();
  @Output() reportFromChange = new EventEmitter<string>();
  @Output() reportToChange = new EventEmitter<string>();
  @Output() reportBranchIdChange = new EventEmitter<string>();
  @Output() toggleExportMenu = new EventEmitter<void>();
  @Output() closeExportMenu = new EventEmitter<void>();
  @Output() exportReport = new EventEmitter<string>();
  @Output() openPtSessionsModal = new EventEmitter<PtProfile>();
  @Output() dismissPtNote = new EventEmitter<void>();
  @Output() navigate = new EventEmitter<View>();

  money(amount: number): string {
    return money(amount);
  }

  moneyPrecise(amount: number): string {
    return preciseMoney(amount);
  }

  initials(name: string): string {
    return initials(name);
  }

  percentLabel(value: number | null): string {
    return value === null ? 'New / no baseline' : `${value > 0 ? '+' : ''}${value}%`;
  }

  roundPct(numerator: number, denominator: number): number {
    if (!denominator || denominator <= 0) return 0;
    return Math.min(100, Math.round((numerator / denominator) * 100));
  }

  chartBarHeight(value: number, maximum: number): number {
    return maximum <= 0 ? 0 : Math.max(value > 0 ? 6 : 0, Math.round((value / maximum) * 100));
  }

  maxTrendMemberships(): number {
    return Math.max(1, ...(this.analyticsData?.trend.map((point) => point.memberships) ?? []));
  }

  maxTrendRenewalRevenue(): number {
    return Math.max(1, ...(this.analyticsData?.trend.flatMap((point) => [
      point.newRevenue, point.renewalRevenue
    ]) ?? []));
  }

  maxPackageRevenue(): number {
    const pkgs = this.analyticsData?.packagePerformance ?? [];
    return Math.max(1, ...pkgs.map(p => p.revenue));
  }

  private readonly CHART_PALETTE = ['#6366f1', '#10b981', '#f59e0b', '#ec4899', '#8b5cf6', '#06b6d4', '#3b82f6', '#f43f5e', '#14b8a6', '#64748b'];

  calculateDonutSlices(items: { name: string; value: number; color?: string }[]): Array<{
    name: string;
    value: number;
    percent: number;
    dashArray: string;
    dashOffset: number;
    color: string;
  }> {
    const total = items.reduce((acc, curr) => acc + Math.max(0, curr.value), 0);
    const C = 251.327; // 2 * PI * 40
    if (total <= 0) {
      return [{
        name: 'No data',
        value: 0,
        percent: 0,
        dashArray: `0 ${C}`,
        dashOffset: 0,
        color: '#94a3b8'
      }];
    }
    let accumulatedOffset = 0;
    return items.map((item, index) => {
      const val = Math.max(0, item.value);
      const pct = Math.round((val / total) * 100);
      const arcLength = (val / total) * C;
      const slice = {
        name: item.name,
        value: val,
        percent: pct,
        dashArray: `${arcLength.toFixed(2)} ${(C - arcLength).toFixed(2)}`,
        dashOffset: -accumulatedOffset,
        color: item.color || this.CHART_PALETTE[index % this.CHART_PALETTE.length]
      };
      accumulatedOffset += arcLength;
      return slice;
    });
  }

  executiveSalesDonutSlices(): ReturnType<typeof this.calculateDonutSlices> {
    const data = this.analyticsData;
    if (!data) return [];
    return this.calculateDonutSlices([
      { name: 'New Enrollments', value: data.newMembershipRevenue, color: '#6366f1' },
      { name: 'Renewals', value: data.renewalRevenue, color: '#10b981' },
      { name: 'Discounts Absorbed', value: data.discountsGiven, color: '#f59e0b' }
    ]);
  }

  packagePerformanceDonutSlices(): ReturnType<typeof this.calculateDonutSlices> {
    const data = this.analyticsData;
    if (!data || !data.packagePerformance || data.packagePerformance.length === 0) return [];
    return this.calculateDonutSlices(
      data.packagePerformance.map((pkg, idx) => ({
        name: pkg.packageName,
        value: pkg.revenue,
        color: this.CHART_PALETTE[idx % this.CHART_PALETTE.length]
      }))
    );
  }

  ptSessionInventoryDonutSlices(): ReturnType<typeof this.calculateDonutSlices> {
    const pt = this.ptAnalyticsData;
    if (!pt) return [];
    return this.calculateDonutSlices([
      { name: 'Completed & Verified', value: pt.sessionsCompleted, color: '#10b981' },
      { name: 'Remaining in Pipeline', value: pt.remainingSessions, color: '#6366f1' },
      { name: 'Missed Sessions', value: pt.sessionsMissed, color: '#f43f5e' },
      { name: 'Cancelled Sessions', value: pt.sessionsCancelled, color: '#f59e0b' }
    ]);
  }

  ptRevenueSplitDonutSlices(): ReturnType<typeof this.calculateDonutSlices> {
    const pt = this.ptAnalyticsData;
    if (!pt) return [];
    const gross = pt.totalPtGrossRevenue > 0 ? pt.totalPtGrossRevenue : pt.contractedPtRevenue;
    const gymShare = pt.totalPtGrossRevenue > 0 ? pt.totalGymRetained : Math.round(gross * 0.6);
    const ptShare = pt.totalPtGrossRevenue > 0 ? pt.totalPtCommission : Math.round(gross * 0.4);
    return this.calculateDonutSlices([
      { name: pt.totalPtGrossRevenue > 0 ? 'Club Retained Profit' : 'Est. Club Profit (60%)', value: gymShare, color: '#3b82f6' },
      { name: pt.totalPtGrossRevenue > 0 ? 'Trainer Commission Pool' : 'Est. Trainer Pool (40%)', value: ptShare, color: '#10b981' }
    ]);
  }

  taxBreakdownDonutSlices(): ReturnType<typeof this.calculateDonutSlices> {
    const data = this.analyticsData;
    if (!data || !data.tax) return [];
    const tax = data.tax;
    return this.calculateDonutSlices([
      { name: 'Base Net Sales', value: tax.recordedSalesExcludingEstimatedGst, color: '#6366f1' },
      { name: `CGST (${tax.cgstRate}%)`, value: tax.estimatedCgst, color: '#10b981' },
      { name: `SGST (${tax.sgstRate}%)`, value: tax.estimatedSgst, color: '#06b6d4' },
      { name: `IGST (${tax.igstRate}%)`, value: tax.estimatedIgst, color: '#f59e0b' }
    ]);
  }

  retentionMemberSlices(): ReturnType<typeof this.calculateDonutSlices> {
    const data = this.analyticsData;
    if (!data) return [];
    return this.calculateDonutSlices([
      { name: 'Retained Active Members', value: data.activeMembers, color: '#10b981' },
      { name: 'Opening Cohort Lost', value: data.membersLeft, color: '#f43f5e' },
      { name: 'New Members Acquired', value: data.newMembers, color: '#6366f1' }
    ]);
  }

  onReportFromChange(val: string): void {
    this.reportFrom = val;
    this.reportFromChange.emit(val);
    this.markCustomAnalyticsPeriod.emit();
  }

  onReportToChange(val: string): void {
    this.reportTo = val;
    this.reportToChange.emit(val);
    this.markCustomAnalyticsPeriod.emit();
  }

  onReportBranchIdChange(val: string): void {
    this.reportBranchId = val;
    this.reportBranchIdChange.emit(val);
  }

  onSelectPeriod(period: any): void {
    this.selectAnalyticsPeriod.emit(period);
  }

  onSelectTab(tab: any): void {
    this.setAnalyticsTab.emit(tab);
  }

  onDismissPtNote(): void {
    this.ptNoteDismissed = true;
    this.dismissPtNote.emit();
  }

  onNavigate(target: View): void {
    this.navigate.emit(target);
  }

  onOpenPtSessionsModal(trainer: PtProfile): void {
    this.openPtSessionsModal.emit(trainer);
  }

  onCloseExportMenu(): void {
    this.closeExportMenu.emit();
  }

  onExportReport(type: string): void {
    this.exportReport.emit(type);
    this.closeExportMenu.emit();
  }
}

function preciseMoney(amount: number): string {
  return moneyPrecise(amount);
}
