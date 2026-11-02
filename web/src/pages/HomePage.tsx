import { Alert, Box, Chip, Stack, Typography } from '@mui/material'
import { useTranslation } from 'react-i18next'
import { WEB_ROLES } from '../auth/roles'
import { useAuth } from '../auth/useAuth'
import { ScopeDashboard } from './dm/ScopeDashboard'
import { SetupOverview } from './SetupOverview'

/** The admin sees how far the school year is set up, the DM their classes (UC-D8); other web roles get theirs in later tasks. */
export function HomePage() {
  const { t } = useTranslation()
  const { user } = useAuth()
  const roles = user?.roles ?? []
  if (roles.includes('ADMIN')) return <SetupOverview />
  if (roles.includes('DM')) return <ScopeDashboard />
  const onWeb = roles.some((r) => WEB_ROLES.includes(r))

  return (
    <Box>
      <Typography variant="h5" component="h1" gutterBottom>
        {t('home.welcome')}
      </Typography>
      {!onWeb && (
        <Alert severity="info" sx={{ mb: 2, maxWidth: 640 }}>
          {t('home.mobileOnly')}
        </Alert>
      )}
      <Typography color="text.secondary" gutterBottom>
        {t('home.roles')}
      </Typography>
      <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: 'wrap' }}>
        {roles.map((role) => (
          <Chip key={role} label={t(`roles.${role}`)} />
        ))}
      </Stack>
    </Box>
  )
}
