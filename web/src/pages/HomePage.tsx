import { Alert, Box, Chip, Stack, Typography } from '@mui/material'
import { useTranslation } from 'react-i18next'
import { WEB_ROLES } from '../auth/roles'
import { useAuth } from '../auth/useAuth'

/** Placeholder until the admin screens (tasks 1.16–1.19). */
export function HomePage() {
  const { t } = useTranslation()
  const { user } = useAuth()
  const roles = user?.roles ?? []
  const onWeb = roles.some((r) => WEB_ROLES.includes(r))

  return (
    <Box>
      <Typography variant="h5" component="h1" gutterBottom>
        {t('home.welcome')}
      </Typography>
      {!onWeb && (
        <Alert severity="info" sx={{ mb: 2 }}>
          {t('home.mobileOnly')}
        </Alert>
      )}
      <Typography variant="subtitle2" color="text.secondary" gutterBottom>
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
