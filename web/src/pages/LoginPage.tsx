import { Alert, Box, Button, Card, CardContent, TextField, Typography } from '@mui/material'
import { useEffect, useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Navigate, useLocation, useNavigate } from 'react-router'
import * as authApi from '../api/auth'
import { ApiError } from '../api/client'
import { errorMessage } from '../api/errors'
import { rememberedSchool } from '../auth/schoolMemory'
import { useAuth } from '../auth/useAuth'
import { LanguageSwitch } from '../components/LanguageSwitch'

/**
 * Sign-in with a phone or email (D15). D16: the school picker appears when the server needs it
 * (the account exists in several schools) or when a school was picked before on this device.
 */
export function LoginPage() {
  const { t } = useTranslation()
  const { status, client, signIn } = useAuth()
  const navigate = useNavigate()
  const from = (useLocation().state as { from?: string } | null)?.from ?? '/'
  const [login, setLogin] = useState('')
  const [password, setPassword] = useState('')
  const [schoolId, setSchoolId] = useState(() => rememberedSchool() ?? '')
  const [schools, setSchools] = useState<authApi.SchoolSummary[] | null>(null)
  const [error, setError] = useState<unknown>(null)
  const [submitting, setSubmitting] = useState(false)

  const loadSchools = (api: typeof client) =>
    authApi
      .schools(api)
      .then(setSchools)
      .catch((e: unknown) => setError(e))

  useEffect(() => {
    if (rememberedSchool() !== null) void loadSchools(client)
  }, [client])

  if (status === 'signedIn') return <Navigate to={from} replace />

  async function submit(event: FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      await signIn(login.trim(), password, schoolId || undefined)
      navigate(from, { replace: true })
    } catch (e) {
      setError(e)
      if (e instanceof ApiError && e.code === 'auth.school_required' && schools === null) await loadSchools(client)
    } finally {
      setSubmitting(false)
    }
  }

  const apiError = error instanceof ApiError ? error : null
  const schoolError = apiError?.messageFor('schoolId')
  const loginError = apiError?.messageFor('login')
  const passwordError = apiError?.messageFor('password')
  const general = error !== null && !schoolError && !loginError && !passwordError

  return (
    <Box sx={{ minHeight: '100vh', display: 'grid', placeItems: 'center', px: 2 }}>
      <Card sx={{ width: '100%', maxWidth: 420 }} variant="outlined">
        <CardContent sx={{ p: 4 }}>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'start', mb: 2 }}>
            <Box>
              <Typography variant="h5" component="p" sx={{ fontWeight: 700, color: 'primary.main' }}>
                {t('app.name')}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                {t('app.tagline')}
              </Typography>
            </Box>
            <LanguageSwitch />
          </Box>
          <Typography variant="h6" component="h1">
            {t('login.title')}
          </Typography>
          <Box component="form" onSubmit={submit} noValidate>
            {general && (
              <Alert severity="error" sx={{ mt: 2 }}>
                {errorMessage(error, t)}
              </Alert>
            )}
            {schools !== null && (
              <TextField
                select
                label={t('login.school')}
                value={schoolId}
                onChange={(e) => setSchoolId(e.target.value)}
                error={Boolean(schoolError)}
                helperText={schoolError ? `${t('login.schoolHint')} ${schoolError}` : undefined}
                slotProps={{ select: { native: true }, inputLabel: { shrink: true } }}
              >
                <option value="">{t('login.schoolChoose')}</option>
                {schools.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.name}
                  </option>
                ))}
              </TextField>
            )}
            <TextField
              label={t('login.login')}
              value={login}
              onChange={(e) => setLogin(e.target.value)}
              autoComplete="username"
              required
              autoFocus
              error={Boolean(loginError)}
              helperText={loginError}
            />
            <TextField
              label={t('login.password')}
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              autoComplete="current-password"
              required
              error={Boolean(passwordError)}
              helperText={passwordError}
            />
            <Button
              type="submit"
              variant="contained"
              size="large"
              fullWidth
              sx={{ mt: 2 }}
              loading={submitting}
              disabled={!login.trim() || !password}
            >
              {t('login.submit')}
            </Button>
          </Box>
        </CardContent>
      </Card>
    </Box>
  )
}
