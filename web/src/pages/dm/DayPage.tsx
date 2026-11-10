import {
  Alert,
  Box,
  Button,
  Checkbox,
  IconButton,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material'
import { Fragment, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useSearchParams } from 'react-router'
import { dayPath, type DayAbsence, type DayAttendance, type DayClass, type DayPeriod, type RecordState } from '../../api/day'
import { DM_PATHS, hm, isoDate, type JustificationRequest, type Reason } from '../../api/dm'
import { errorMessage } from '../../api/errors'
import { useAuth } from '../../auth/useAuth'
import { PageHeader } from '../../components/PageHeader'
import { Pill } from '../../components/Pill'
import { useResource } from '../../hooks/useResource'
import { CalendarCheckIcon, CaretRightIcon, ProhibitIcon } from '../../icons'
import { tokens } from '../../theme'
import { CancelRecordDialog, JustifyDialog } from './dialogs'

/** A record the DM acts on: an absence from one period or a lateness at the gate (BR-8, BR-9). */
interface Target {
  kind: 'absence' | 'lateness'
  recordId: string
  what: string
}

/** The school day before [date]: Friday before a Monday. Holidays answer "no lessons". */
function previousSchoolDay(date: string): string {
  const day = new Date(`${date}T12:00:00`)
  do day.setDate(day.getDate() - 1)
  while (day.getDay() === 0 || day.getDay() === 6)
  return isoDate(day)
}

/**
 * UC-D10, D40: "Présences du jour". One day of the DM's classes, period by period: recorded with
 * its absentees, not recorded (information only: the DM never records a roll call, BR-4), upcoming;
 * then the day's lateness of each class. The DM justifies several records at once (BR-12) or cancels
 * one (BR-13). Cancelled rows stay listed, struck through, never counted (BR-15). The date is in
 * the address (`?date=`), so a day can be linked to.
 */
export function DayPage() {
  const { t } = useTranslation()
  const { client } = useAuth()
  const [params, setParams] = useSearchParams()
  const date = params.get('date')
  const day = useResource<DayAttendance>(dayPath(date))
  const [selected, setSelected] = useState<Map<string, Target>>(new Map())
  const [justifying, setJustifying] = useState(false)
  const [cancelling, setCancelling] = useState<Target | null>(null)
  const [done, setDone] = useState<string | null>(null)
  // read once: the page does not follow midnight
  const [today] = useState(() => isoDate(new Date()))
  const shown = day.data?.date ?? date ?? today

  const goTo = (next: string | null) => {
    setSelected(new Map())
    setDone(null)
    setParams(next && next !== today ? { date: next } : {})
  }
  const toggle = (target: Target) =>
    setSelected((current) => {
      const next = new Map(current)
      if (next.has(target.recordId)) next.delete(target.recordId)
      else next.set(target.recordId, target)
      return next
    })

  const finish = async (message: string) => {
    setDone(message)
    setSelected(new Map())
    await day.reload()
  }

  const chosen = [...selected.values()]
  const justify = async (reason: Reason, note: string) => {
    const body: JustificationRequest = {
      reason,
      ...(note ? { note } : {}),
      absenceIds: chosen.filter((r) => r.kind === 'absence').map((r) => r.recordId),
      latenessIds: chosen.filter((r) => r.kind === 'lateness').map((r) => r.recordId),
    }
    await client.request(DM_PATHS.justifications, { method: 'POST', body })
    setJustifying(false)
    await finish(t('dm.justified', { count: chosen.length }))
  }

  const cancel = async (reason: string) => {
    if (!cancelling) return
    const path =
      cancelling.kind === 'absence' ? DM_PATHS.cancelAbsence(cancelling.recordId) : DM_PATHS.cancelLateness(cancelling.recordId)
    await client.request(path, { method: 'POST', body: { reason } })
    setCancelling(null)
    await finish(t('dm.cancelled'))
  }

  const data = day.data
  return (
    <Stack>
      <PageHeader
        icon={CalendarCheckIcon}
        title={t('day.title')}
        description={t('day.intro')}
        actions={
          <Stack direction="row" spacing={1} sx={{ alignItems: 'center', flexWrap: 'wrap', rowGap: 1 }}>
            <TextField
              id="day-date"
              type="date"
              size="small"
              label={t('day.date')}
              value={shown}
              onChange={(e) => e.target.value && goTo(e.target.value)}
              slotProps={{ htmlInput: { max: today }, inputLabel: { shrink: true } }}
            />
            <Button variant="outlined" onClick={() => goTo(previousSchoolDay(shown))}>
              {t('day.previous')}
            </Button>
            <Button variant="outlined" disabled={shown === today} onClick={() => goTo(null)}>
              {t('day.today')}
            </Button>
          </Stack>
        }
      />
      <Stack spacing={2}>
        {done && (
          <Alert severity="success" onClose={() => setDone(null)}>
            {done}
          </Alert>
        )}
        {day.error !== null && <Alert severity="error">{errorMessage(day.error, t)}</Alert>}
        {data && !data.schoolDay && <Empty text={t('day.noLessons')} />}
        {data?.schoolDay && (
          <>
            <Box component="dl" sx={{ m: 0, display: 'grid', gap: 1.5, gridTemplateColumns: { xs: '1fr', sm: 'repeat(3, 1fr)' } }}>
              <Figure label={t('day.totals.notRecorded', { count: data.totals.notRecorded })} value={data.totals.notRecorded} />
              <Figure label={t('day.totals.absentStudents', { count: data.totals.absentStudents })} value={data.totals.absentStudents} />
              <Figure label={t('day.totals.lateness', { count: data.totals.lateness })} value={data.totals.lateness} />
            </Box>
            <Stack direction="row" sx={{ alignItems: 'center', justifyContent: 'space-between', gap: 2, minHeight: 40 }}>
              <Typography color="text.secondary" aria-live="polite">
                {chosen.length > 0 ? t('dm.selected', { count: chosen.length }) : t('day.selectHint')}
              </Typography>
              <Button variant="contained" disabled={chosen.length === 0} onClick={() => setJustifying(true)}>
                {t('day.justifySelected', { count: chosen.length })}
              </Button>
            </Stack>
            {data.classes.length === 0 && <Empty text={t('day.noClasses')} />}
            {data.classes.map((schoolClass) => (
              <ClassSection
                key={schoolClass.classId}
                schoolClass={schoolClass}
                selected={selected}
                onToggle={toggle}
                onCancel={setCancelling}
              />
            ))}
          </>
        )}
      </Stack>
      <JustifyDialog count={justifying ? chosen.length : 0} onSubmit={justify} onClose={() => setJustifying(false)} />
      <CancelRecordDialog what={cancelling?.what ?? null} onSubmit={cancel} onClose={() => setCancelling(null)} />
    </Stack>
  )
}

function ClassSection({
  schoolClass,
  selected,
  onToggle,
  onCancel,
}: {
  schoolClass: DayClass
  selected: Map<string, Target>
  onToggle: (target: Target) => void
  onCancel: (target: Target) => void
}) {
  const { t } = useTranslation()
  const [open, setOpen] = useState<Set<number>>(new Set())
  const flip = (number: number) =>
    setOpen((current) => {
      const next = new Set(current)
      if (next.has(number)) next.delete(number)
      else next.add(number)
      return next
    })
  return (
    <Paper variant="outlined" component="section" aria-labelledby={`class-${schoolClass.classId}`} sx={{ overflow: 'hidden' }}>
      <Typography id={`class-${schoolClass.classId}`} component="h2" variant="h6" sx={{ px: 2, pt: 1.5, pb: 1 }}>
        {schoolClass.className}
      </Typography>
      <TableContainer>
        <Table size="small" sx={{ minWidth: 640 }}>
          <TableHead>
            <TableRow>
              <TableCell>{t('day.period')}</TableCell>
              <TableCell>{t('day.time')}</TableCell>
              <TableCell>{t('day.subject')}</TableCell>
              <TableCell>{t('day.teacher')}</TableCell>
              <TableCell>{t('day.status')}</TableCell>
              <TableCell align="right">{t('day.absent')}</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {schoolClass.periods.map((period) => {
              const expanded = open.has(period.number)
              return (
                <Fragment key={period.periodId}>
                  <TableRow>
                    <TableCell sx={{ whiteSpace: 'nowrap' }}>
                      {period.status === 'RECORDED' ? (
                        <Button
                          size="small"
                          aria-expanded={expanded}
                          aria-label={t('day.absentees', { number: period.number })}
                          onClick={() => flip(period.number)}
                          startIcon={
                            <Box component="span" sx={{ display: 'inline-flex', transform: expanded ? 'rotate(90deg)' : 'none', transition: 'transform 150ms' }}>
                              <CaretRightIcon size={14} aria-hidden />
                            </Box>
                          }
                          sx={{ minWidth: 0, fontWeight: 700 }}
                        >
                          {t('day.periodShort', { number: period.number })}
                        </Button>
                      ) : (
                        <Typography component="span" sx={{ pl: 1, fontWeight: 700 }}>
                          {t('day.periodShort', { number: period.number })}
                        </Typography>
                      )}
                    </TableCell>
                    <TableCell sx={{ fontVariantNumeric: 'tabular-nums', whiteSpace: 'nowrap' }}>
                      {hm(period.startTime)}–{hm(period.endTime)}
                    </TableCell>
                    <TableCell>{period.subject ?? '—'}</TableCell>
                    <TableCell>{period.teacherName ?? '—'}</TableCell>
                    <TableCell>
                      <StatusPill period={period} />
                    </TableCell>
                    <TableCell align="right" sx={{ fontVariantNumeric: 'tabular-nums' }}>
                      {period.status === 'RECORDED' ? period.absentCount : '—'}
                    </TableCell>
                  </TableRow>
                  {expanded && (
                    <TableRow>
                      <TableCell colSpan={6} sx={{ bgcolor: tokens.raised, py: 1 }}>
                        {period.absences.length === 0 ? (
                          <Typography color="text.secondary" sx={{ py: 1 }}>
                            {t('day.nobodyAbsent')}
                          </Typography>
                        ) : (
                          <RecordTable
                            rows={period.absences.map((a) => ({
                              record: a,
                              target: {
                                kind: 'absence',
                                recordId: a.recordId,
                                what: t('day.absenceOf', { name: fullName(a), number: period.number }),
                              },
                              detail: a.matricule,
                            }))}
                            lateness={false}
                            selected={selected}
                            onToggle={onToggle}
                            onCancel={onCancel}
                          />
                        )}
                      </TableCell>
                    </TableRow>
                  )}
                </Fragment>
              )
            })}
          </TableBody>
        </Table>
      </TableContainer>
      {schoolClass.lateness.length > 0 && (
        <Box sx={{ px: 2, pb: 1.5 }}>
          <Typography component="h3" variant="subtitle2" sx={{ pt: 2, pb: 0.5 }}>
            {t('day.lateness')}
          </Typography>
          <RecordTable
            rows={schoolClass.lateness.map((l) => ({
              record: l,
              target: {
                kind: 'lateness',
                recordId: l.recordId,
                what: t('day.latenessOf', { name: fullName(l), time: hm(l.arrivalTime) }),
              },
              detail: t('dm.lateAt', { time: hm(l.arrivalTime) }),
            }))}
            lateness
            selected={selected}
            onToggle={onToggle}
            onCancel={onCancel}
          />
        </Box>
      )}
    </Paper>
  )
}

const fullName = (r: DayAbsence) => `${r.firstName} ${r.lastName}`

/** Unjustified rows can be ticked; cancelled ones are struck through, with no checkbox or action. */
function RecordTable({
  rows,
  lateness,
  selected,
  onToggle,
  onCancel,
}: {
  rows: { record: DayAbsence; target: Target; detail: string }[]
  lateness: boolean
  selected: Map<string, Target>
  onToggle: (target: Target) => void
  onCancel: (target: Target) => void
}) {
  const { t } = useTranslation()
  return (
    <Table size="small" aria-label={lateness ? t('day.lateness') : t('day.absenteesTable')}>
      <TableBody>
        {rows.map(({ record, target, detail }) => {
          const cancelled = record.state === 'CANCELLED'
          const name = fullName(record)
          return (
            <TableRow key={record.recordId} selected={selected.has(record.recordId)}>
              <TableCell padding="checkbox">
                {record.state === 'UNJUSTIFIED' && (
                  <Checkbox
                    checked={selected.has(record.recordId)}
                    onChange={() => onToggle(target)}
                    slotProps={{ input: { 'aria-label': name } }}
                  />
                )}
              </TableCell>
              <TableCell sx={cancelled ? { color: tokens.muted } : undefined}>
                <Typography
                  component="span"
                  sx={{ display: 'block', fontWeight: 600, textDecoration: cancelled ? 'line-through' : 'none', color: 'inherit' }}
                >
                  {name}
                </Typography>
                <Typography component="span" variant="body2" sx={{ color: tokens.muted, fontVariantNumeric: 'tabular-nums' }}>
                  {detail}
                </Typography>
              </TableCell>
              <TableCell>
                <StatePill state={record.state} lateness={lateness} />
              </TableCell>
              <TableCell align="right" sx={{ width: 56 }}>
                {!cancelled && (
                  <Tooltip title={t('dm.cancelRecord')}>
                    <IconButton size="small" aria-label={t('dm.cancelRecordOf', { what: target.what })} onClick={() => onCancel(target)}>
                      <ProhibitIcon size={18} aria-hidden />
                    </IconButton>
                  </Tooltip>
                )}
              </TableCell>
            </TableRow>
          )
        })}
      </TableBody>
    </Table>
  )
}

function StatusPill({ period }: { period: DayPeriod }) {
  const { t } = useTranslation()
  switch (period.status) {
    case 'RECORDED':
      return <Pill tone="brand">{t('day.status_RECORDED')}</Pill>
    case 'NOT_RECORDED':
      return <Pill tone="warning">{t('day.status_NOT_RECORDED')}</Pill>
    default:
      return <Pill tone="neutral">{t('day.status_UPCOMING')}</Pill>
  }
}

function StatePill({ state, lateness }: { state: RecordState; lateness: boolean }) {
  const { t } = useTranslation()
  const key = lateness ? 'lateState' : 'state'
  const tone = state === 'JUSTIFIED' ? 'positive' : state === 'CANCELLED' ? 'neutral' : 'negative'
  return <Pill tone={tone}>{t(`day.${key}_${state}`)}</Pill>
}

function Figure({ label, value }: { label: string; value: number }) {
  return (
    // the number reads first on screen; the term stays first in the markup (dt before dd)
    <Paper variant="outlined" sx={{ px: 2, py: 1.5, display: 'flex', flexDirection: 'column-reverse' }}>
      <Typography component="dt" variant="body2" sx={{ color: tokens.muted }}>
        {label}
      </Typography>
      <Typography component="dd" sx={{ m: 0, fontSize: 24, fontWeight: 700, fontVariantNumeric: 'tabular-nums' }}>
        {value}
      </Typography>
    </Paper>
  )
}

function Empty({ text }: { text: string }) {
  return (
    <Paper variant="outlined" sx={{ px: 2, py: 5, textAlign: 'center' }}>
      <Typography color="text.secondary">{text}</Typography>
    </Paper>
  )
}
