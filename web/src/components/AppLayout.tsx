import { AppBar, Box, Button, Container, Toolbar, Typography } from '@mui/material'
import { useTranslation } from 'react-i18next'
import { Outlet } from 'react-router'
import { useAuth } from '../auth/useAuth'
import { LanguageSwitch } from './LanguageSwitch'

export function AppLayout() {
  const { t } = useTranslation()
  const { signOut } = useAuth()
  return (
    <Box sx={{ minHeight: '100vh' }}>
      <AppBar position="static" elevation={0}>
        <Toolbar sx={{ gap: 2 }}>
          <Typography variant="h6" component="p" sx={{ flexGrow: 1, fontWeight: 700 }}>
            {t('app.name')}
          </Typography>
          <LanguageSwitch color="inherit" />
          <Button color="inherit" onClick={signOut}>
            {t('nav.signOut')}
          </Button>
        </Toolbar>
      </AppBar>
      <Container component="main" maxWidth="lg" sx={{ py: 4 }}>
        <Outlet />
      </Container>
    </Box>
  )
}
