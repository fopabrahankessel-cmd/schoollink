import { Alert, Box, Button, Paper, Stack, Typography } from '@mui/material'
import { useCallback, useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useParams } from 'react-router'
import { errorMessage } from '../../api/errors'
import { guardianPath, listName, studentGuardianPath, studentPath, type Student, type StudentGuardian } from '../../api/students'
import { useAuth } from '../../auth/useAuth'
import { PersonAvatar } from '../../components/PersonAvatar'
import { Pill } from '../../components/Pill'
import { formatDate } from '../../format'
import {
  ArrowCounterClockwiseIcon,
  ArrowLeftIcon,
  ArrowRightIcon,
  PencilSimpleIcon,
  PhoneIcon,
  SignOutIcon,
  TranslateIcon,
  UserPlusIcon,
  UsersFourIcon,
} from '../../icons'
import { tokens } from '../../theme'
import {
  AddGuardianDialog,
  ClassDialog,
  EditGuardianDialog,
  LeaveDialog,
  RemoveGuardianDialog,
  StudentDetailsDialog,
  type GuardianEdit,
} from './dialogs'
import { ADMIN_STUDENTS } from './StudentsAdminPage'

type Loaded = { id: string; student: Student | null; error: unknown }
type Open = 'details' | 'class' | 'leave' | 'return' | 'guardian' | null

function today(): string {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
}

/**
 * D43: one student, for the admin: the details, the class, whether the student left the school,
 * and the guardians. Each step that stops something (a class change, a departure, a guardian
 * removed, a new phone) says what it changes before it is done; the server checks and audits
 * every one (NFR-8, NFR-11).
 */
