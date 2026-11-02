// Guardians and invitation codes API (UC-A4, D7, D13). Admin only; the school comes from the
// signed-in user (NFR-8). A phone number travels in bodies only, never in a URL (NFR-9).

/** ACCOUNT: activated in the app, gets push (D7). The others get SMS and can be invited. */
export type GuardianStatus = 'ACCOUNT' | 'INVITED' | 'EXPIRED' | 'NOT_INVITED'

export type Relation = 'MOTHER' | 'FATHER' | 'GUARDIAN' | 'OTHER'

export interface GuardianChild {
  id: string
  firstName: string
  lastName: string
  classId: string
  relation: Relation
  primary: boolean
}

export interface Guardian {
  id: string
  name: string
  phone: string
  language: 'FR' | 'EN'
  status: GuardianStatus
  /** The latest code, whatever its state; null when never invited. */
  invitedAt: string | null
  expiresAt: string | null
  children: GuardianChild[]
}

/** The code itself is in the answer but never shown: D13 sends it by SMS, no printed sheet. */
export interface ClassInvitations {
  issued: { guardianId: string }[]
  skippedWithAccount: number
}

export const guardiansPath = (classId: string) => `/guardians?classId=${encodeURIComponent(classId)}`
export const guardianInvitationPath = (guardianId: string) => `/guardians/${encodeURIComponent(guardianId)}/invitations`
export const classInvitationsPath = (classId: string) => `/classes/${encodeURIComponent(classId)}/invitations`

export const STATUSES: GuardianStatus[] = ['NOT_INVITED', 'EXPIRED', 'INVITED', 'ACCOUNT']
