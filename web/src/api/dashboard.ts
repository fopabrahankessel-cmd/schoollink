import type { ScopeDashboard } from './dm'

// UC-A6, BR-24, BR-25: the school dashboard, the admin's. The school comes from the signed-in
// user (NFR-8), never from a parameter.

/**
 * BR-24: deliveryRate is the share of finished sends that reached the gateway or the phone,
 * (sent + delivered + opened) / (sent + delivered + opened + failed); null when none is finished.
 */
export interface Channel {
  pending: number
  sent: number
  delivered: number
  opened: number
  failed: number
  deliveryRate: number | null
}

/** BR-25, NFR-14: this calendar month, whatever the range. Summons still go past the cap (D12). */
export interface SmsUsage {
  sentThisMonth: number
  monthlyCap: number
  capReached: boolean
}

export interface SchoolDashboard extends ScopeDashboard {
  delivery: { push: Channel; sms: Channel }
  sms: SmsUsage
}

const q = encodeURIComponent

export const schoolDashboardPath = (from: string, to: string) => `/dashboard/school?from=${q(from)}&to=${q(to)}`
