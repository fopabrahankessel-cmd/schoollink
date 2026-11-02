import { Box, Paper, Stack, Table, TableBody, TableCell, TableHead, TableRow, Typography } from '@mui/material'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Area, AreaChart, Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import type { Counts } from '../../api/dm'
import { formatDate } from '../../format'
import { tokens } from '../../theme'
import { absenceRate, usePercent } from './figures'

type Day = { date: string; counts: Counts }

const HEIGHT = 220
const axisTick = { fill: tokens.muted, fontSize: 12 }

/**
 * Two charts, one measure each, never two scales on one axis: the share of lessons missed day by
 * day, and the late arrivals at the gate. Absences and lateness stay apart (BR-8, BR-9). Each has
 * a tooltip, and the same numbers are in the table under them, so nothing depends on hovering.
 */
export function TrendCharts({ days }: { days: Day[] }) {
  const { t, i18n } = useTranslation()
  const percent = usePercent()
  const short = new Intl.DateTimeFormat(i18n.language, { day: 'numeric', month: 'short' })
  const dayLabel = (iso: string) => {
    const [year, month, date] = iso.split('-').map(Number)
    return short.format(new Date(year, month - 1, date))
  }
  // The share is plotted in percent units, on round steps (0, 0.5, 1 %…) worked out here.
  const data = days.map((d) => {
    const rate = absenceRate(d.counts)
    return { date: d.date, rate, points: rate === null ? null : rate * 100, lateness: d.counts.lateness }
  })
  const rateTicks = roundTicks(Math.max(0, ...data.map((d) => d.points ?? 0)))

  return (
    <Stack spacing={2}>
      <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', lg: '1fr 1fr' } }}>
        <ChartCard title={t('dashboard.absenceTrend')}>
          <ResponsiveContainer width="100%" height={HEIGHT}>
            <AreaChart data={data} margin={{ top: 8, right: 12, bottom: 0, left: 0 }}>
              <CartesianGrid vertical={false} stroke={tokens.rule} />
              <XAxis dataKey="date" tickFormatter={dayLabel} tick={axisTick} tickLine={false} axisLine={{ stroke: tokens.border }} minTickGap={16} />
              <YAxis
                domain={[0, rateTicks[rateTicks.length - 1]]}
                ticks={rateTicks}
                tickFormatter={(v: number) => percent(v / 100)}
                tick={axisTick}
                tickLine={false}
                axisLine={false}
                width={52}
              />
              <Tooltip
                cursor={{ stroke: tokens.control, strokeWidth: 1 }}
                content={({ active, payload }) =>
                  active && payload?.length ? (
                    <Readout value={percent(payload[0].payload.rate)} label={formatDate(payload[0].payload.date, i18n.language)} />
                  ) : null
                }
              />
              <Area
                type="linear"
                dataKey="points"
                stroke={tokens.brand}
                strokeWidth={2}
                fill={tokens.brand}
                fillOpacity={0.1}
                dot={false}
                activeDot={{ r: 4, fill: tokens.brand, stroke: tokens.surface, strokeWidth: 2 }}
                connectNulls
                isAnimationActive={false}
              />
            </AreaChart>
          </ResponsiveContainer>
        </ChartCard>
        <ChartCard title={t('dashboard.latenessTrend')}>
          <ResponsiveContainer width="100%" height={HEIGHT}>
            <BarChart data={data} margin={{ top: 8, right: 12, bottom: 0, left: 0 }}>
              <CartesianGrid vertical={false} stroke={tokens.rule} />
              <XAxis dataKey="date" tickFormatter={dayLabel} tick={axisTick} tickLine={false} axisLine={{ stroke: tokens.border }} minTickGap={16} />
              <YAxis tick={axisTick} tickLine={false} axisLine={false} width={52} allowDecimals={false} />
              <Tooltip
                cursor={{ fill: tokens.brandTint }}
                content={({ active, payload }) =>
                  active && payload?.length ? (
                    <Readout
                      value={t('dashboard.lateCount', { count: payload[0].payload.lateness })}
                      label={formatDate(payload[0].payload.date, i18n.language)}
                    />
                  ) : null
                }
              />
              <Bar dataKey="lateness" fill={tokens.brand} radius={[4, 4, 0, 0]} maxBarSize={24} isAnimationActive={false} />
            </BarChart>
          </ResponsiveContainer>
        </ChartCard>
      </Box>
      <Box component="details" sx={{ '& > summary': { cursor: 'pointer', color: tokens.brandDark, fontWeight: 600, fontSize: 14 } }}>
        <summary>{t('dashboard.showData')}</summary>
        <Paper variant="outlined" sx={{ mt: 1, overflowX: 'auto' }}>
          <Table size="small" aria-label={t('dashboard.dataTable')}>
            <TableHead>
              <TableRow>
                <TableCell>{t('dm.date')}</TableCell>
                <TableCell align="right">{t('dm.kpi.absenceRate')}</TableCell>
                <TableCell align="right">{t('dm.absentPeriods')}</TableCell>
                <TableCell align="right">{t('dm.kpi.lateness')}</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {days.map((d) => (
                <TableRow key={d.date}>
                  <TableCell>{formatDate(d.date, i18n.language)}</TableCell>
                  <TableCell align="right">{percent(absenceRate(d.counts))}</TableCell>
                  <TableCell align="right">{d.counts.absentPeriods}</TableCell>
                  <TableCell align="right">{d.counts.lateness}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Paper>
      </Box>
    </Stack>
  )
}

function ChartCard({ title, children }: { title: string; children: ReactNode }) {
  return (
    <Paper variant="outlined" sx={{ p: 2, minWidth: 0 }}>
      <Typography variant="subtitle2" component="h3" sx={{ mb: 1 }}>
        {title}
      </Typography>
      <Box aria-hidden>{children}</Box>
    </Paper>
  )
}

/** The value leads, the day follows. */
function Readout({ value, label }: { value: string; label: string }) {
  return (
    <Paper sx={{ px: 1.5, py: 1, boxShadow: tokens.shadow.raised, border: `1px solid ${tokens.border}` }}>
      <Typography sx={{ fontWeight: 700, fontSize: 14, fontVariantNumeric: 'tabular-nums' }}>{value}</Typography>
      <Typography variant="caption" sx={{ color: tokens.muted }}>
        {label}
      </Typography>
    </Paper>
  )
}

/** Ticks from 0 past the top value, at most five steps of 1, 2 or 5 times a power of ten. */
function roundTicks(top: number): number[] {
  const goal = top > 0 ? top : 1
  const power = 10 ** Math.floor(Math.log10(goal / 5))
  const step = [1, 2, 5, 10].map((m) => m * power).find((candidate) => goal / candidate <= 5) ?? 10 * power
  const count = Math.ceil(goal / step - 1e-9)
  return Array.from({ length: count + 1 }, (_, i) => Number((i * step).toFixed(6)))
}
