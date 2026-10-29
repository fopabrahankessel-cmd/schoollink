import { AppBar, Box, Button, Container, Tab, Tabs, Toolbar, Typography } from '@mui/material'
import { useTranslation } from 'react-i18next'
import { Link, Outlet, useLocation } from 'react-router'
import { useAuth } from '../auth/useAuth'
import { LanguageSwitch } from './LanguageSwitch'

/** The admin's screens (UC-A1); other roles get theirs in later tasks. */
const ADMIN_NAV = [
  { to: '/', label: 'nav.home' },
  { to: '/structure/calendar', label: 'nav.calendar' },
  { to: '/structure/periods', label: 'nav.periods' },
  { to: '/structure/classes', label: 'nav.classes' },
  { to: '/structure/timetable', label: 'nav.timetable' },
] as const

export function AppLayout() {
  const { t } = useTranslation()
  const { user, signOut } = useAuth()
  const { pathname } = useLocation()
  const nav = user?.roles.includes('ADMIN') && !user.passwordChangeRequired ? ADMIN_NAV : []
  const current = nav.some((n) => n.to === pathname) ? pathname : false

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
        {nav.length > 0 && (
          <Box component="nav" sx={{ bgcolor: 'background.paper', color: 'text.primary', px: 2 }}>
            <Tabs value={current} variant="scrollable" scrollButtons="auto" aria-label={t('app.name')}>
              {nav.map((n) => (
                <Tab key={n.to} value={n.to} label={t(n.label)} component={Link} to={n.to} />
              ))}
            </Tabs>
          </Box>
        )}
      </AppBar>
      <Container component="main" maxWidth="lg" sx={{ py: 4 }}>
        <Outlet />
      </Container>
    </Box>
  )
}
