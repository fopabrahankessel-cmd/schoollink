// D43: the students and guardians of the school, kept up to date by the admin after the import.
// The school comes from the signed-in user (NFR-8). A phone goes in a body, never in a URL (NFR-9).
import { query } from './structure'

export type Sex = 'M' | 'F'
export type Relation = 'MOTHER' | 'FATHER' | 'GUARDIAN' | 'OTHER'
export type Language = 'FR' | 'EN'

export const RELATIONS: readonly Relation[] = ['MOTHER', 'FATHER', 'GUARDIAN', 'OTHER']

/** A row of the list. `leftOn`: the day the student left the school, null while enrolled. */
export interface StudentRow {
  id: string
  matricule: string
  firstName: string
  lastName: string
  classId: string
  className: string
  leftOn: string | null
  guardians: number
}

/** `children`: the guardian's children in the school, so a change is known to touch them all. */
export interface StudentGuardian {
  id: string
  name: string
  phone: string
  language: Language
  relation: Relation
  primary: boolean
  hasAccount: boolean
  children: number
  /** D46: the parent reset a forgotten password 3 times this school year; the admin may allow it again. */
  resetsLocked: boolean
}

/** The guardians: the primary first. */
export interface Student {
  id: string
  matricule: string
  firstName: string
  lastName: string
  sex: Sex
  birthYear: number
  classId: string
  className: string
  leftOn: string | null
  guardians: StudentGuardian[]
}

export interface StudentDetails {
  matricule: string
  firstName: string
  lastName: string
  sex: Sex
  birthYear: number
}

/** `language`: null for the school's. A phone already known in the school is that guardian. */
export interface GuardianRequest {
  phone: string
  name: string
  relation: Relation
  language: Language | null
  primary: boolean
}

export interface NewStudent extends StudentDetails {
  classId: string
  guardians: GuardianRequest[]
}

export interface GuardianUpdate {
  name: string
  language: Language
  phone: string
}

export const STUDENTS_PATH = '/students'

export const studentPath = (id: string) => `${STUDENTS_PATH}/${encodeURIComponent(id)}`

export const studentGuardianPath = (studentId: string, guardianId: string) =>
  `${studentPath(studentId)}/guardians/${encodeURIComponent(guardianId)}`

export const guardianPath = (id: string) => `/guardians/${encodeURIComponent(id)}`
/** D46: lets the guardian's parent account reset its password by SMS again. */
export const passwordResetsPath = (id: string) => `${guardianPath(id)}/password-resets`

/** The students of a class, or those found by name or matricule; those who left only when asked. */
export function studentsPath(filter: { classId?: string; q?: string; left?: boolean }): string {
  const params: Record<string, string> = {}
  if (filter.classId) params.classId = filter.classId
  if (filter.q?.trim()) params.q = filter.q.trim()
  if (filter.left) params.left = 'true'
  return STUDENTS_PATH + query(params)
}

/** "FICTIF Awa": the family name first, as on the school's lists. */
export function listName(s: { firstName: string; lastName: string }): string {
  return `${s.lastName.toUpperCase()} ${s.firstName}`
}
