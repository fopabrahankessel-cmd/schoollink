// School structure API (UC-A1, D1, D3, D5, D10). Dates are ISO "YYYY-MM-DD", times "HH:mm[:ss]".
// The school always comes from the signed-in user, never from the request (NFR-8).

export interface AcademicYear {
  id: string
  label: string
  startDate: string
  endDate: string
}

export interface Term {
  id: string
  academicYearId: string
  label: string
  startDate: string
  endDate: string
}

export interface Holiday {
  id: string
  termId: string
  label: string
  startDate: string
  endDate: string
}

export interface Period {
  id: string
  number: number
  startTime: string
  endTime: string
}

export interface Block {
  id: string
  name: string
}

export interface SchoolClass {
  id: string
  academicYearId: string
  blockId: string
  name: string
  level: string
}

/** D1: weekday is ISO, 1 = Monday; school days are Monday to Friday (D3). */
export interface TimetableSlot {
  id: string
  classId: string
  weekday: number
  periodId: string
  teacherId: string
  subject: string
}

/** The admin's view of a staff member (GET /staff), as far as the timetable needs it. */
export interface Staff {
  id: string
  firstName: string
  lastName: string
  roles: { id: string; role: string; blockId: string | null }[]
  teaching?: { id: string; classId: string; subject: string }[]
}

export const PATHS = {
  years: '/academic-years',
  terms: '/terms',
  holidays: '/holidays',
  periods: '/periods',
  blocks: '/blocks',
  classes: '/classes',
  timetable: '/timetable-slots',
  staff: '/staff',
} as const

export const WEEKDAYS = [1, 2, 3, 4, 5] as const

/** "07:30:00" -> "07:30" */
export function shortTime(time: string): string {
  return time.slice(0, 5)
}

/** A query string from the defined values only. */
export function query(params: Record<string, string | undefined | null>): string {
  const entries = Object.entries(params).filter((e): e is [string, string] => Boolean(e[1]))
  return entries.length ? `?${new URLSearchParams(entries).toString()}` : ''
}
