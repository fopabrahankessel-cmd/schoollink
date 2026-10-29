import { Box, Button, Drawer, IconButton, Stack, Typography } from '@mui/material'
import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, Outlet, useLocation } from 'react-router'
import * as authApi from '../api/auth'
import { useAuth } from '../auth/useAuth'
import { theme, tokens } from '../theme'
import { LanguageSwitch } from './LanguageSwitch'
import { Mark } from './Mark'

interface NavItem {
  to: string
  label: string
}

/** The admin's screens (UC-A1, UC-A2), grouped as the school office thinks of them. */
const ADMIN_NAV: { group?: string; items: NavItem[] }[] = [
  { items: [{ to: '/', label: 'nav.home' }] },
  {
    group: 'nav.school',
    items: [
      { to: '/structure/calendar', label: 'nav.calendar' },
      { to: '/structure/periods', label: 'nav.periods' },
      { to: '/structure/classes', label: 'nav.classes' },
      { to: '/structure/timetable', label: 'nav.timetable' },
    ],
  },
  { group: 'nav.people', items: [{ to: '/staff', label: 'nav.staff' }] },
]

const phone = theme.breakpoints.down('md')

/**
 * The shell: a tinted frame (sidebar) around a white sheet holding the screen. On a phone the
 * frame folds into a 48px bar whose menu opens the same navigation.
 */
