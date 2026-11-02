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
  Tooltip,
  Typography,
} from '@mui/material'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useParams } from 'react-router'
import {
  DM_PATHS,
  hm,
  type JustificationRequest,
  type Meeting,
  type Reason,
  type StudentHistory,
  type SummonsRequest,
} from '../../api/dm'
import { errorMessage } from '../../api/errors'
import { useAuth } from '../../auth/useAuth'
import { PageHeader } from '../../components/PageHeader'
import { Pill } from '../../components/Pill'
import { formatDate } from '../../format'
import { useResource } from '../../hooks/useResource'
import { EnvelopeSimpleIcon, ProhibitIcon, StudentIcon } from '../../icons'
import { tokens } from '../../theme'
import { CancelRecordDialog, JustifyDialog, SummonDialog } from './dialogs'

/** One line of the history: an absence from one lesson, or one arrival late at the gate (BR-8, BR-9). */
interface Row {
  key: string
  kind: 'absence' | 'lateness'
  recordId: string
  date: string
  label: string
  justified: boolean
}

/**
 * UC-D8: one student's absences and lateness, newest first. The DM justifies several records at
 * once (UC-D1, BR-11, D26), cancels one entered by mistake (UC-D6, BR-13, D27), or summons the
 * parents (UC-D3, D31). Cancelled records are gone from the history (BR-15).
 */
