import { Avatar, Box, Drawer, IconButton, Stack, Tooltip, Typography } from '@mui/material'
import { alpha } from '@mui/material/styles'
import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, Outlet, useLocation } from 'react-router'
import * as authApi from '../api/auth'
import { useAuth } from '../auth/useAuth'
import {
  CalendarBlankIcon,
  ChalkboardTeacherIcon,
  ClockIcon,
  FileArrowUpIcon,
  HouseIcon,
  IdentificationCardIcon,
  ListIcon,
  SignOutIcon,
  TableIcon,
  UsersFourIcon,
  UsersThreeIcon,
  type Icon,
} from '../icons'
import { hover, theme, tokens } from '../theme'
import { LanguageSwitch } from './LanguageSwitch'
import { Mark } from './Mark'

interface NavItem {
  to: string
  label: string
  icon: Icon
}

/** The admin's screens (UC-A1, UC-A2), grouped as the school office thinks of them. */
const ADMIN_NAV: { group?: string; items: NavItem[] }[] = [
  { items: [{ to: '/', label: 'nav.home', icon: HouseIcon }] },
  {
    group: 'nav.school',
    items: [
      { to: '/structure/calendar', label: 'nav.calendar', icon: CalendarBlankIcon },
      { to: '/structure/periods', label: 'nav.periods', icon: ClockIcon },
      { to: '/structure/classes', label: 'nav.classes', icon: ChalkboardTeacherIcon },
      { to: '/structure/timetable', label: 'nav.timetable', icon: TableIcon },
    ],
  },
  {
    group: 'nav.people',
    items: [
      { to: '/staff', label: 'nav.staff', icon: UsersThreeIcon },
      { to: '/students/import', label: 'nav.import', icon: FileArrowUpIcon },
      { to: '/guardians', label: 'nav.guardians', icon: UsersFourIcon },
    ],
  },
]

const phone = theme.breakpoints.down('md')

