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

/**
 * NFR-6: whether sending works right now, whatever the range. `overdue`: sends due for more than
 * 10 minutes and still not sent (a send waiting for its next try is not overdue). `lastDigestAt`:
 * the last run of the SMS digest (BR-22), null when it never ran.
 */
export interface Sending {
  failedLastHour: number
  overdue: number
  lastDigestAt: string | null
}

export interface SchoolDashboard extends ScopeDashboard {
  delivery: { push: Channel; sms: Channel }
  sms: SmsUsage
  sending: Sending
}

const q = encodeURIComponent

export const schoolDashboardPath = (from: string, to: string) => `/dashboard/school?from=${q(from)}&to=${q(to)}`
