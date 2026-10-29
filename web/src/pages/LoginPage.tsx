import { Alert, Box, Button, Stack, TextField, Typography } from '@mui/material'
import { useEffect, useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Navigate, useLocation, useNavigate } from 'react-router'
import * as authApi from '../api/auth'
import { ApiError } from '../api/client'
import { errorMessage } from '../api/errors'
import { rememberedSchool } from '../auth/schoolMemory'
import { useAuth } from '../auth/useAuth'
import { LanguageSwitch } from '../components/LanguageSwitch'
import { Mark } from '../components/Mark'
import { CalendarCheckIcon, ClockIcon, UsersThreeIcon } from '../icons'
import { theme, tokens } from '../theme'

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
    <Box
      sx={{
        minHeight: '100dvh',
        bgcolor: tokens.ground,
        display: 'grid',
        gridTemplateColumns: { xs: '1fr', md: 'minmax(0, 5fr) minmax(0, 6fr)' },
      }}
    >
      <BrandPanel />
      <Box sx={{ display: 'flex', flexDirection: 'column', px: 2, minWidth: 0 }}>
        <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center', py: 2, width: '100%', maxWidth: 440, mx: 'auto' }}>
          <Stack direction="row" spacing={1.25} sx={{ alignItems: 'center', visibility: { md: 'hidden' } }}>
            <Mark size={28} />
            <Typography component="p" sx={{ fontSize: 16, fontWeight: 700 }}>
              {t('app.name')}
            </Typography>
          </Stack>
          <LanguageSwitch />
        </Stack>
      <Box sx={{ flex: 1, display: 'grid', placeItems: { xs: 'start center', sm: 'center' }, pt: { xs: 2, sm: 0 }, pb: { xs: 4, sm: 10 } }}>
        <Box
          sx={{
            width: '100%',
            maxWidth: 440,
            bgcolor: tokens.surface,
            border: `1px solid ${tokens.border}`,
            borderRadius: `${tokens.radius.sheet}px`,
            boxShadow: tokens.shadow.raised,
            p: { xs: 3, sm: 4.5 },
          }}
        >
          <Typography variant="h5" component="h1">
            {t('login.title')}
          </Typography>
          <Typography color="text.secondary" sx={{ mt: 0.5 }}>
            {t('app.tagline')}
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
        </Box>
      </Box>
      </Box>
    </Box>
  )
}

const FEATURES = [
  { key: 'login.featureAttendance', icon: CalendarCheckIcon },
  { key: 'login.featureLateness', icon: ClockIcon },
  { key: 'login.featureFamilies', icon: UsersThreeIcon },
] as const

/** What SchoolLink does for a school, beside the form on a wide screen. Hidden on a phone. */
function BrandPanel() {
  const { t } = useTranslation()
  return (
    <Box
      sx={{
        position: 'relative',
        overflow: 'hidden',
        display: 'flex',
        flexDirection: 'column',
        p: 5,
        m: 1.25,
        borderRadius: `${tokens.radius.sheet}px`,
        color: '#ffffff',
        bgcolor: tokens.brandDark,
        backgroundImage: `radial-gradient(120% 80% at 0% 0%, ${tokens.brand} 0%, ${tokens.brandDark} 60%, #0b3f3b 100%)`,
        [theme.breakpoints.down('md')]: { display: 'none' },
      }}
    >
      {/* a register's ruled lines, as a quiet texture */}
      <Box
        aria-hidden
        sx={{
          position: 'absolute',
          inset: 0,
          opacity: 0.08,
          backgroundImage: 'repeating-linear-gradient(0deg, #ffffff 0 1px, transparent 1px 36px)',
          maskImage: 'linear-gradient(180deg, transparent, #000 40%, #000 70%, transparent)',
        }}
      />
      <Stack direction="row" spacing={1.25} sx={{ alignItems: 'center', position: 'relative' }}>
        <Box sx={{ borderRadius: '8px', boxShadow: '0 0 0 1px rgba(255,255,255,0.25)', lineHeight: 0 }}>
          <Mark size={32} />
        </Box>
        <Typography component="p" sx={{ fontSize: 18, fontWeight: 700 }}>
          {t('app.name')}
        </Typography>
      </Stack>
      <Box sx={{ mt: 'auto', position: 'relative', maxWidth: 440 }}>
        <Typography component="p" sx={{ fontSize: 32, fontWeight: 700, lineHeight: 1.15, letterSpacing: '-0.02em' }}>
          {t('login.panelTitle')}
        </Typography>
        <Stack component="ul" sx={{ listStyle: 'none', p: 0, m: 0, mt: 4, gap: 2 }}>
          {FEATURES.map(({ key, icon: FeatureIcon }) => (
            <Stack component="li" key={key} direction="row" sx={{ gap: 1.5, alignItems: 'flex-start' }}>
              <Box
                aria-hidden
                sx={{
                  width: 36,
                  height: 36,
                  flex: 'none',
                  display: 'grid',
                  placeItems: 'center',
                  borderRadius: '8px',
                  bgcolor: 'rgba(255,255,255,0.12)',
                  boxShadow: 'inset 0 0 0 1px rgba(255,255,255,0.16)',
                }}
              >
                <FeatureIcon size={20} />
              </Box>
              <Typography sx={{ color: 'rgba(255,255,255,0.9)', pt: 0.75 }}>{t(key)}</Typography>
            </Stack>
          ))}
        </Stack>
      </Box>
    </Box>
  )
}
