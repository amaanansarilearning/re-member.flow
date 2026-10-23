import { BUSINESS_TIME_ZONE } from './date-utils';

export const currencyFormatter = new Intl.NumberFormat('en-IN', {
  style: 'currency', currency: 'INR', maximumFractionDigits: 0
});

export const preciseCurrencyFormatter = new Intl.NumberFormat('en-IN', {
  style: 'currency', currency: 'INR', minimumFractionDigits: 2, maximumFractionDigits: 2
});

export const dateFormatter = new Intl.DateTimeFormat('en-IN', {
  day: 'numeric', month: 'short', year: 'numeric', timeZone: 'UTC'
});

export function money(amount: number): string {
  return currencyFormatter.format(amount);
}

export function moneyPrecise(amount: number): string {
  return preciseCurrencyFormatter.format(amount);
}

export function dateLabel(value: string | null | undefined): string {
  if (!value) return '—';
  try {
    if (value.includes('T') || value.includes(':')) {
      const parsed = new Date(value);
      if (!isNaN(parsed.getTime())) {
        return dateFormatter.format(parsed);
      }
    }
    const parts = value.split('-');
    if (parts.length >= 3) {
      const year = Number(parts[0]);
      const month = Number(parts[1]);
      const day = Number(parts[2].substring(0, 2));
      if (!isNaN(year) && !isNaN(month) && !isNaN(day)) {
        return dateFormatter.format(new Date(Date.UTC(year, month - 1, day)));
      }
    }
  } catch {
    // Graceful fallback
  }
  return value;
}

export function initials(name: string | null | undefined): string {
  if (!name || !name.trim()) return '?';
  return name.trim().split(/\s+/).slice(0, 2).map((part) => part.charAt(0)).join('').toUpperCase();
}

