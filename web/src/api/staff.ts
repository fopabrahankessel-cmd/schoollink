// Staff accounts and assignments API (UC-A2, D5, D15, D18). Admin only for every change.
import type { Role } from './client'
import type { Block } from './structure'

export interface RoleAssignment {
  id: string
  role: Role
  blockId: string | null
}

export interface TeachingAssignment {
  id: string
  classId: string
  subject: string
}

/** The admin's view of a staff member; never contains a password. */
export interface StaffMember {
  id: string
  firstName: string
  lastName: string
  phone: string | null
  email: string | null
  language: 'FR' | 'EN'
  status: 'ACTIVE' | 'DISABLED' | 'INVITED'
  passwordChangeRequired: boolean
  roles: RoleAssignment[]
  teaching: TeachingAssignment[]
}

export interface StaffRequest {
  firstName: string
  lastName: string
  phone: string | null
  email: string | null
  language: 'FR' | 'EN' | null
}

/** D18: shown once, never stored in clear. */
export interface CreatedStaff {
  staff: StaffMember
  temporaryPassword: string
}

/** The roles an admin can give to staff (the server refuses the others). */
export const ASSIGNABLE_ROLES: readonly Role[] = ['TEACHER', 'DM', 'ASSISTANT_DM', 'COUNSELLOR']

/** D5: these roles are given per block. */
export const BLOCK_ROLES: readonly Role[] = ['DM', 'ASSISTANT_DM']

export const STAFF_PATH = '/staff'

export function fullName(s: { firstName: string; lastName: string }): string {
  return `${s.firstName} ${s.lastName}`
}

/** "Surveillant Général · Bloc A": D5 roles show their block. */
export function roleLabel(role: RoleAssignment, blocks: Block[], t: (key: string) => string): string {
  const block = role.blockId ? blocks.find((b) => b.id === role.blockId)?.name : undefined
  return block ? `${t(`roles.${role.role}`)} · ${block}` : t(`roles.${role.role}`)
}
