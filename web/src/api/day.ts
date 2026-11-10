/** UC-D10, D40: one day of attendance in the DM's classes, as `GET /attendance/day` returns it. */
export const DAY_PATH = '/attendance/day'

export const dayPath = (date: string | null) => (date ? `${DAY_PATH}?date=${encodeURIComponent(date)}` : DAY_PATH)

/** RECORDED: a session exists. NOT_RECORDED: started or past, no roll call (information only, BR-4). */
export type PeriodStatus = 'RECORDED' | 'NOT_RECORDED' | 'UPCOMING'

/** BR-15: a cancelled record is listed but never counted. */
export type RecordState = 'UNJUSTIFIED' | 'JUSTIFIED' | 'CANCELLED'

export interface DayAbsence {
  recordId: string
  studentId: string
  firstName: string
  lastName: string
  matricule: string
  state: RecordState
}

export interface DayLateness extends DayAbsence {
  arrivalTime: string
}

export interface DayPeriod {
  periodId: string
  number: number
  startTime: string
  endTime: string
  subject: string | null
  teacherName: string | null
  status: PeriodStatus
  absentCount: number
  absences: DayAbsence[]
}

export interface DayClass {
  classId: string
  className: string
  periods: DayPeriod[]
  lateness: DayLateness[]
}

export interface DayAttendance {
  date: string
  schoolDay: boolean
  totals: { notRecorded: number; absentStudents: number; lateness: number }
  classes: DayClass[]
}