export function StudentHistoryPage() {
  const { id = '' } = useParams()
  const { t, i18n } = useTranslation()
  const { client } = useAuth()
  const history = useResource<StudentHistory>(DM_PATHS.history(id))
  const [selected, setSelected] = useState<Set<string>>(new Set())
  const [justifying, setJustifying] = useState(false)
  const [cancelling, setCancelling] = useState<Row | null>(null)
  const [summoning, setSummoning] = useState(false)
  const [done, setDone] = useState<string | null>(null)

  const student = history.data
  const rows = student ? toRows(student, (key, values) => t(key, values)) : []
  const chosen = rows.filter((r) => selected.has(r.key))
  const toggle = (key: string) =>
    setSelected((current) => {
      const next = new Set(current)
      if (next.has(key)) next.delete(key)
      else next.add(key)
      return next
    })
  const open = rows.filter((r) => !r.justified)
  const allChosen = open.length > 0 && open.every((r) => selected.has(r.key))

  const finish = async (message: string) => {
    setDone(message)
    setSelected(new Set())
    await history.reload()
  }

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
    const path = cancelling.kind === 'absence' ? DM_PATHS.cancelAbsence(cancelling.recordId) : DM_PATHS.cancelLateness(cancelling.recordId)
    await client.request(path, { method: 'POST', body: { reason } })
    setCancelling(null)
    await finish(t('dm.cancelled'))
  }

  const summon = async (reason: string, meeting: Meeting | null) => {
    const body: SummonsRequest = { studentId: id, reason, ...(meeting ?? {}) }
    await client.request(DM_PATHS.summons, { method: 'POST', body })
    setSummoning(false)
    setDone(t('dm.summonsSent'))
  }

  const name = student ? `${student.lastName} ${student.firstName}` : ''

  return (
    <Stack>
      <PageHeader
        icon={StudentIcon}
        title={name || t('dm.historyTitle')}
        description={
          student
            ? t('dm.historyIntro', {
                className: student.className ?? '',
                matricule: student.matricule,
                from: formatDate(student.from, i18n.language),
                to: formatDate(student.to, i18n.language),
              })
            : undefined
        }
        back={{ to: '/students', label: t('dm.studentsTitle') }}
        actions={
          student && (
            <Button variant="outlined" startIcon={<EnvelopeSimpleIcon size={18} aria-hidden />} onClick={() => setSummoning(true)}>
              {t('dm.summonParents')}
            </Button>
          )
        }
      />
      <Stack spacing={2}>
        {done && (
          <Alert severity="success" onClose={() => setDone(null)}>
            {done}
          </Alert>
        )}
        {history.error !== null && <Alert severity="error">{errorMessage(history.error, t)}</Alert>}
        {student && (
          <>
            <Box
              component="dl"
              sx={{ m: 0, display: 'grid', gap: 1.5, gridTemplateColumns: { xs: '1fr 1fr', md: 'repeat(4, 1fr)' } }}
            >
              <Figure label={t('dm.totals.absentPeriods')} value={student.totals.absentPeriods} />
              <Figure label={t('dm.totals.unjustifiedAbsentPeriods')} value={student.totals.unjustifiedAbsentPeriods} />
              <Figure label={t('dm.totals.lateness')} value={student.totals.lateness} />
              <Figure label={t('dm.totals.unjustifiedLateness')} value={student.totals.unjustifiedLateness} />
            </Box>

            {rows.length === 0 ? (
              <Paper variant="outlined" sx={{ px: 2, py: 5, textAlign: 'center' }}>
                <Typography color="text.secondary">{t('dm.noRecords')}</Typography>
              </Paper>
            ) : (
              <Stack spacing={1}>
                <Stack direction="row" sx={{ alignItems: 'center', justifyContent: 'space-between', gap: 2, minHeight: 40 }}>
                  <Typography color="text.secondary" aria-live="polite">
                    {chosen.length > 0 ? t('dm.selected', { count: chosen.length }) : t('dm.selectHint')}
                  </Typography>
                  <Button variant="contained" disabled={chosen.length === 0} onClick={() => setJustifying(true)}>
                    {t('dm.justifySelected')}
                  </Button>
                </Stack>
                <TableContainer component={Paper} variant="outlined">
                  <Table size="small" sx={{ minWidth: 560 }}>
                    <TableHead>
                      <TableRow>
                        <TableCell padding="checkbox">
                          <Checkbox
                            checked={allChosen}
                            indeterminate={chosen.length > 0 && !allChosen}
                            disabled={open.length === 0}
                            onChange={() => setSelected(allChosen ? new Set() : new Set(open.map((r) => r.key)))}
                            slotProps={{ input: { 'aria-label': t('dm.selectAll') } }}
                          />
                        </TableCell>
                        <TableCell>{t('dm.date')}</TableCell>
                        <TableCell>{t('dm.record')}</TableCell>
                        <TableCell>{t('dm.status')}</TableCell>
                        <TableCell align="right">{t('crud.actions')}</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {rows.map((row) => (
                        <TableRow key={row.key} selected={selected.has(row.key)}>
                          <TableCell padding="checkbox">
                            {!row.justified && (
                              <Checkbox
                                checked={selected.has(row.key)}
                                onChange={() => toggle(row.key)}
                                slotProps={{
                                  input: { 'aria-label': `${formatDate(row.date, i18n.language)}, ${row.label}` },
                                }}
                              />
                            )}
                          </TableCell>
                          <TableCell sx={{ whiteSpace: 'nowrap' }}>{formatDate(row.date, i18n.language)}</TableCell>
                          <TableCell>{row.label}</TableCell>
                          <TableCell>
                            <Pill tone={row.justified ? 'positive' : 'warning'}>
                              {row.justified ? t('dm.justifiedStatus') : t('dm.unjustifiedStatus')}
                            </Pill>
                          </TableCell>
                          <TableCell align="right">
                            <Tooltip title={t('dm.cancelRecord')}>
                              <IconButton
                                size="small"
                                aria-label={t('dm.cancelRecordOf', { what: `${formatDate(row.date, i18n.language)}, ${row.label}` })}
                                onClick={() => setCancelling(row)}
                              >
                                <ProhibitIcon size={18} aria-hidden />
                              </IconButton>
                            </Tooltip>
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </TableContainer>
              </Stack>
            )}
          </>
        )}
      </Stack>
      <JustifyDialog count={justifying ? chosen.length : 0} onSubmit={justify} onClose={() => setJustifying(false)} />
      <CancelRecordDialog
        what={cancelling ? `${formatDate(cancelling.date, i18n.language)}, ${cancelling.label}` : null}
        onSubmit={cancel}
        onClose={() => setCancelling(null)}
      />
      <SummonDialog open={summoning} studentName={student?.firstName ?? ''} onSubmit={summon} onClose={() => setSummoning(false)} />
    </Stack>
  )
}

function Figure({ label, value }: { label: string; value: number }) {
  return (
    <Paper variant="outlined" sx={{ px: 2, py: 1.5 }}>
      <Typography component="dt" variant="body2" sx={{ color: tokens.muted }}>
        {label}
      </Typography>
      <Typography component="dd" sx={{ m: 0, fontSize: 24, fontWeight: 700, fontVariantNumeric: 'tabular-nums' }}>
        {value}
      </Typography>
    </Paper>
  )
}

function toRows(history: StudentHistory, t: (key: string, values: Record<string, unknown>) => string): Row[] {
  return history.days.flatMap((day) => [
    ...(day.lateness
      ? [
          {
            key: `l:${day.lateness.recordId}`,
            kind: 'lateness' as const,
            recordId: day.lateness.recordId,
            date: day.date,
            label: t('dm.lateAt', { time: hm(day.lateness.arrivalTime) }),
            justified: day.lateness.justified,
          },
        ]
      : []),
    ...day.absences.map((a) => ({
      key: `a:${a.recordId}`,
      kind: 'absence' as const,
      recordId: a.recordId,
      date: day.date,
      label: t('dm.absentFrom', { period: a.periodNumber, time: hm(a.startTime) }),
      justified: a.justified,
    })),
  ])
}
