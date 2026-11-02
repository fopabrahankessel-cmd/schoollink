import {
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
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import type { Counts, RuleCount } from '../../api/dm'
import { tokens } from '../../theme'
import { absenceRate, usePercent, type Range } from './figures'

// The pieces both dashboards are made of: the school's (UC-A6) and the DM's (UC-D8, D34).

export function RangeFields({ range, valid, onChange }: { range: Range; valid: boolean; onChange: (range: Range) => void }) {
  const { t } = useTranslation()
  return (
    <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
      <TextField
        type="date"
        label={t('dm.from')}
        value={range.from}
        onChange={(e) => onChange({ ...range, from: e.target.value })}
        slotProps={{ inputLabel: { shrink: true } }}
      />
      <TextField
        type="date"
        label={t('dm.to')}
        value={range.to}
        onChange={(e) => onChange({ ...range, to: e.target.value })}
        error={!valid}
        helperText={valid ? undefined : t('dm.rangeInvalid')}
        slotProps={{ inputLabel: { shrink: true } }}
      />
    </Stack>
  )
}

/** A row of headline figures, as a description list. */
export function Figures({ children }: { children: ReactNode }) {
  return (
    <Box component="dl" sx={{ m: 0, display: 'grid', gap: 1.5, gridTemplateColumns: { xs: '1fr 1fr', md: 'repeat(4, 1fr)' } }}>
      {children}
    </Box>
  )
}

export function Figure({
  label,
  value,
  detail,
  to,
  linkLabel,
}: {
  label: string
  value: number | string
  detail?: ReactNode
  to?: string
  linkLabel?: string
}) {
  return (
    <Paper variant="outlined" sx={{ px: 2, py: 1.5 }}>
      <Typography component="dt" variant="body2" sx={{ color: tokens.muted }}>
        {label}
      </Typography>
      <Typography component="dd" sx={{ m: 0, fontSize: 24, fontWeight: 700, fontVariantNumeric: 'tabular-nums' }}>
        {value}
      </Typography>
      {detail && (
        <Typography component="dd" variant="body2" sx={{ m: 0, color: tokens.muted }}>
          {detail}
        </Typography>
      )}
      {to && linkLabel && (
        <Typography component="dd" variant="body2" sx={{ m: 0 }}>
          <Link to={to}>{linkLabel}</Link>
        </Typography>
      )}
    </Paper>
  )
}

export function Section({ title, intro, children }: { title: string; intro?: string; children: ReactNode }) {
  return (
    <Box component="section">
      <Typography variant="h6" component="h2" sx={{ mb: intro ? 0 : 1 }}>
        {title}
      </Typography>
      {intro && (
        <Typography color="text.secondary" sx={{ mb: 1.5 }}>
          {intro}
        </Typography>
      )}
      {children}
    </Box>
  )
}

/** Every class of the scope, even one without any record. */
export function ClassTable({ classes }: { classes: { classId: string; name: string | null; counts: Counts }[] }) {
  const { t } = useTranslation()
  const percent = usePercent()
  return (
    <TableContainer component={Paper} variant="outlined">
      <Table size="small" sx={{ minWidth: 560 }} aria-label={t('dm.byClass')}>
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
          {classes.map((c) => (
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
  )
}

/** One row per rule: raised and closed in the range, open now (D34). */
export function RuleTable({ alerts }: { alerts: RuleCount[] }) {
  const { t } = useTranslation()
  return (
    <TableContainer component={Paper} variant="outlined">
      <Table size="small" sx={{ minWidth: 480 }} aria-label={t('dm.alertsByRule')}>
        <TableHead>
          <TableRow>
            <TableCell>{t('dm.rule')}</TableCell>
            <TableCell align="right">{t('dm.raisedInRange')}</TableCell>
            <TableCell align="right">{t('dm.closedInRange')}</TableCell>
            <TableCell align="right">{t('dm.openNow')}</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {alerts.map((r) => (
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
  )
}