export function AppLayout() {
  const { pathname } = useLocation()
  const [menuOpen, setMenuOpen] = useState(false)
  const [menuPath, setMenuPath] = useState(pathname)
  // Following a link closes the phone menu.
  if (menuPath !== pathname) {
    setMenuPath(pathname)
    setMenuOpen(false)
  }

  return (
    <Box sx={{ minHeight: '100vh', bgcolor: tokens.ground, display: 'flex' }}>
      <Box
        component="aside"
        sx={{
          width: tokens.sidebarWidth,
          flex: 'none',
          position: 'sticky',
          top: 0,
          height: '100vh',
          display: 'flex',
          [phone]: { display: 'none' },
        }}
      >
        <Sidebar />
      </Box>
      <Box sx={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column' }}>
        <PhoneBar onMenu={() => setMenuOpen(true)} />
        <Box
          component="main"
          sx={{
            flex: 1,
            m: `${tokens.sheetInset}px ${tokens.sheetInset}px ${tokens.sheetInset}px 0`,
            bgcolor: tokens.surface,
            border: `1px solid ${tokens.border}`,
            borderRadius: `${tokens.radius.sheet}px`,
            px: { xs: 2, sm: 3, lg: 5 },
            py: { xs: 2.5, sm: 4 },
            [phone]: { m: 0, border: 0, borderRadius: 0 },
          }}
        >
          <Box sx={{ maxWidth: 1152 }}>
            <Outlet />
          </Box>
        </Box>
      </Box>
      <Drawer
        open={menuOpen}
        onClose={() => setMenuOpen(false)}
        slotProps={{ paper: { sx: { width: 280, bgcolor: tokens.ground } } }}
      >
        <Sidebar />
      </Drawer>
    </Box>
  )
}

function Sidebar() {
  const { t } = useTranslation()
  const { user, signOut } = useAuth()
  const { pathname } = useLocation()
  const schoolName = useSchoolName(user?.schoolId ?? undefined)
  const nav = user?.roles.includes('ADMIN') && !user.passwordChangeRequired ? ADMIN_NAV : []
  const isCurrent = (to: string) => to === pathname || (to !== '/' && pathname.startsWith(`${to}/`))

  return (
    <Stack sx={{ width: '100%', minHeight: '100%', px: 1.5, py: 2.5, gap: 3 }}>
      <Stack direction="row" spacing={1.25} sx={{ px: 1, alignItems: 'center' }}>
        <Mark size={28} />
        <Box sx={{ minWidth: 0 }}>
          <Typography component="p" sx={{ fontSize: 16, fontWeight: 600, lineHeight: 1.2 }}>
            {t('app.name')}
          </Typography>
          {schoolName && (
            <Typography component="p" variant="caption" color="text.secondary" noWrap title={schoolName}>
              {schoolName}
            </Typography>
          )}
        </Box>
      </Stack>

      {nav.length > 0 && (
        <Stack component="nav" aria-label={t('nav.label')} sx={{ gap: 2.5, flex: 1, overflowY: 'auto' }}>
          {nav.map((section) => (
            <Box key={section.group ?? 'main'}>
              {section.group && (
                <Typography component="p" variant="caption" color="text.secondary" sx={{ px: 1, mb: 0.5 }}>
                  {t(section.group)}
                </Typography>
              )}
              <Stack component="ul" sx={{ listStyle: 'none', m: 0, p: 0, gap: 0.25 }}>
                {section.items.map((item) => (
                  <li key={item.to}>
                    <NavLink item={item} current={isCurrent(item.to)} />
                  </li>
                ))}
              </Stack>
            </Box>
          ))}
        </Stack>
      )}

      <Stack sx={{ mt: 'auto', px: 1, gap: 1.5 }}>
        {user && (
          <Typography variant="caption" color="text.secondary">
            {user.roles.map((r) => t(`roles.${r}`)).join(', ')}
          </Typography>
        )}
        <Stack direction="row" sx={{ alignItems: 'center', justifyContent: 'space-between', gap: 1 }}>
          <LanguageSwitch />
          <Button size="small" onClick={signOut} sx={{ color: 'text.secondary' }}>
            {t('nav.signOut')}
          </Button>
        </Stack>
      </Stack>
    </Stack>
  )
}

function NavLink({ item, current }: { item: NavItem; current: boolean }) {
  const { t } = useTranslation()
  return (
    <Box
      component={Link}
      to={item.to}
      aria-current={current ? 'page' : undefined}
      sx={{
        display: 'flex',
        alignItems: 'center',
        minHeight: 32,
        px: 1,
        borderRadius: `${tokens.radius.card}px`,
        color: current ? tokens.text : tokens.muted,
        fontWeight: current ? 500 : 400,
        bgcolor: current ? tokens.active : 'transparent',
        textDecoration: 'none',
        transition: 'background-color 120ms ease, color 120ms ease',
        '@media (hover: hover) and (pointer: fine)': {
          '&:hover': { bgcolor: current ? tokens.active : tokens.raised, color: tokens.text },
        },
        [phone]: { minHeight: 44 },
      }}
    >
      {t(item.label)}
    </Box>
  )
}

function PhoneBar({ onMenu }: { onMenu: () => void }) {
  const { t } = useTranslation()
  return (
    <Stack
      direction="row"
      sx={{ display: 'none', [phone]: { display: 'flex' }, height: 48, px: 1, alignItems: 'center', gap: 1 }}
    >
      <IconButton onClick={onMenu} aria-label={t('nav.menu')} sx={{ width: 44, height: 44 }}>
        <svg width="20" height="20" viewBox="0 0 20 20" aria-hidden="true">
          <path d="M3 6h14M3 10h14M3 14h14" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
        </svg>
      </IconButton>
      <Mark size={22} />
      <Typography component="p" sx={{ fontWeight: 600 }}>
        {t('app.name')}
      </Typography>
    </Stack>
  )
}

/** The signed-in school's name (D16), from the public school list; nothing while unknown. */
function useSchoolName(schoolId: string | undefined): string | null {
  const { client } = useAuth()
  const [school, setSchool] = useState<{ id: string; name: string | null } | null>(null)
  useEffect(() => {
    if (!schoolId) return
    let live = true
    authApi
      .schools(client)
      .then((list) => live && setSchool({ id: schoolId, name: list.find((s) => s.id === schoolId)?.name ?? null }))
      .catch(() => undefined)
    return () => {
      live = false
    }
  }, [client, schoolId])
  return school && school.id === schoolId ? school.name : null
}