/**
 * The shell: a fixed frame the height of the window. The sidebar and the white sheet each scroll on
 * their own, so the navigation stays in place while a long screen scrolls. On a phone the frame
 * folds into a bar whose menu opens the same navigation.
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
  // A new screen starts at its top, not where the previous one was scrolled to.
  const sheet = useRef<HTMLElement>(null)
  useEffect(() => {
    sheet.current?.scrollTo?.({ top: 0 })
  }, [pathname])

  return (
    <Box sx={{ height: '100dvh', bgcolor: tokens.ground, display: 'flex', overflow: 'hidden' }}>
      <Box
        component="aside"
        sx={{ width: tokens.sidebarWidth, flex: 'none', display: 'flex', minHeight: 0, [phone]: { display: 'none' } }}
      >
        <Sidebar />
      </Box>
      <Box sx={{ flex: 1, minWidth: 0, minHeight: 0, display: 'flex', flexDirection: 'column' }}>
        <PhoneBar onMenu={() => setMenuOpen(true)} />
        <Box
          ref={sheet}
          component="main"
          id="sheet"
          tabIndex={-1}
          sx={{
            flex: 1,
            minHeight: 0,
            overflowY: 'auto',
            overflowX: 'hidden',
            overscrollBehavior: 'contain',
            scrollbarGutter: 'stable',
            outline: 'none',
            m: `${tokens.sheetInset}px ${tokens.sheetInset}px ${tokens.sheetInset}px 0`,
            bgcolor: tokens.surface,
            border: `1px solid ${tokens.border}`,
            borderRadius: `${tokens.radius.sheet}px`,
            boxShadow: tokens.shadow.card,
            [phone]: { m: 0, border: 0, borderRadius: 0, boxShadow: 'none' },
          }}
        >
          <Box sx={{ maxWidth: 1200, px: { xs: 2, sm: 3, lg: 5 }, pb: { xs: 4, sm: 6 } }}>
            <Outlet />
          </Box>
        </Box>
      </Box>
      <Drawer
        open={menuOpen}
        onClose={() => setMenuOpen(false)}
        slotProps={{ paper: { sx: { width: 288, bgcolor: tokens.ground } } }}
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
  const roles = user?.roles.map((r) => t(`roles.${r}`)).join(', ')

  return (
    <Stack sx={{ width: '100%', height: '100%', minHeight: 0 }}>
      <Stack direction="row" spacing={1.25} sx={{ px: 2.5, pt: 2.5, pb: 2, alignItems: 'center', flex: 'none' }}>
        <Mark size={32} />
        <Box sx={{ minWidth: 0 }}>
          <Typography component="p" sx={{ fontSize: 16, fontWeight: 700, lineHeight: 1.2, letterSpacing: '-0.01em' }}>
            {t('app.name')}
          </Typography>
          {schoolName && (
            <Typography component="p" variant="caption" color="text.secondary" noWrap title={schoolName}>
              {schoolName}
            </Typography>
          )}
        </Box>
      </Stack>

      <Box component="nav" aria-label={t('nav.label')} sx={{ flex: 1, minHeight: 0, overflowY: 'auto', px: 1.5, py: 1 }}>
        <Stack sx={{ gap: 2.5 }}>
          {nav.map((section) => (
            <Box key={section.group ?? 'main'}>
              {section.group && (
                <Typography component="p" variant="caption" sx={{ px: 1.25, mb: 0.75, fontWeight: 600, color: tokens.muted }}>
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
      </Box>

      {user && (
        <Box sx={{ flex: 'none', p: 1.5 }}>
          <Stack
            sx={{
              p: 1.5,
              gap: 1.5,
              bgcolor: tokens.surface,
              border: `1px solid ${tokens.border}`,
              borderRadius: `${tokens.radius.card}px`,
              boxShadow: tokens.shadow.card,
            }}
          >
            <Stack direction="row" sx={{ alignItems: 'center', gap: 1.25, minWidth: 0 }}>
              <Avatar sx={{ width: 32, height: 32, bgcolor: tokens.brandSoft, color: tokens.brandDark }}>
                <IdentificationCardIcon size={18} aria-hidden />
              </Avatar>
              <Box sx={{ minWidth: 0, flex: 1 }}>
                <Typography component="p" sx={{ fontSize: 13, fontWeight: 600, lineHeight: 1.3 }} noWrap title={roles}>
                  {roles}
                </Typography>
                <Typography component="p" variant="caption" color="text.secondary">
                  {t('nav.signedIn')}
                </Typography>
              </Box>
            </Stack>
            <Stack direction="row" sx={{ alignItems: 'center', justifyContent: 'space-between', gap: 1 }}>
              <LanguageSwitch />
              <Tooltip title={t('nav.signOut')}>
                <IconButton onClick={signOut} aria-label={t('nav.signOut')} size="small" sx={{ width: 36, height: 36 }}>
                  <SignOutIcon size={18} aria-hidden />
                </IconButton>
              </Tooltip>
            </Stack>
          </Stack>
        </Box>
      )}
    </Stack>
  )
}

function NavLink({ item, current }: { item: NavItem; current: boolean }) {
  const { t } = useTranslation()
  const ItemIcon = item.icon
  return (
    <Box
      component={Link}
      to={item.to}
      aria-current={current ? 'page' : undefined}
      sx={{
        display: 'flex',
        alignItems: 'center',
        gap: 1.25,
        minHeight: 38,
        px: 1.25,
        borderRadius: `${tokens.radius.control}px`,
        color: current ? tokens.brandDark : tokens.muted,
        fontWeight: current ? 700 : 500,
        bgcolor: current ? tokens.surface : 'transparent',
        boxShadow: current ? tokens.shadow.card : 'none',
        textDecoration: 'none',
        transition: 'background-color 150ms ease, color 150ms ease',
        '& svg': { flex: 'none', color: current ? tokens.brand : 'inherit' },
        [hover]: {
          '&:hover': { bgcolor: current ? tokens.surface : alpha(tokens.surface, 0.6), color: current ? tokens.brandDark : tokens.text, textDecoration: 'none' },
        },
        [phone]: { minHeight: 44 },
      }}
    >
      <ItemIcon size={20} weight={current ? 'fill' : 'regular'} aria-hidden />
      {t(item.label)}
    </Box>
  )
}

function PhoneBar({ onMenu }: { onMenu: () => void }) {
  const { t } = useTranslation()
  return (
    <Stack
      direction="row"
      sx={{
        display: 'none',
        [phone]: { display: 'flex' },
        flex: 'none',
        height: 56,
        px: 1,
        alignItems: 'center',
        gap: 1,
        bgcolor: tokens.surface,
        borderBottom: `1px solid ${tokens.border}`,
      }}
    >
      <IconButton onClick={onMenu} aria-label={t('nav.menu')} sx={{ width: 44, height: 44, color: tokens.text }}>
        <ListIcon size={22} aria-hidden />
      </IconButton>
      <Mark size={26} />
      <Typography component="p" sx={{ fontWeight: 700 }}>
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
