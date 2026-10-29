import {
  Alert,
  Box,
  Button,
  Chip,
  InputAdornment,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  ToggleButton,
  ToggleButtonGroup,
  Typography,
} from '@mui/material'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate } from 'react-router'
import type { Role } from '../../api/client'
import { errorMessage } from '../../api/errors'
import { ASSIGNABLE_ROLES, fullName, STAFF_PATH, type CreatedStaff, type StaffMember } from '../../api/staff'
import { PATHS, type Block } from '../../api/structure'
import { useAuth } from '../../auth/useAuth'
import { PageHeader } from '../../components/PageHeader'
import { PersonAvatar } from '../../components/PersonAvatar'
import { useList } from '../../hooks/useList'
import { MagnifyingGlassIcon, UserPlusIcon, UsersThreeIcon } from '../../icons'
import { tokens } from '../../theme'
import { RoleChips } from './RoleChips'
import { StaffFormDialog } from './StaffFormDialog'
import { TemporaryPasswordDialog } from './TemporaryPasswordDialog'

type Filter = Role | 'ALL'

/** UC-A2: the school's staff accounts. A new account gets a temporary password (D18). */
export function StaffListPage() {
  const { t } = useTranslation()
  const { client } = useAuth()
  const navigate = useNavigate()
  const staff = useList<StaffMember>(STAFF_PATH)
  const blocks = useList<Block>(PATHS.blocks)
  const [adding, setAdding] = useState(false)
  const [created, setCreated] = useState<CreatedStaff | null>(null)
  const [search, setSearch] = useState('')
  const [filter, setFilter] = useState<Filter>('ALL')

  const withRole = (role: Filter) => (role === 'ALL' ? staff.items : staff.items.filter((s) => s.roles.some((r) => r.role === role)))
  const shown = useMemo(() => {
    const q = search.trim().toLowerCase()
    const inRole = filter === 'ALL' ? staff.items : staff.items.filter((s) => s.roles.some((r) => r.role === filter))
    return q ? inRole.filter((s) => `${fullName(s)} ${s.email ?? ''}`.toLowerCase().includes(q)) : inRole
  }, [staff.items, search, filter])

  return (
    <Stack>
      <PageHeader
        icon={UsersThreeIcon}
        title={t('staff.title')}
        description={t('staff.intro')}
        actions={
          <Button variant="contained" startIcon={<UserPlusIcon weight="bold" aria-hidden />} onClick={() => setAdding(true)}>
            {t('staff.add')}
          </Button>
        }
      />
      <Stack spacing={2}>
        {staff.error !== null && <Alert severity="error">{errorMessage(staff.error, t)}</Alert>}
        <Paper variant="outlined" sx={{ overflow: 'hidden' }}>
          <Stack
            direction={{ xs: 'column', md: 'row' }}
            sx={{ gap: 1.5, px: 2, py: 1.75, justifyContent: 'space-between', alignItems: { md: 'center' } }}
          >
            <Box sx={{ overflowX: 'auto', mx: { xs: -2, md: 0 }, px: { xs: 2, md: 0 } }}>
              <ToggleButtonGroup
                size="small"
                exclusive
                value={filter}
                onChange={(_, value: Filter | null) => value && setFilter(value)}
                aria-label={t('staff.filterLabel')}
                sx={{ '& .MuiToggleButton-root': { gap: 0.75, whiteSpace: 'nowrap', height: 32 } }}
              >
                {(['ALL', ...ASSIGNABLE_ROLES] as Filter[]).map((role) => (
                  <ToggleButton key={role} value={role}>
                    {role === 'ALL' ? t('staff.allRoles') : t(`roles.${role}`)}
                    {!staff.loading && (
                      <Box component="span" sx={{ fontSize: 11, opacity: 0.75 }}>
                        {withRole(role).length}
                      </Box>
                    )}
                  </ToggleButton>
                ))}
              </ToggleButtonGroup>
            </Box>
            <Stack direction="row" sx={{ alignItems: 'center', gap: 2 }}>
              {!staff.loading && (
                <Typography color="text.secondary" sx={{ flex: 'none', fontSize: 13 }}>
                  {t('staff.count', { count: shown.length })}
                </Typography>
              )}
              <TextField
                label={t('staff.search')}
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                margin="none"
                type="search"
                sx={{ width: { md: 260 }, '& .MuiInputLabel-root': visuallyHidden }}
                slotProps={{
                  input: {
                    startAdornment: (
                      <InputAdornment position="start">
                        <MagnifyingGlassIcon size={18} aria-hidden />
                      </InputAdornment>
                    ),
                  },
                  htmlInput: { placeholder: t('staff.search') },
                }}
              />
            </Stack>
          </Stack>
          {!staff.loading && shown.length === 0 ? (
            <Typography color="text.secondary" sx={{ px: 2, py: 5, textAlign: 'center', borderTop: `1px solid ${tokens.border}` }}>
              {t('staff.empty')}
            </Typography>
          ) : (
            <TableContainer sx={{ borderTop: `1px solid ${tokens.border}` }}>
              <Table size="small" sx={{ minWidth: 720, '& .MuiTableCell-root:first-of-type': { pl: 2 } }}>
                <TableHead>
                  <TableRow>
                    <TableCell>{t('staff.name')}</TableCell>
                    <TableCell>{t('staff.phone')}</TableCell>
                    <TableCell>{t('staff.roles')}</TableCell>
                    <TableCell>{t('staff.status')}</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {shown.map((s) => (
                    <TableRow key={s.id} hover>
                      <TableCell>
                        <Stack direction="row" sx={{ alignItems: 'center', gap: 1.5, minWidth: 0 }}>
                          <PersonAvatar id={s.id} firstName={s.firstName} lastName={s.lastName} />
                          <Box sx={{ minWidth: 0 }}>
                            {/* the id, never the phone, goes in the URL (NFR-9) */}
                            <Box component={Link} to={`/staff/${s.id}`} sx={{ color: tokens.text, display: 'block' }}>
                              {fullName(s)}
                            </Box>
                            {s.email && (
                              <Typography variant="caption" color="text.secondary" component="p" noWrap>
                                {s.email}
                              </Typography>
                            )}
                          </Box>
                        </Stack>
                      </TableCell>
                      <TableCell sx={{ color: 'text.secondary', whiteSpace: 'nowrap' }}>{s.phone ?? '—'}</TableCell>
                      <TableCell>
                        <RoleChips roles={s.roles} blocks={blocks.items} />
                      </TableCell>
                      <TableCell>
                        <StatusChip staff={s} />
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          )}
        </Paper>
      </Stack>

      <StaffFormDialog
        open={adding}
        staff={null}
        onClose={() => setAdding(false)}
        onSubmit={async (request) => {
          const result = await client.request<CreatedStaff>(STAFF_PATH, { method: 'POST', body: request })
          setAdding(false)
          setCreated(result)
          void staff.reload()
        }}
      />
      <TemporaryPasswordDialog
        password={created?.temporaryPassword ?? null}
        name={created ? fullName(created.staff) : ''}
        onClose={() => {
          const id = created?.staff.id
          setCreated(null)
          // next step: give the new account its roles
          if (id) navigate(`/staff/${id}`)
        }}
      />
    </Stack>
  )
}

// The search field is labelled for screen readers; its placeholder and icon say the same on screen.
const visuallyHidden = {
  position: 'absolute',
  width: '1px',
  height: '1px',
  overflow: 'hidden',
  clip: 'rect(0 0 0 0)',
  whiteSpace: 'nowrap',
} as const

export function StatusChip({ staff }: { staff: StaffMember }) {
  const { t } = useTranslation()
  // Colour only where something is pending; an active account is the normal case.
  if (staff.status === 'DISABLED') return <Chip size="small" label={t('staff.disabled')} />
  if (staff.passwordChangeRequired) return <Chip size="small" color="warning" label={t('staff.pending')} />
  return (
    <Stack direction="row" component="span" sx={{ alignItems: 'center', gap: 0.75, color: 'text.secondary' }}>
      <Box component="span" aria-hidden sx={{ width: 8, height: 8, borderRadius: 999, bgcolor: tokens.positive }} />
      {t('staff.active')}
    </Stack>
  )
}
