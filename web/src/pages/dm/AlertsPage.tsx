import {
  Alert,
  Button,
  Menu,
  MenuItem,
  Paper,
  Stack,
  Tab,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Tabs,
  Typography,
} from '@mui/material'
import type { TFunction } from 'i18next'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { DM_PATHS, type Decision, type DecisionRequest, type Meeting, type RiskAlert } from '../../api/dm'
import { errorMessage } from '../../api/errors'
import { useAuth } from '../../auth/useAuth'
import { PageHeader } from '../../components/PageHeader'
import { Pill } from '../../components/Pill'
import { formatDate, formatInstant } from '../../format'
import { useList } from '../../hooks/useList'
import { WarningIcon } from '../../icons'
import { tokens } from '../../theme'
import { DecisionDialog } from './dialogs'

type Status = 'open' | 'closed'

/**
 * UC-D2: the risk alerts of the DM's classes. Open ones wait for a decision, the longest waiting
 * first (D30); decided ones keep who decided what and why (BR-18). Rules only raise alerts; every
 * decision is the DM's (BR-16 to BR-19).
 */
export function AlertsPage() {
  const { t, i18n } = useTranslation()
  const { client } = useAuth()
  const [status, setStatus] = useState<Status>('open')
  const alerts = useList<RiskAlert>(DM_PATHS.alerts(status))
  const [deciding, setDeciding] = useState<{ alert: RiskAlert; decision: Decision } | null>(null)
  const [menu, setMenu] = useState<{ anchor: HTMLElement; alert: RiskAlert } | null>(null)
  const [done, setDone] = useState<string | null>(null)

  const decide = async (note: string, meeting: Meeting | null) => {
    if (!deciding) return
    const body: DecisionRequest = { decision: deciding.decision, ...(note ? { note } : {}), ...(meeting ?? {}) }
    await client.request(DM_PATHS.decision(deciding.alert.id), { method: 'POST', body })
    setDone(t(`dm.decided.${deciding.decision}`, { name: fullName(deciding.alert) }))
    setDeciding(null)
    await alerts.reload()
  }

  return (
    <Stack>
      <PageHeader icon={WarningIcon} title={t('dm.alertsTitle')} description={t('dm.alertsIntro')} />
      <Stack spacing={2}>
        <Tabs value={status} onChange={(_, value: Status) => setStatus(value)} aria-label={t('dm.alertsTitle')}>
          <Tab value="open" label={t('dm.open')} />
          <Tab value="closed" label={t('dm.closed')} />
        </Tabs>
        {done && (
          <Alert severity="success" onClose={() => setDone(null)}>
            {done}
          </Alert>
        )}
        {alerts.error !== null && <Alert severity="error">{errorMessage(alerts.error, t)}</Alert>}
        {!alerts.loading && alerts.error === null && alerts.items.length === 0 ? (
          <Paper variant="outlined" sx={{ px: 2, py: 5, textAlign: 'center' }}>
            <Typography color="text.secondary">{t(status === 'open' ? 'dm.noOpenAlerts' : 'dm.noClosedAlerts')}</Typography>
          </Paper>
        ) : (
          <TableContainer component={Paper} variant="outlined">
            <Table size="small" sx={{ minWidth: 760 }}>
              <TableHead>
                <TableRow>
                  <TableCell>{t('dm.student')}</TableCell>
                  <TableCell>{t('dm.rule')}</TableCell>
                  <TableCell>{t('dm.raised')}</TableCell>
                  <TableCell align="right">{status === 'open' ? t('dm.yourDecision') : t('dm.decisionColumn')}</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {alerts.items.map((alert) => (
                  <TableRow key={alert.id} data-testid={`alert-${alert.id}`}>
                    <TableCell>
                      <Link to={`/students/${alert.studentId}`}>{fullName(alert)}</Link>
                      <Typography variant="caption" component="p" color="text.secondary">
                        {t('dm.classAndMatricule', { className: alert.className ?? '', matricule: alert.matricule })}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant="subtitle2" component="p">
                        {t(`dm.rules.${alert.ruleType}`)}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        {evidenceText(alert, t, i18n.language)}
                      </Typography>
                    </TableCell>
                    <TableCell sx={{ whiteSpace: 'nowrap' }}>{formatInstant(alert.raisedAt, i18n.language)}</TableCell>
                    <TableCell align="right">
                      {status === 'open' ? (
                        <Stack direction="row" spacing={1} sx={{ justifyContent: 'flex-end' }}>
                          <Button variant="contained" size="small" onClick={() => setDeciding({ alert, decision: 'SUMMON' })}>
                            {t('dm.decision.SUMMON')}
                          </Button>
                          <Button
                            variant="outlined"
                            size="small"
                            aria-haspopup="menu"
                            onClick={(e) => setMenu({ anchor: e.currentTarget, alert })}
                          >
                            {t('dm.otherDecisions')}
                          </Button>
                        </Stack>
                      ) : (
                        <Stack sx={{ alignItems: 'flex-end', gap: 0.5 }}>
                          {alert.decision && <Pill tone="neutral">{t(`dm.decision.${alert.decision}`)}</Pill>}
                          {alert.decisionNote && (
                            <Typography variant="caption" color="text.secondary" sx={{ maxWidth: 260, textAlign: 'right' }}>
                              {alert.decisionNote}
                            </Typography>
                          )}
                          {alert.decidedAt && (
                            <Typography variant="caption" sx={{ color: tokens.muted }}>
                              {formatInstant(alert.decidedAt, i18n.language)}
                            </Typography>
                          )}
                        </Stack>
                      )}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Stack>
      <Menu anchorEl={menu?.anchor} open={menu !== null} onClose={() => setMenu(null)}>
        {(['DISMISS', 'DISCIPLINARY', 'REFER'] as const).map((decision) => (
          <MenuItem
            key={decision}
            onClick={() => {
              if (menu) setDeciding({ alert: menu.alert, decision })
              setMenu(null)
            }}
          >
            {t(`dm.decision.${decision}`)}
          </MenuItem>
        ))}
      </Menu>
      <DecisionDialog
        decision={deciding?.decision ?? null}
        studentName={deciding?.alert.firstName ?? ''}
        onSubmit={decide}
        onClose={() => setDeciding(null)}
      />
    </Stack>
  )
}

const fullName = (a: { firstName: string; lastName: string }) => `${a.lastName} ${a.firstName}`

/** What the rule saw, in words: the days of a streak, or the count of unjustified lessons. */
function evidenceText(alert: RiskAlert, t: TFunction, language: string): string {
  const day = (iso: string) => formatDate(iso, language)
  const dates = alert.evidence.dates?.length ? alert.evidence.dates : [alert.asOf]
  switch (alert.ruleType) {
    case 'FULL_DAY_ABSENCE':
      return t('dm.evidence.FULL_DAY_ABSENCE', { count: dates.length, dates: dates.map(day).join(', ') })
    case 'CONSECUTIVE_LATENESS':
      return t('dm.evidence.CONSECUTIVE_LATENESS', { count: dates.length, dates: dates.map(day).join(', ') })
    case 'UNJUSTIFIED_ABSENCES':
      return t('dm.evidence.UNJUSTIFIED_ABSENCES', {
        count: alert.evidence.periods ?? alert.threshold,
        from: day(alert.evidence.from ?? alert.asOf),
        to: day(alert.evidence.to ?? alert.asOf),
      })
  }
}
