import {
  Alert,
  Box,
  Button,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from '@mui/material'
import { useCallback, useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useParams } from 'react-router'
import { ApiError } from '../../api/client'
import { errorMessage } from '../../api/errors'
import {
  ASSIGNABLE_ROLES,
  BLOCK_ROLES,
  fullName,
  roleLabel,
  STAFF_PATH,
  type RoleAssignment,
  type StaffMember,
  type TeachingAssignment,
} from '../../api/staff'
import { PATHS, query, type AcademicYear, type Block, type SchoolClass } from '../../api/structure'
import { useAuth } from '../../auth/useAuth'
import { ConfirmDialog } from '../../components/ConfirmDialog'
import { YearPicker } from '../../components/YearPicker'
import { useList } from '../../hooks/useList'
import { StaffFormDialog } from './StaffFormDialog'
import { StatusChip } from './StaffListPage'
import { TemporaryPasswordDialog } from './TemporaryPasswordDialog'

type Loaded = { id: string; staff: StaffMember | null; error: unknown }
type Confirm = { title: string; text: string; label: string; danger?: boolean; run: () => Promise<unknown> }

/**
 * UC-A2: one staff member: identity, status, temporary password (D18), roles with their block
 * (D5) and, for a teacher, the classes and subjects taught (BR-1). Every change is checked and
 * audited by the server (NFR-11).
 */
export function StaffDetailPage() {
  const { t } = useTranslation()
  const { id = '' } = useParams()
  const { client } = useAuth()
  const blocks = useList<Block>(PATHS.blocks)
  const classes = useList<SchoolClass>(PATHS.classes)
  const [loaded, setLoaded] = useState<Loaded | null>(null)
  const [actionError, setActionError] = useState<unknown>(null)
  const [editing, setEditing] = useState(false)
  const [password, setPassword] = useState<string | null>(null)
  const [confirm, setConfirm] = useState<Confirm | null>(null)
  // Only the latest request may set the page: a slow answer for another id is dropped.
  const latest = useRef(0)

  const load = useCallback(async () => {
    const request = ++latest.current
    let next: Loaded
    try {
      next = { id, staff: await client.request<StaffMember>(`${STAFF_PATH}/${id}`), error: null }
    } catch (error) {
      next = { id, staff: null, error }
    }
    if (request === latest.current) setLoaded(next)
  }, [client, id])

  useEffect(() => {
    void load()
  }, [load])

  const current = loaded?.id === id ? loaded : null
  const staff = current?.staff ?? null

  /** Runs a change, then shows the new state; a refusal is shown above the sections. */
  const change = useCallback(
    async (action: () => Promise<unknown>) => {
      setActionError(null)
      try {
        await action()
        await load()
      } catch (e) {
        setActionError(e)
        throw e
      }
    },
    [load],
  )

  if (current?.error) {
    return <Alert severity="error">{errorMessage(current.error, t)}</Alert>
  }
  if (staff === null) return null

  const base = `${STAFF_PATH}/${staff.id}`
  const isTeacher = staff.roles.some((r) => r.role === 'TEACHER')
  const className = (classId: string) => classes.items.find((c) => c.id === classId)?.name ?? ''

  return (
    <Stack spacing={3}>
      <Box>
        <Link to="/staff">{t('staff.back')}</Link>
        <Stack direction="row" spacing={2} sx={{ alignItems: 'center', mt: 1 }}>
          <Typography variant="h5" component="h1">
            {fullName(staff)}
          </Typography>
          <StatusChip staff={staff} />
        </Stack>
      </Box>
      {actionError !== null && (
        <Alert severity="error" onClose={() => setActionError(null)}>
          {errorMessage(actionError, t)}
        </Alert>
      )}

      <Section title={t('staff.identity')}>
        <Typography>{[staff.phone, staff.email].filter(Boolean).join(' · ')}</Typography>
        <Typography color="text.secondary">
          {t('staff.language')}: {t(`language.${staff.language.toLowerCase()}`)}
        </Typography>
        <Stack direction="row" spacing={1} useFlexGap sx={{ mt: 2, flexWrap: 'wrap' }}>
          <Button variant="outlined" onClick={() => setEditing(true)}>
            {t('crud.edit')}
          </Button>
          <Button
            variant="outlined"
            onClick={() =>
              setConfirm({
                title: t('staff.resetTitle'),
                text: t('staff.resetText'),
                label: t('staff.reset'),
                run: () =>
                  change(async () => {
                    const result = await client.request<{ temporaryPassword: string }>(`${base}/password-reset`, {
                      method: 'POST',
                    })
                    setPassword(result.temporaryPassword)
                  }),
              })
            }
          >
            {t('staff.reset')}
          </Button>
          {staff.status === 'DISABLED' ? (
            <Button variant="outlined" color="success" onClick={() => void change(() => setStatus('ACTIVE'))}>
              {t('staff.enable')}
            </Button>
          ) : (
            <Button
              variant="outlined"
              color="error"
              onClick={() =>
                setConfirm({
                  title: t('staff.disableTitle'),
                  text: t('staff.disableText'),
                  label: t('staff.disable'),
                  danger: true,
                  run: () => change(() => setStatus('DISABLED')),
                })
              }
            >
              {t('staff.disable')}
            </Button>
          )}
        </Stack>
      </Section>

      <Section title={t('staff.roles')}>
        {staff.roles.length === 0 ? (
          <Typography color="text.secondary">{t('staff.noRoles')}</Typography>
        ) : (
          <Table size="small">
            <TableBody>
              {staff.roles.map((role) => (
                <TableRow key={role.id}>
                  <TableCell>{roleLabel(role, blocks.items, t)}</TableCell>
                  <TableCell align="right">
                    <Button
                      size="small"
                      color="error"
                      aria-label={t('staff.removeRole', { role: roleLabel(role, blocks.items, t) })}
                      onClick={() =>
                        setConfirm({
                          title: t('staff.removeRoleTitle'),
                          text: t('staff.removeRole', { role: roleLabel(role, blocks.items, t) }),
                          label: t('staff.remove'),
                          danger: true,
                          run: () => change(() => client.request(`${base}/roles/${role.id}`, { method: 'DELETE' })),
                        })
                      }
                    >
                      {t('staff.remove')}
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
        <AddRoleForm
          blocks={blocks.items}
          taken={staff.roles}
          onAdd={(role, blockId) =>
            change(() => client.request(`${base}/roles`, { method: 'POST', body: { role, blockId } }))
          }
        />
      </Section>

      <Section title={t('staff.teaching')}>
        {!isTeacher ? (
          <Typography color="text.secondary">{t('staff.teachingNeedsRole')}</Typography>
        ) : (
          <>
            {staff.teaching.length === 0 ? (
              <Typography color="text.secondary">{t('staff.noTeaching')}</Typography>
            ) : (
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>{t('timetable.class')}</TableCell>
                    <TableCell>{t('staff.subject')}</TableCell>
                    <TableCell />
                  </TableRow>
                </TableHead>
                <TableBody>
                  {staff.teaching.map((a) => (
                    <TableRow key={a.id}>
                      <TableCell>{className(a.classId)}</TableCell>
                      <TableCell>{a.subject}</TableCell>
                      <TableCell align="right">
                        <Button
                          size="small"
                          color="error"
                          aria-label={t('staff.removeTeaching', { subject: a.subject, class: className(a.classId) })}
                          onClick={() =>
                            void change(() =>
                              client.request(`${base}/teaching/${a.id}`, { method: 'DELETE' }),
                            ).catch(() => undefined)
                          }
                        >
                          {t('staff.remove')}
                        </Button>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
            <AddTeachingForm
              teaching={staff.teaching}
              onAdd={(classId, subject) =>
                change(() => client.request(`${base}/teaching`, { method: 'POST', body: { classId, subject } }))
              }
            />
          </>
        )}
      </Section>

      <StaffFormDialog
        open={editing}
        staff={staff}
        onClose={() => setEditing(false)}
        onSubmit={async (request) => {
          await client.request(base, { method: 'PUT', body: request })
          setEditing(false)
          await load()
        }}
      />
      <TemporaryPasswordDialog password={password} name={fullName(staff)} onClose={() => setPassword(null)} />
      <ConfirmDialog
        open={confirm !== null}
        title={confirm?.title ?? ''}
        text={confirm?.text ?? ''}
        confirmLabel={confirm?.label ?? ''}
        danger={confirm?.danger}
        onClose={() => setConfirm(null)}
        onConfirm={() => void confirm?.run().catch(() => undefined)}
      />
    </Stack>
  )

  function setStatus(status: 'ACTIVE' | 'DISABLED') {
    return client.request(`${base}/status`, { method: 'PUT', body: { status } })
  }
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <Paper variant="outlined" sx={{ p: 2 }} component="section" aria-label={title}>
      <Typography variant="h6" component="h2" gutterBottom>
        {title}
      </Typography>
      {children}
    </Paper>
  )
}

/** D5: a DM or assistant DM is given a block; the server allows one DM per block. */
function AddRoleForm({
  blocks,
  taken,
  onAdd,
}: {
  blocks: Block[]
  taken: RoleAssignment[]
  onAdd: (role: string, blockId: string | null) => Promise<void>
}) {
  const { t } = useTranslation()
  const [role, setRole] = useState('')
  const [blockId, setBlockId] = useState('')
  const [saving, setSaving] = useState(false)
  const needsBlock = BLOCK_ROLES.some((r) => r === role)
  const choices = ASSIGNABLE_ROLES.filter((r) => BLOCK_ROLES.includes(r) || !taken.some((x) => x.role === r))

  async function submit(event: FormEvent) {
    event.preventDefault()
    setSaving(true)
    try {
      await onAdd(role, needsBlock ? blockId : null)
      setRole('')
      setBlockId('')
    } catch {
      // shown by the page
    } finally {
      setSaving(false)
    }
  }

  return (
    <Stack component="form" onSubmit={submit} direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ mt: 2, alignItems: { sm: 'center' } }}>
      <TextField
        select
        label={t('staff.newRole')}
        value={role}
        onChange={(e) => setRole(e.target.value)}
        margin="none"
        sx={{ minWidth: 240, maxWidth: { sm: 320 } }}
        slotProps={{ select: { native: true }, inputLabel: { shrink: true } }}
      >
        <option value="" disabled>
          {t('crud.choose')}
        </option>
        {choices.map((r) => (
          <option key={r} value={r}>
            {t(`roles.${r}`)}
          </option>
        ))}
      </TextField>
      {needsBlock && (
        <TextField
          select
          label={t('structure.block')}
          value={blockId}
          onChange={(e) => setBlockId(e.target.value)}
          margin="none"
          sx={{ minWidth: 180 }}
          slotProps={{ select: { native: true }, inputLabel: { shrink: true } }}
        >
          <option value="" disabled>
            {t('crud.choose')}
          </option>
          {blocks.map((b) => (
            <option key={b.id} value={b.id}>
              {b.name}
            </option>
          ))}
        </TextField>
      )}
      <Button type="submit" variant="contained" loading={saving} sx={{ flexShrink: 0, whiteSpace: 'nowrap' }} disabled={!role || (needsBlock && !blockId)}>
        {t('staff.addRole')}
      </Button>
    </Stack>
  )
}

/** BR-1: a teacher opens sessions only for the classes and subjects assigned here. */
function AddTeachingForm({
  teaching,
  onAdd,
}: {
  teaching: TeachingAssignment[]
  onAdd: (classId: string, subject: string) => Promise<void>
}) {
  const { t } = useTranslation()
  const years = useList<AcademicYear>(PATHS.years)
  const [yearId, setYearId] = useState('')
  const classes = useList<SchoolClass>(yearId ? PATHS.classes + query({ academicYearId: yearId }) : null)
  const [classId, setClassId] = useState('')
  const [subject, setSubject] = useState('')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const subjects = [...new Set(teaching.map((a) => a.subject))]

  async function submit(event: FormEvent) {
    event.preventDefault()
    setSaving(true)
    setError(null)
    try {
      await onAdd(classId, subject.trim())
      setClassId('')
    } catch (e) {
      setError(e)
    } finally {
      setSaving(false)
    }
  }

  const subjectError = error instanceof ApiError ? error.messageFor('subject') : undefined

  return (
    <Stack component="form" onSubmit={submit} direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ mt: 2, alignItems: { sm: 'flex-start' } }}>
      <YearPicker years={years.items} value={yearId} onChange={(id) => { setYearId(id); setClassId('') }} />
      <TextField
        select
        label={t('timetable.class')}
        value={classId}
        onChange={(e) => setClassId(e.target.value)}
        margin="none"
        sx={{ minWidth: 160 }}
        slotProps={{ select: { native: true }, inputLabel: { shrink: true } }}
      >
        <option value="" disabled>
          {t('crud.choose')}
        </option>
        {classes.items.map((c) => (
          <option key={c.id} value={c.id}>
            {c.name}
          </option>
        ))}
      </TextField>
      <TextField
        label={t('staff.subject')}
        value={subject}
        onChange={(e) => setSubject(e.target.value)}
        margin="none"
        error={Boolean(subjectError)}
        helperText={subjectError}
        slotProps={{ htmlInput: { list: 'staff-subjects' } }}
      />
      <datalist id="staff-subjects">
        {subjects.map((s) => (
          <option key={s} value={s} />
        ))}
      </datalist>
      <Button type="submit" variant="contained" loading={saving} disabled={!classId || !subject.trim()} sx={{ mt: { sm: 1 }, flexShrink: 0, whiteSpace: 'nowrap' }}>
        {t('staff.addTeaching')}
      </Button>
    </Stack>
  )
}
