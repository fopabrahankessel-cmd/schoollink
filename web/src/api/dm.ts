// The DM's API: alerts and decisions (UC-D2, D30), summons (UC-D3, D31), justification (UC-D1,
// D26), cancellation (UC-D6, D27), a student's history and the scope dashboard (UC-D8, D34), the
// student search (D35) and the settings (UC-D7, D28). The school and the DM's classes come from
// the signed-in user (NFR-8), never from a parameter.

export type RuleType = 'FULL_DAY_ABSENCE' | 'CONSECUTIVE_LATENESS' | 'UNJUSTIFIED_ABSENCES'

export const RULES: RuleType[] = ['FULL_DAY_ABSENCE', 'CONSECUTIVE_LATENESS', 'UNJUSTIFIED_ABSENCES']

export type Decision = 'SUMMON' | 'DISMISS' | 'DISCIPLINARY' | 'REFER'

export const DECISIONS: Decision[] = ['SUMMON', 'DISMISS', 'DISCIPLINARY', 'REFER']

/** D30: dismiss and disciplinary need the note (the reason, or the issue recorded). */
export const needsNote = (decision: Decision) => decision === 'DISMISS' || decision === 'DISCIPLINARY'

/** What the rule saw: the days of a streak, or the unjustified periods from when to when. */
export interface Evidence {
  dates?: string[]
  periods?: number
  from?: string
  to?: string
}

export interface RiskAlert {
  id: string
  studentId: string
  firstName: string
  lastName: string
  matricule: string
  classId: string
  className: string | null
  ruleType: RuleType
  threshold: number
  asOf: string
  evidence: Evidence
  status: 'OPEN' | 'CLOSED'
  raisedAt: string
  decision: Decision | null
  decisionNote: string | null
  decidedAt: string | null
}

/** Both left out: the next school day at the start of school (D31). */
export interface Meeting {
  meetingDate?: string
  meetingTime?: string
}

export interface DecisionRequest extends Meeting {
  decision: Decision
  note?: string
}

export interface SummonsRequest extends Meeting {
  studentId: string
  reason: string
}

export interface Summons {
  id: string
  meetingDate: string
  meetingTime: string
}

export type Reason = 'ILLNESS' | 'FAMILY' | 'SCHOOL_ACTIVITY' | 'OTHER'

export const REASONS: Reason[] = ['ILLNESS', 'FAMILY', 'SCHOOL_ACTIVITY', 'OTHER']

/** NFR-10, D26: only "illness" is stored about health, so an illness takes no note. */
export const takesNote = (reason: Reason) => reason !== 'ILLNESS'

export interface JustificationRequest {
  reason: Reason
  note?: string
  absenceIds: string[]
  latenessIds: string[]
}

export interface PeriodAbsence {
  recordId: string
  periodNumber: number
  startTime: string
  justified: boolean
}

export interface DayLateness {
  recordId: string
  arrivalTime: string
  justified: boolean
}

export interface HistoryDay {
  date: string
  absences: PeriodAbsence[]
  lateness: DayLateness | null
}

export interface Totals {
  absentPeriods: number
  unjustifiedAbsentPeriods: number
  lateness: number
  unjustifiedLateness: number
}

export interface StudentHistory {
  studentId: string
  matricule: string
  firstName: string
  lastName: string
  classId: string
  className: string | null
  from: string
  to: string
  totals: Totals
  /** Newest first. */
  days: HistoryDay[]
}

export interface StudentCard {
  id: string
  matricule: string
  firstName: string
  lastName: string
  classId: string
  className: string | null
}

export interface Counts {
  sessions: number
  studentPeriods: number
  absentPeriods: number
  unjustifiedAbsentPeriods: number
  lateness: number
  unjustifiedLateness: number
}

export interface RuleCount {
  rule: RuleType
  open: number
  raised: number
  closed: number
}

export interface ScopeDashboard {
  from: string
  to: string
  totals: Counts
  /** Days with a session or a lateness, oldest first. */
  days: { date: string; counts: Counts }[]
  classes: { classId: string; name: string | null; counts: Counts }[]
  alerts: RuleCount[]
}

export interface RuleSetting {
  type: RuleType
  count: number
  active: boolean
  min: number
  max: number
}

export interface Settings {
  /** "07:30:00" */
  startTime: string
  digestTimes: string[]
  rules: RuleSetting[]
}

export interface SettingsRequest {
  startTime: string
  digestTimes: string[]
  rules: { type: RuleType; count: number; active: boolean }[]
}

// Every parameter is encoded, path ones too: an id from the address bar must not reach another endpoint.
const q = encodeURIComponent

export const DM_PATHS = {
  alerts: (status: 'open' | 'closed') => `/alerts?status=${status}`,
  decision: (alertId: string) => `/alerts/${q(alertId)}/decision`,
  summons: '/summons',
  justifications: '/justifications',
  cancelAbsence: (recordId: string) => `/records/${q(recordId)}/cancel`,
  cancelLateness: (recordId: string) => `/lateness/${q(recordId)}/cancel`,
  history: (studentId: string) => `/students/${q(studentId)}/history`,
  students: (query: string) => `/lateness/students?q=${q(query)}`,
  dashboard: (from: string, to: string) => `/dashboard/scope?from=${q(from)}&to=${q(to)}`,
  settings: '/settings/thresholds',
}

/** "07:30:00" -> "07:30" */
export const hm = (time: string) => time.slice(0, 5)

/** A date as the API takes it: "2026-11-02". */
export function isoDate(day: Date): string {
  return `${day.getFullYear()}-${String(day.getMonth() + 1).padStart(2, '0')}-${String(day.getDate()).padStart(2, '0')}`
}
