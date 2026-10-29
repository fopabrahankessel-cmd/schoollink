import type { ReactNode } from 'react'
import { Navigate } from 'react-router'
import type { Role } from '../api/client'
import { useAuth } from './useAuth'

/**
 * Hides screens a role cannot use. Only a convenience: the server checks every request
 * (NFR-8) and answers 403 whatever the interface shows.
 */
export function RequireRole({ role, children }: { role: Role; children: ReactNode }) {
  const { user } = useAuth()
  return user?.roles.includes(role) ? children : <Navigate to="/" replace />
}
