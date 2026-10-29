import { useEffect, useMemo, useState, type ReactNode } from 'react'
import * as authApi from '../api/auth'
import type { ApiClient, UserInfo } from '../api/client'
import { rememberSchool } from './schoolMemory'
import { AuthContext, type AuthStatus, type AuthValue } from './useAuth'

export function AuthProvider({ client, children }: { client: ApiClient; children: ReactNode }) {
  const [user, setUser] = useState<UserInfo | null>(null)
  const [status, setStatus] = useState<AuthStatus>(() => (client.hasSession() ? 'loading' : 'signedOut'))

  useEffect(() => {
    const unsubscribe = client.onSession((next) => {
      setUser(next)
      setStatus(next ? 'signedIn' : 'signedOut')
    })
    // A reload keeps the session: trade the stored refresh token for a new pair.
    if (client.hasSession()) {
      client.refresh().catch(() => setStatus('signedOut'))
    }
    return () => {
      unsubscribe()
    }
  }, [client])

  const value = useMemo<AuthValue>(
    () => ({
      status,
      user,
      client,
      async signIn(login, password, schoolId) {
        client.startSession(await authApi.login(client, login, password, schoolId))
        if (schoolId) rememberSchool(schoolId)
      },
      async changePassword(currentPassword, newPassword) {
        client.startSession(await authApi.changePassword(client, currentPassword, newPassword))
      },
      signOut() {
        void client.signOut()
      },
    }),
    [client, status, user],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
