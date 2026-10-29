import { createContext, useContext } from 'react'
import type { ApiClient, UserInfo } from '../api/client'

export type AuthStatus = 'loading' | 'signedOut' | 'signedIn'

export interface AuthValue {
  status: AuthStatus
  user: UserInfo | null
  client: ApiClient
  signIn: (login: string, password: string, schoolId?: string) => Promise<void>
  changePassword: (currentPassword: string, newPassword: string) => Promise<void>
  signOut: () => void
}

export const AuthContext = createContext<AuthValue | null>(null)

export function useAuth(): AuthValue {
  const value = useContext(AuthContext)
  if (value === null) throw new Error('useAuth outside AuthProvider')
  return value
}
