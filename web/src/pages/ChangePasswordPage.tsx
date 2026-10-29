import { Alert, Box, Button, Card, CardContent, TextField, Typography } from '@mui/material'
import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'
import { ApiError } from '../api/client'
import { errorMessage } from '../api/errors'
import { useAuth } from '../auth/useAuth'

/** D18: a temporary password is replaced before anything else. Limits match the server (8–72). */
export function ChangePasswordPage() {
  const { t } = useTranslation()
  const { changePassword } = useAuth()
  const navigate = useNavigate()
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState<unknown>(null)
  const [submitting, setSubmitting] = useState(false)

  const localError =
    next.length > 0 && next.length < 8
      ? t('password.tooShort')
      : next.length > 72
        ? t('password.tooLong')
        : undefined
  const mismatch = confirm.length > 0 && confirm !== next ? t('password.mismatch') : undefined

  async function submit(event: FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      await changePassword(current, next)
      navigate('/', { replace: true })
    } catch (e) {
      setError(e)
    } finally {
      setSubmitting(false)
    }
  }

  const apiError = error instanceof ApiError ? error : null
  const currentError = apiError?.messageFor('currentPassword')
  const nextError = apiError?.messageFor('newPassword')

  return (
    <Box sx={{ display: 'grid', placeItems: 'center', py: 6, px: 2 }}>
      <Card sx={{ width: '100%', maxWidth: 440 }} variant="outlined">
        <CardContent sx={{ p: 4 }}>
          <Typography variant="h6" component="h1">
            {t('password.title')}
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
            {t('password.intro')}
          </Typography>
          <Box component="form" onSubmit={submit} noValidate>
            {error !== null && !currentError && !nextError && (
              <Alert severity="error" sx={{ mt: 2 }}>
                {errorMessage(error, t)}
              </Alert>
            )}
            <TextField
              label={t('password.current')}
              type="password"
              value={current}
              onChange={(e) => setCurrent(e.target.value)}
              autoComplete="current-password"
              required
              autoFocus
              error={Boolean(currentError)}
              helperText={currentError}
            />
            <TextField
              label={t('password.new')}
              type="password"
              value={next}
              onChange={(e) => setNext(e.target.value)}
              autoComplete="new-password"
              required
              error={Boolean(localError ?? nextError)}
              helperText={localError ?? nextError ?? t('password.rules')}
            />
            <TextField
              label={t('password.confirm')}
              type="password"
              value={confirm}
              onChange={(e) => setConfirm(e.target.value)}
              autoComplete="new-password"
              required
              error={Boolean(mismatch)}
              helperText={mismatch}
            />
            <Button
              type="submit"
              variant="contained"
              size="large"
              fullWidth
              sx={{ mt: 2 }}
              loading={submitting}
              disabled={!current || !next || next !== confirm || localError !== undefined}
            >
              {t('password.submit')}
            </Button>
          </Box>
        </CardContent>
      </Card>
    </Box>
  )
}
