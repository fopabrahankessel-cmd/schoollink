import {
  Alert,
  Button,
  Chip,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from '@mui/material'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate } from 'react-router'
import { errorMessage } from '../../api/errors'
import { fullName, STAFF_PATH, type CreatedStaff, type StaffMember } from '../../api/staff'
import { PATHS, type Block } from '../../api/structure'
import { useAuth } from '../../auth/useAuth'
import { useList } from '../../hooks/useList'
import { RoleChips } from './RoleChips'
import { StaffFormDialog } from './StaffFormDialog'
import { TemporaryPasswordDialog } from './TemporaryPasswordDialog'

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

  const shown = useMemo(() => {
    const q = search.trim().toLowerCase()
    return q ? staff.items.filter((s) => `${fullName(s)} ${s.email ?? ''}`.toLowerCase().includes(q)) : staff.items
  }, [staff.items, search])

  return (
    <Stack spacing={3}>
      <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center' }}>
        <Typography variant="h5" component="h1">
          {t('staff.title')}
        </Typography>
        <Button variant="contained" onClick={() => setAdding(true)}>
          {t('staff.add')}
        </Button>
      </Stack>
      {staff.error !== null && <Alert severity="error">{errorMessage(staff.error, t)}</Alert>}
      <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'flex-end', gap: 2 }}>
        <TextField
          label={t('staff.search')}
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          margin="none"
          sx={{ maxWidth: 320 }}
          type="search"
        />
        {!staff.loading && (
          <Typography color="text.secondary" sx={{ flex: 'none', pb: 1 }}>
            {t('staff.count', { count: shown.length })}
          </Typography>
        )}
      </Stack>
      {!staff.loading && shown.length === 0 ? (
        <Typography color="text.secondary">{t('staff.empty')}</Typography>
      ) : (
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>{t('staff.name')}</TableCell>
                <TableCell>{t('staff.contact')}</TableCell>
                <TableCell>{t('staff.roles')}</TableCell>
                <TableCell>{t('staff.status')}</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {shown.map((s) => (
                <TableRow key={s.id} hover>
                  <TableCell sx={{ fontWeight: 500 }}>
                    {/* the id, never the phone, goes in the URL (NFR-9) */}
                    <Link to={`/staff/${s.id}`}>{fullName(s)}</Link>
                  </TableCell>
                  <TableCell sx={{ color: 'text.secondary' }}>{[s.phone, s.email].filter(Boolean).join(' · ')}</TableCell>
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

export function StatusChip({ staff }: { staff: StaffMember }) {
  const { t } = useTranslation()
  // Colour only where something is pending; an active account is the normal case.
  if (staff.status === 'DISABLED') return <Chip size="small" label={t('staff.disabled')} />
  if (staff.passwordChangeRequired) return <Chip size="small" color="warning" label={t('staff.pending')} />
  return (
    <Typography component="span" color="text.secondary">
      {t('staff.active')}
    </Typography>
  )
}
