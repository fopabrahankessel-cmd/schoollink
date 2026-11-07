// Guardians API (UC-A4, D7). Admin only; the school comes from the signed-in user (NFR-8). A phone
// number travels in bodies only, never in a URL (NFR-9).

/** ACCOUNT: signed in to the app, gets push (D7). NO_ACCOUNT: gets SMS, and can sign in at any time. */
export type GuardianStatus = 'ACCOUNT' | 'NO_ACCOUNT'

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
  children: GuardianChild[]
}

export const guardiansPath = (classId: string) => `/guardians?classId=${encodeURIComponent(classId)}`

export const STATUSES: GuardianStatus[] = ['NO_ACCOUNT', 'ACCOUNT']
