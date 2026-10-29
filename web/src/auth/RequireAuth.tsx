import { Box, CircularProgress } from '@mui/material'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Navigate, useLocation } from 'react-router'
import { useAuth } from './useAuth'

export const CHANGE_PASSWORD_PATH = '/change-password'

/**
 * Signed-in pages. D18: while the password is temporary the server answers only /me and
 * /me/password, so every page but the password page redirects there.
 */
export function RequireAuth({ children }: { children: ReactNode }) {
  const { status, user } = useAuth()
  const location = useLocation()
  if (status === 'loading') return <Loading />
  if (status === 'signedOut' || user === null) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }
  if (user.passwordChangeRequired && location.pathname !== CHANGE_PASSWORD_PATH) {
    return <Navigate to={CHANGE_PASSWORD_PATH} replace />
  }
  if (!user.passwordChangeRequired && location.pathname === CHANGE_PASSWORD_PATH) {
    return <Navigate to="/" replace />
  }
  return children
}

export function Loading() {
  const { t } = useTranslation()
  return (
    <Box sx={{ display: 'grid', placeItems: 'center', minHeight: '60vh' }}>
      <CircularProgress aria-label={t('common.loading')} />
    </Box>
  )
}