export function StudentAdminPage() {
  const { t, i18n } = useTranslation()
  const { id = '' } = useParams()
  const { client } = useAuth()
  const [loaded, setLoaded] = useState<Loaded | null>(null)
  const [open, setOpen] = useState<Open>(null)
  const [editing, setEditing] = useState<StudentGuardian | null>(null)
  const [removing, setRemoving] = useState<StudentGuardian | null>(null)
  // Only the latest request may set the page: a slow answer for another id is dropped.
  const latest = useRef(0)

  const load = useCallback(async () => {
    const request = ++latest.current
    let next: Loaded
    try {
      next = { id, student: await client.request<Student>(studentPath(id)), error: null }
    } catch (error) {
      next = { id, student: null, error }
    }
    if (request === latest.current) setLoaded(next)
  }, [client, id])

  useEffect(() => {
    void load()
  }, [load])

  const current = loaded?.id === id ? loaded : null
  const student = current?.student ?? null

  if (current?.error) return <Alert severity="error">{errorMessage(current.error, t)}</Alert>
  if (student === null) return null

  const base = studentPath(student.id)
  const left = student.leftOn !== null

  /** Sends a change; the dialog closes and the page shows the new state. A refusal stays in the dialog. */
  async function save(action: () => Promise<unknown>, close: () => void) {
    await action()
    close()
    await load()
  }
  const closeOpen = () => setOpen(null)

  async function saveGuardian(guardian: StudentGuardian, edit: GuardianEdit) {
    const g = edit.guardian
    // the guardian is shared by their children; the link is this student's only
    if (g.name !== guardian.name || g.phone !== guardian.phone || g.language !== guardian.language) {
      await client.request(guardianPath(guardian.id), { method: 'PUT', body: g })
    }
    if (edit.relation !== guardian.relation || edit.primary !== guardian.primary) {
      await client.request(studentGuardianPath(student!.id, guardian.id), { method: 'PUT', body: { relation: edit.relation, primary: edit.primary } })
    }
  }

  return (
    <Stack spacing={3} sx={{ maxWidth: 920, pt: { xs: 2, sm: 3.5 } }}>
      <Box>
        <Box component={Link} to={ADMIN_STUDENTS} sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.75, fontSize: 13, color: tokens.muted, minHeight: 24 }}>
          <ArrowLeftIcon size={16} aria-hidden />
          {t('roster.back')}
        </Box>
      </Box>

      <Paper variant="outlined" component="section" aria-label={t('roster.identity')} sx={{ overflow: 'hidden' }}>
        <Stack sx={{ p: 2.5, gap: 2.5, borderTop: `4px solid ${left ? tokens.border : tokens.brand}` }}>
          <Stack direction="row" sx={{ gap: 2, alignItems: 'center', minWidth: 0 }}>
            <PersonAvatar id={student.id} firstName={student.firstName} lastName={student.lastName} size={64} />
            <Box sx={{ minWidth: 0, flex: 1 }}>
              <Stack direction="row" sx={{ alignItems: 'center', gap: 1.5, flexWrap: 'wrap' }}>
                <Typography variant="h5" component="h1">
                  {listName(student)}
                </Typography>
                {student.leftOn && <Pill tone="neutral">{t('roster.leftOn', { date: formatDate(student.leftOn, i18n.language) })}</Pill>}
              </Stack>
              <Box component="dl" sx={{ m: 0, mt: 1, display: 'flex', flexWrap: 'wrap', columnGap: 3.5, rowGap: 1 }}>
                <Fact label={t('roster.matricule')} value={student.matricule} />
                <Fact label={left ? t('roster.lastClass') : t('roster.class')} value={student.className} />
                <Fact label={t('roster.sex')} value={t(`roster.sexValue.${student.sex}`)} />
                {/* NFR-10: the year only */}
                <Fact label={t('roster.birthYear')} value={String(student.birthYear)} />
              </Box>
            </Box>
          </Stack>
          <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: 'wrap' }}>
            <Button variant="outlined" startIcon={<PencilSimpleIcon aria-hidden />} onClick={() => setOpen('details')}>
              {t('roster.editTitle')}
            </Button>
            {left ? (
              <Button variant="outlined" color="success" startIcon={<ArrowCounterClockwiseIcon aria-hidden />} onClick={() => setOpen('return')}>
                {t('roster.comeBack')}
              </Button>
            ) : (
              <>
                <Button variant="outlined" startIcon={<ArrowRightIcon aria-hidden />} onClick={() => setOpen('class')}>
                  {t('roster.changeClass')}
                </Button>
                <Button variant="outlined" color="error" startIcon={<SignOutIcon aria-hidden />} onClick={() => setOpen('leave')}>
                  {t('roster.leave')}
                </Button>
              </>
            )}
          </Stack>
          {left && (
            <Typography variant="body2" color="text.secondary">
              {t('roster.leftNote')}
            </Typography>
          )}
        </Stack>
      </Paper>

      <Paper variant="outlined" component="section" aria-label={t('roster.guardians')} sx={{ overflow: 'hidden' }}>
        <Stack direction="row" sx={{ px: 2.5, py: 2, gap: 1.5, alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap' }}>
          <Stack direction="row" sx={{ alignItems: 'center', gap: 1.5 }}>
            <Box aria-hidden sx={{ width: 36, height: 36, display: 'grid', placeItems: 'center', borderRadius: `${tokens.radius.control}px`, bgcolor: tokens.brandSoft, color: tokens.brandDark }}>
              <UsersFourIcon size={20} />
            </Box>
            <Typography variant="h6" component="h2">
              {t('roster.guardians')}
            </Typography>
          </Stack>
          <Button variant="outlined" size="small" startIcon={<UserPlusIcon aria-hidden />} onClick={() => setOpen('guardian')}>
            {t('roster.addGuardian')}
          </Button>
        </Stack>
        <Box component="ul" sx={{ listStyle: 'none', m: 0, p: 0 }}>
          {student.guardians.map((g) => (
            <Stack
              component="li"
              key={g.id}
              direction={{ xs: 'column', sm: 'row' }}
              sx={{ px: 2.5, py: 1.75, gap: 1.5, alignItems: { sm: 'center' }, justifyContent: 'space-between', borderTop: `1px solid ${tokens.border}` }}
            >
              <Box sx={{ minWidth: 0 }}>
                <Stack direction="row" sx={{ alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
                  <Typography sx={{ fontWeight: 600 }}>{g.name}</Typography>
                  <Typography component="span" color="text.secondary" sx={{ fontSize: 13 }}>
                    {t(`roster.relationValue.${g.relation}`)}
                  </Typography>
                  {g.primary && <Pill tone="brand">{t('roster.primaryGuardian')}</Pill>}
                </Stack>
                <Stack direction="row" sx={{ mt: 0.5, columnGap: 2, rowGap: 0.5, flexWrap: 'wrap', alignItems: 'center', color: tokens.muted, fontSize: 13 }}>
                  <Stack direction="row" component="span" sx={{ alignItems: 'center', gap: 0.75, fontVariantNumeric: 'tabular-nums' }}>
                    <PhoneIcon size={16} aria-hidden />
                    {g.phone}
                  </Stack>
                  <Stack direction="row" component="span" sx={{ alignItems: 'center', gap: 0.75 }}>
                    <TranslateIcon size={16} aria-hidden />
                    {t(`language.${g.language.toLowerCase()}`)}
                  </Stack>
                  <Pill tone={g.hasAccount ? 'positive' : 'neutral'}>{g.hasAccount ? t('roster.hasAccount') : t('roster.noAccount')}</Pill>
                </Stack>
              </Box>
              <Stack direction="row" spacing={0.5} sx={{ flex: 'none' }}>
                <Button size="small" aria-label={t('roster.editGuardianOf', { name: g.name })} onClick={() => setEditing(g)}>
                  {t('crud.edit')}
                </Button>
                {/* a student always keeps one guardian: the last one is replaced, never removed */}
                {student.guardians.length > 1 && (
                  <Button size="small" sx={{ color: 'text.secondary' }} aria-label={t('roster.removeGuardianOf', { name: g.name })} onClick={() => setRemoving(g)}>
                    {t('roster.remove')}
                  </Button>
                )}
              </Stack>
            </Stack>
          ))}
        </Box>
      </Paper>

      <StudentDetailsDialog
        open={open === 'details'}
        student={student}
        onClose={closeOpen}
        onSubmit={(request) => save(() => client.request(base, { method: 'PUT', body: request }), closeOpen)}
      />
      <ClassDialog
        open={open === 'class'}
        title={t('roster.changeClassTitle', { name: student.firstName })}
        text={t('roster.changeClassText', { from: student.className })}
        confirmLabel={t('roster.changeClass')}
        exclude={student.classId}
        onClose={closeOpen}
        onSubmit={(classId) => save(() => client.request(`${base}/class`, { method: 'PUT', body: { classId } }), closeOpen)}
      />
      <ClassDialog
        open={open === 'return'}
        title={t('roster.returnTitle', { name: student.firstName })}
        text={t('roster.returnText')}
        confirmLabel={t('roster.comeBack')}
        onClose={closeOpen}
        onSubmit={(classId) => save(() => client.request(`${base}/return`, { method: 'POST', body: { classId } }), closeOpen)}
      />
      <LeaveDialog
        open={open === 'leave'}
        name={student.firstName}
        today={today()}
        onClose={closeOpen}
        onSubmit={(date) => save(() => client.request(`${base}/leave`, { method: 'POST', body: { date } }), closeOpen)}
      />
      <AddGuardianDialog
        open={open === 'guardian'}
        onClose={closeOpen}
        onSubmit={(request) => save(() => client.request(`${base}/guardians`, { method: 'POST', body: request }), closeOpen)}
      />
      <EditGuardianDialog
        guardian={editing}
        onClose={() => setEditing(null)}
        onSubmit={(edit) => save(() => saveGuardian(editing!, edit), () => setEditing(null))}
      />
      <RemoveGuardianDialog
        guardian={removing}
        student={student}
        onClose={() => setRemoving(null)}
        onSubmit={(newPrimary) =>
          save(
            () => client.request(studentGuardianPath(student.id, removing!.id) + (newPrimary ? `?primary=${encodeURIComponent(newPrimary)}` : ''), { method: 'DELETE' }),
            () => setRemoving(null),
          )
        }
      />
    </Stack>
  )
}

function Fact({ label, value }: { label: string; value: string }) {
  return (
    <Box>
      <Typography component="dt" sx={{ fontSize: 12, color: tokens.muted }}>
        {label}
      </Typography>
      <Typography component="dd" sx={{ m: 0, fontSize: 14, fontWeight: 600, fontVariantNumeric: 'tabular-nums' }}>
        {value}
      </Typography>
    </Box>
  )
}
