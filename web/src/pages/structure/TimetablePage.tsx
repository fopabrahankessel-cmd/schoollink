import {
  Alert,
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Paper,
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
import { useMemo, useState, type FormEvent } from 'react'
import { PageHeader } from '../../components/PageHeader'
import { TableIcon } from '../../icons'
import { hover, tintFor, tokens } from '../../theme'
import { useTranslation } from 'react-i18next'
import { ApiError } from '../../api/client'
import { errorMessage } from '../../api/errors'
import {
  PATHS,
  query,
  shortTime,
  WEEKDAYS,
  type AcademicYear,
  type Period,
  type SchoolClass,
  type Staff,
  type TimetableSlot,
} from '../../api/structure'
import { useAuth } from '../../auth/useAuth'
import { YearPicker } from '../../components/YearPicker'
import { useList } from '../../hooks/useList'

interface Lesson {
  teacherId: string
  subject: string
  label: string
}

interface Cell {
  weekday: number
  period: Period
  slot: TimetableSlot | undefined
}

const lessonKey = (teacherId: string, subject: string) => `${teacherId}|${subject}`

/**
 * UC-A1, D1: a class's weekly timetable, Monday to Friday (D3) by lesson period. A lesson can
 * only be given by a teacher assigned to the class for that subject (BR-1); the server also
 * refuses a teacher who is busy in another class at that time.
 */
export function TimetablePage() {
  const { t } = useTranslation()
  const { client } = useAuth()
  const years = useList<AcademicYear>(PATHS.years)
  const periods = useList<Period>(PATHS.periods)
  const staff = useList<Staff>(PATHS.staff)
  const [yearId, setYearId] = useState('')
  const [classId, setClassId] = useState('')
  const classes = useList<SchoolClass>(yearId ? PATHS.classes + query({ academicYearId: yearId }) : null)
  const slots = useList<TimetableSlot>(classId ? PATHS.timetable + query({ classId }) : null)
  const [cell, setCell] = useState<Cell | null>(null)
  const [choice, setChoice] = useState('')
  const [error, setError] = useState<unknown>(null)
  const [saving, setSaving] = useState(false)

  const lessons = useMemo<Lesson[]>(
    () =>
      staff.items.flatMap((s) =>
        (s.teaching ?? [])
          .filter((a) => a.classId === classId)
          .map((a) => ({ teacherId: s.id, subject: a.subject, label: `${a.subject} · ${s.firstName} ${s.lastName}` })),
      ),
    [staff.items, classId],
  )
  const teacherName = (id: string) => {
    const s = staff.items.find((x) => x.id === id)
    return s ? `${s.firstName} ${s.lastName}` : ''
  }

  function open(next: Cell) {
    setCell(next)
    setChoice(next.slot ? lessonKey(next.slot.teacherId, next.slot.subject) : '')
    setError(null)
  }

  async function run(action: () => Promise<unknown>) {
    setSaving(true)
    setError(null)
    try {
      await action()
      setCell(null)
      await slots.reload()
    } catch (e) {
      setError(e)
    } finally {
      setSaving(false)
    }
  }

  function save(event: FormEvent) {
    event.preventDefault()
    if (!cell) return
    const lesson = lessons.find((l) => lessonKey(l.teacherId, l.subject) === choice)
    if (!lesson) return
    const body = {
      classId,
      weekday: cell.weekday,
      periodId: cell.period.id,
      teacherId: lesson.teacherId,
      subject: lesson.subject,
    }
    void run(() =>
      cell.slot
        ? client.request(`${PATHS.timetable}/${cell.slot.id}`, { method: 'PUT', body })
        : client.request(PATHS.timetable, { method: 'POST', body }),
    )
  }

  const loadError = years.error ?? periods.error ?? staff.error ?? classes.error ?? slots.error
  const fieldError = error instanceof ApiError ? error.messageFor('teacherId') : undefined

  return (
    <Stack>
      <PageHeader icon={TableIcon} title={t('timetable.title')} description={t('timetable.intro')} />
      <Stack spacing={3}>
      {loadError !== null && <Alert severity="error">{errorMessage(loadError, t)}</Alert>}
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
        <YearPicker
          years={years.items}
          value={yearId}
          onChange={(id) => {
            setYearId(id)
            setClassId('')
          }}
        />
        <TextField
          select
          label={t('timetable.class')}
          value={classId}
          onChange={(e) => setClassId(e.target.value)}
          margin="none"
          sx={{ maxWidth: 260 }}
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
      </Stack>

      {!classId ? (
        <Notice>{t('timetable.pickClass')}</Notice>
      ) : periods.items.length === 0 && !periods.loading ? (
        <Notice>{t('timetable.needPeriods')}</Notice>
      ) : (
        <>
          {!staff.loading && lessons.length === 0 && <Alert severity="info">{t('timetable.noTeaching')}</Alert>}
          <TableContainer component={Paper} variant="outlined">
            <Table size="small" aria-label={t('timetable.title')}>
              <TableHead>
                <TableRow>
                  <TableCell sx={{ width: 120, ...pinned, bgcolor: tokens.raised }} />
                  {WEEKDAYS.map((d) => (
                    <TableCell key={d} sx={{ minWidth: 132 }}>
                      {t(`weekdays.${d}`)}
                    </TableCell>
                  ))}
                </TableRow>
              </TableHead>
              <TableBody>
                {periods.items.map((period) => (
                  <TableRow key={period.id}>
                    <TableCell component="th" scope="row" sx={{ whiteSpace: 'nowrap', verticalAlign: 'top', ...pinned }}>
                      <Box sx={{ fontWeight: 700 }}>{t('timetable.periodNumber', { number: period.number })}</Box>
                      <Box sx={{ fontSize: 12, color: 'text.secondary' }}>
                        {shortTime(period.startTime)}–{shortTime(period.endTime)}
                      </Box>
                    </TableCell>
                    {WEEKDAYS.map((weekday) => {
                      const slot = slots.items.find((s) => s.weekday === weekday && s.periodId === period.id)
                      const title = t('timetable.cellTitle', { day: t(`weekdays.${weekday}`), number: period.number })
                      const tint = slot ? tintFor(slot.subject.toLowerCase()) : null
                      return (
                        <TableCell key={weekday} sx={{ p: 0.5 }}>
                          <Button
                            fullWidth
                            size="small"
                            color="inherit"
                            aria-label={t('timetable.cellLabel', { cell: title, content: slot ? `${slot.subject}, ${teacherName(slot.teacherId)}` : t('timetable.free') })}
                            onClick={() => open({ weekday, period, slot })}
                            sx={{
                              justifyContent: 'flex-start',
                              textAlign: 'left',
                              minHeight: 52,
                              borderRadius: `${tokens.radius.control}px`,
                              ...(tint
                                ? {
                                    bgcolor: tint.bg,
                                    color: tint.ink,
                                    boxShadow: `inset 3px 0 0 ${tint.ink}`,
                                    [hover]: { '&:hover': { bgcolor: tint.bg, filter: 'brightness(0.97)' } },
                                  }
                                : {
                                    border: `1px dashed ${tokens.border}`,
                                    color: tokens.muted,
                                    [hover]: { '&:hover': { borderColor: tokens.brand, bgcolor: tokens.brandTint, color: tokens.brandDark } },
                                  }),
                            }}
                          >
                            {slot ? (
                              <Box sx={{ minWidth: 0 }}>
                                <Box sx={{ fontWeight: 700, lineHeight: 1.3 }}>{slot.subject}</Box>
                                <Box sx={{ fontSize: 12, fontWeight: 500, opacity: 0.85 }}>{teacherName(slot.teacherId)}</Box>
                              </Box>
                            ) : (
                              <Box sx={{ fontWeight: 500 }}>{t('timetable.free')}</Box>
                            )}
                          </Button>
                        </TableCell>
                      )
                    })}
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        </>
      )}
      </Stack>

      <Dialog open={cell !== null} onClose={() => setCell(null)} fullWidth maxWidth="xs">
        <Box component="form" onSubmit={save} noValidate>
          <DialogTitle>
            {cell && t('timetable.cellTitle', { day: t(`weekdays.${cell.weekday}`), number: cell.period.number })}
          </DialogTitle>
          <DialogContent>
            {error !== null && !fieldError && (
              <Alert severity="error" sx={{ mt: 1 }}>
                {errorMessage(error, t)}
              </Alert>
            )}
            <TextField
              select
              label={t('timetable.lesson')}
              value={choice}
              onChange={(e) => setChoice(e.target.value)}
              error={Boolean(fieldError)}
              helperText={fieldError ?? t('timetable.lessonHint')}
              slotProps={{ select: { native: true }, inputLabel: { shrink: true } }}
            >
              <option value="" disabled>
                {t('crud.choose')}
              </option>
              {lessons.map((l) => (
                <option key={lessonKey(l.teacherId, l.subject)} value={lessonKey(l.teacherId, l.subject)}>
                  {l.label}
                </option>
              ))}
            </TextField>
          </DialogContent>
          <DialogActions>
            {cell?.slot && (
              <Button
                color="error"
                sx={{ mr: 'auto' }}
                disabled={saving}
                onClick={() => {
                  const id = cell.slot?.id
                  if (id) void run(() => client.request(`${PATHS.timetable}/${id}`, { method: 'DELETE' }))
                }}
              >
                {t('timetable.clear')}
              </Button>
            )}
            <Button onClick={() => setCell(null)}>{t('crud.cancel')}</Button>
            <Button type="submit" variant="contained" loading={saving} disabled={!choice}>
              {t('crud.save')}
            </Button>
          </DialogActions>
        </Box>
      </Dialog>
    </Stack>
  )
}

// The period column stays in view while a narrow screen scrolls the week sideways.
const pinned = { position: 'sticky', left: 0, zIndex: 1, bgcolor: tokens.surface, boxShadow: `inset -1px 0 0 ${tokens.rule}` } as const

/** A centred instruction in place of the grid. */
function Notice({ children }: { children: string }) {
  return (
    <Typography
      color="text.secondary"
      sx={{ border: `1px dashed ${tokens.control}`, borderRadius: `${tokens.radius.card}px`, bgcolor: tokens.raised, px: 2, py: 4, textAlign: 'center' }}
    >
      {children}
    </Typography>
  )
}
