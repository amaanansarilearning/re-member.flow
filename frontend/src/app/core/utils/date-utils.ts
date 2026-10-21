export const BUSINESS_TIME_ZONE = 'Asia/Kolkata';

const businessDateFormatter = new Intl.DateTimeFormat('en-CA', {
  year: 'numeric', month: '2-digit', day: '2-digit', timeZone: BUSINESS_TIME_ZONE
});

export function todayDate(): string {
  const parts = businessDateFormatter.formatToParts(new Date());
  const part = (type: string) => parts.find((value) => value.type === type)?.value ?? '';
  return `${part('year')}-${part('month')}-${part('day')}`;
}

export function todayLabel(): string {
  return new Intl.DateTimeFormat('en-IN', {
    weekday: 'short', day: 'numeric', month: 'short', year: 'numeric', timeZone: BUSINESS_TIME_ZONE
  }).format(new Date()).toUpperCase();
}

export function addDays(value: string, days: number): string {
  const date = new Date(`${value}T00:00:00Z`);
  date.setUTCDate(date.getUTCDate() + days);
  return date.toISOString().slice(0, 10);
}

export function addMonths(value: string, months: number): string {
  const [year, month, day] = value.split('-').map(Number);
  const firstOfMonth = new Date(Date.UTC(year, month - 1 + months, 1));
  const targetYear = firstOfMonth.getUTCFullYear();
  const targetMonth = firstOfMonth.getUTCMonth();
  const lastDay = new Date(Date.UTC(targetYear, targetMonth + 1, 0)).getUTCDate();
  return `${targetYear}-${String(targetMonth + 1).padStart(2, '0')}-${String(Math.min(day, lastDay)).padStart(2, '0')}`;
}

export function daysUntil(value: string): number {
  return Math.max(0, Math.ceil((Date.parse(`${value}T00:00:00Z`) - Date.parse(`${todayDate()}T00:00:00Z`)) / 86400000));
}

export function isExpired(value: string): boolean {
  return value < todayDate();
}

export function isUpcoming(value: string): boolean {
  return value > todayDate();
}

export function isExpiring(value: string): boolean {
  return !isExpired(value) && daysUntil(value) <= 7;
}

