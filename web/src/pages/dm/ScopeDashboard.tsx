import {
  Alert,
  Box,
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
import { useState, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { DM_PATHS, isoDate, type Counts, type ScopeDashboard as Dashboard } from '../../api/dm'
import { errorMessage } from '../../api/errors'
import { PageHeader } from '../../components/PageHeader'
import { formatDate } from '../../format'
import { useResource } from '../../hooks/useResource'
import { SquaresFourIcon } from '../../icons'
import { tokens } from '../../theme'

/** BR-15: cancelled records are already left out of the counts by the server. */
const absenceRate = (c: Counts) => (c.studentPeriods === 0 ? null : c.absentPeriods / c.studentPeriods)

function lastDays(days: number, today = new Date()) {
  const from = new Date(today)
  from.setDate(from.getDate() - (days - 1))
  return { from: isoDate(from), to: isoDate(today) }
}

/**
 * UC-D8, D34: the DM's home. Absences and lateness in their own classes over a range (the last 30
 * days by default), day by day and class by class, and the alerts each rule raised. Absences and
 * lateness are counted apart and never merged (BR-8, BR-9).
 */
export function ScopeDashboard() {
  const { t, i18n } = useTranslation()
  const [range, setRange] = useState(() => lastDays(30))
  const valid = range.from !== '' && range.to !== '' && range.from <= range.to
  const dashboard = useResource<Dashboard>(valid ? DM_PATHS.dashboard(range.from, range.to) : null)
  const percent = (rate: number | null) =>
    rate === null ? '–' : new Intl.NumberFormat(i18n.language, { style: 'percent', maximumFractionDigits: 1 }).format(rate)
  const data = dashboard.data

  return (
    <Stack>
      <PageHeader icon={SquaresFourIcon} title={t('dm.dashboardTitle')} description={t('dm.dashboardIntro')} />
      <Stack spacing={3}>
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
          <TextField
            type="date"
            label={t('dm.from')}
            value={range.from}
            onChange={(e) => setRange((r) => ({ ...r, from: e.target.value }))}
            slotProps={{ inputLabel: { shrink: true } }}
          />
          <TextField
            type="date"
            label={t('dm.to')}
            value={range.to}
            onChange={(e) => setRange((r) => ({ ...r, to: e.target.value }))}
            error={!valid}
            helperText={valid ? undefined : t('dm.rangeInvalid')}
            slotProps={{ inputLabel: { shrink: true } }}
          />
        </Stack>
        {dashboard.error !== null && <Alert severity="error">{errorMessage(dashboard.error, t)}</Alert>}
        {data && (
          <>
            <Box component="dl" sx={{ m: 0, display: 'grid', gap: 1.5, gridTemplateColumns: { xs: '1fr 1fr', md: 'repeat(4, 1fr)' } }}>
              <Figure label={t('dm.kpi.absenceRate')} value={percent(absenceRate(data.totals))} />
              <Figure label={t('dm.kpi.unjustifiedAbsences')} value={data.totals.unjustifiedAbsentPeriods} />
              <Figure label={t('dm.kpi.lateness')} value={data.totals.lateness} />
              <Figure
                label={t('dm.kpi.openAlerts')}
                value={data.alerts.reduce((sum, r) => sum + r.open, 0)}
                to="/alerts"
                linkLabel={t('dm.seeAlerts')}
              />
            </Box>

            <Section title={t('dm.byDay')}>
              {data.days.length === 0 ? (
                <Typography color="text.secondary">{t('dm.noActivity')}</Typography>
              ) : (
                <DayBars days={data.days} percent={percent} language={i18n.language} />
              )}
            </Section>

            <Section title={t('dm.byClass')}>
              <TableContainer component={Paper} variant="outlined">
                <Table size="small" sx={{ minWidth: 560 }}>
                  <TableHead>
                    <TableRow>
                      <TableCell>{t('dm.class')}</TableCell>
                      <TableCell align="right">{t('dm.kpi.absenceRate')}</TableCell>
                      <TableCell align="right">{t('dm.absentPeriods')}</TableCell>
                      <TableCell align="right">{t('dm.kpi.unjustifiedAbsences')}</TableCell>
                      <TableCell align="right">{t('dm.kpi.lateness')}</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {data.classes.map((c) => (
                      <TableRow key={c.classId}>
                        <TableCell>{c.name ?? ''}</TableCell>
                        <TableCell align="right">{percent(absenceRate(c.counts))}</TableCell>
                        <TableCell align="right">{c.counts.absentPeriods}</TableCell>
                        <TableCell align="right">{c.counts.unjustifiedAbsentPeriods}</TableCell>
                        <TableCell align="right">{c.counts.lateness}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            </Section>

            <Section title={t('dm.alertsByRule')}>
              <TableContainer component={Paper} variant="outlined">
                <Table size="small" sx={{ minWidth: 480 }}>
                  <TableHead>
                    <TableRow>
                      <TableCell>{t('dm.rule')}</TableCell>
                      <TableCell align="right">{t('dm.raisedInRange')}</TableCell>
                      <TableCell align="right">{t('dm.closedInRange')}</TableCell>
                      <TableCell align="right">{t('dm.openNow')}</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {data.alerts.map((r) => (
                      <TableRow key={r.rule}>
                        <TableCell>{t(`dm.rules.${r.rule}`)}</TableCell>
                        <TableCell align="right">{r.raised}</TableCell>
                        <TableCell align="right">{r.closed}</TableCell>
                        <TableCell align="right">{r.open}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            </Section>
          </>
        )}
      </Stack>
    </Stack>
  )
}

/** One bar a day for the absence rate; the number sits under each bar, so colour is never alone. */
function DayBars({
  days,
  percent,
  language,
}: {
  days: Dashboard['days']
  percent: (rate: number | null) => string
  language: string
}) {
  const { t } = useTranslation()
  const rates = days.map((d) => absenceRate(d.counts))
  const top = Math.max(0.05, ...rates.map((r) => r ?? 0))
  const short = new Intl.DateTimeFormat(language, { day: 'numeric', month: 'short' })
  return (
    <Paper variant="outlined" sx={{ p: 2, overflowX: 'auto' }}>
      <Box
        component="ul"
        aria-label={t('dm.byDay')}
        sx={{ listStyle: 'none', m: 0, p: 0, display: 'flex', alignItems: 'flex-end', gap: 1, minHeight: 180 }}
      >
        {days.map((day, i) => {
          const rate = rates[i]
          const label = t('dm.dayBar', {
            date: formatDate(day.date, language),
            rate: percent(rate),
            lateness: day.counts.lateness,
          })
          const [year, month, date] = day.date.split('-').map(Number)
          return (
            <Box
              component="li"
              key={day.date}
              title={label}
              aria-label={label}
              sx={{ flex: '1 0 36px', maxWidth: 56, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 0.5 }}
            >
              <Typography aria-hidden sx={{ fontSize: 11, fontVariantNumeric: 'tabular-nums', color: tokens.muted }}>
                {percent(rate)}
              </Typography>
              <Box
                aria-hidden
                sx={{
                  width: '100%',
                  height: `${Math.max(2, ((rate ?? 0) / top) * 120)}px`,
                  bgcolor: tokens.brand,
                  borderRadius: '4px 4px 0 0',
                }}
              />
              <Typography aria-hidden sx={{ fontSize: 11, color: tokens.muted, whiteSpace: 'nowrap' }}>
                {short.format(new Date(year, month - 1, date))}
              </Typography>
            </Box>
          )
        })}
      </Box>
    </Paper>
  )
}

function Figure({ label, value, to, linkLabel }: { label: string; value: number | string; to?: string; linkLabel?: string }) {
  return (
    <Paper variant="outlined" sx={{ px: 2, py: 1.5 }}>
      <Typography component="dt" variant="body2" sx={{ color: tokens.muted }}>
        {label}
      </Typography>
      <Typography component="dd" sx={{ m: 0, fontSize: 24, fontWeight: 700, fontVariantNumeric: 'tabular-nums' }}>
        {value}
      </Typography>
      {to && linkLabel && (
        <Typography component="dd" variant="body2" sx={{ m: 0 }}>
          <Link to={to}>{linkLabel}</Link>
        </Typography>
      )}
    </Paper>
  )
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <Box component="section">
      <Typography variant="h6" component="h2" sx={{ mb: 1 }}>
        {title}
      </Typography>
      {children}
    </Box>
  )
}
