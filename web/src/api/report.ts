/** UC-D9, D2: the students of the DM's classes absent from every timetabled period of a day. */
export const REPORT_PATH = '/reports/full-day-absences'
export const DEFAULT_DATE_PATH = `${REPORT_PATH}/default-date`

export const reportPath = (date: string) => `${REPORT_PATH}?date=${encodeURIComponent(date)}`

export interface ReportStudent {
  lastName: string
  firstName: string
  matricule: string
}

export interface ReportClass {
  className: string
  students: ReportStudent[]
}

export interface ReportBlock {
  blockName: string
  classes: ReportClass[]
}

/** A class with lessons never recorded that day: its full-day absences cannot be known. */
export interface UnrecordedClass {
  className: string
  periods: number[]
}

export interface FullDayReport {
  date: string
  /** The DM who asked for it, "First Last". */
  generatedBy: string
  studentCount: number
  classCount: number
  blocks: ReportBlock[]
  unrecorded: UnrecordedClass[]
}
