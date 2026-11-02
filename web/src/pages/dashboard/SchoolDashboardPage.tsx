import { Alert, Box, LinearProgress, Paper, Stack, Typography } from '@mui/material'
import { useTranslation } from 'react-i18next'
import { schoolDashboardPath, type Channel, type SchoolDashboard, type SmsUsage } from '../../api/dashboard'
import { errorMessage } from '../../api/errors'
import { absenceRate, usePercent, useRange } from '../../components/dashboard/figures'
import { ClassTable, Figure, Figures, RangeFields, RuleTable, Section } from '../../components/dashboard/parts'
import { LazyTrendCharts } from '../../components/dashboard/LazyTrendCharts'
import { PageHeader } from '../../components/PageHeader'
import { useResource } from '../../hooks/useResource'
import { ChartLineIcon, WarningIcon } from '../../icons'
import { tokens } from '../../theme'

/**
 * UC-A6: the whole school over a range (the last 30 days by default): attendance and lateness day
 * by day (BR-8, BR-9 kept apart), alerts per rule, how many notifications reached the parents
 * (BR-24), and this month's SMS against the school's cap (BR-25, NFR-14).
 */
export function SchoolDashboardPage() {
  const { t } = useTranslation()
  const percent = usePercent()
  const { range, setRange, valid } = useRange()
  const dashboard = useResource<SchoolDashboard>(valid ? schoolDashboardPath(range.from, range.to) : null)
  const data = dashboard.data

  return (
    <Stack>
      <PageHeader icon={ChartLineIcon} title={t('dashboard.title')} description={t('dashboard.intro')} />
      <Stack spacing={3}>
        <RangeFields range={range} valid={valid} onChange={setRange} />
        {dashboard.error !== null && <Alert severity="error">{errorMessage(dashboard.error, t)}</Alert>}
        {data && (
          <>
            <Figures>
              <Figure label={t('dm.kpi.absenceRate')} value={percent(absenceRate(data.totals))} />
              <Figure label={t('dm.kpi.unjustifiedAbsences')} value={data.totals.unjustifiedAbsentPeriods} />
              <Figure label={t('dm.kpi.lateness')} value={data.totals.lateness} />
              <Figure label={t('dashboard.openAlerts')} value={data.alerts.reduce((sum, r) => sum + r.open, 0)} />
            </Figures>

            <Section title={t('dashboard.byDay')}>
              {data.days.length === 0 ? (
                <Typography color="text.secondary">{t('dm.noActivity')}</Typography>
              ) : (
                <LazyTrendCharts days={data.days} />
              )}
            </Section>

            <Section title={t('dashboard.notifications')} intro={t('dashboard.notificationsIntro')}>
              <Box sx={{ display: 'grid', gap: 1.5, gridTemplateColumns: { xs: '1fr', md: 'repeat(3, 1fr)' } }}>
                <DeliveryCard name={t('dashboard.push')} channel={data.delivery.push} opens />
                <DeliveryCard name={t('dashboard.sms')} channel={data.delivery.sms} />
                <SmsMeter usage={data.sms} />
              </Box>
            </Section>

            <Section title={t('dm.byClass')}>
              <ClassTable classes={data.classes} />
            </Section>
            <Section title={t('dm.alertsByRule')}>
              <RuleTable alerts={data.alerts} />
            </Section>
          </>
        )}
      </Stack>
    </Stack>
  )
}

/**
 * BR-24: the share of finished sends that got through, then what each send became. Only a push
 * can be opened; an SMS ends at "delivered" when the gateway confirms it.
 */
function DeliveryCard({ name, channel, opens = false }: { name: string; channel: Channel; opens?: boolean }) {
  const { t } = useTranslation()
  const percent = usePercent()
  const rows: [string, number][] = [
    ...(opens ? [[t('dashboard.opened'), channel.opened] as [string, number]] : []),
    [t('dashboard.delivered'), channel.delivered],
    [t('dashboard.sent'), channel.sent],
    [t('dashboard.pending'), channel.pending],
    [t('dashboard.failed'), channel.failed],
  ]
  return (
    <Paper variant="outlined" component="section" aria-label={name} sx={{ px: 2, py: 1.5 }}>
      <Typography variant="body2" sx={{ color: tokens.muted }}>
        {t('dashboard.deliveryRate', { channel: name })}
      </Typography>
      <Typography sx={{ fontSize: 24, fontWeight: 700, fontVariantNumeric: 'tabular-nums' }}>{percent(channel.deliveryRate)}</Typography>
      <Box component="dl" sx={{ m: 0, mt: 1, display: 'grid', gridTemplateColumns: '1fr auto', rowGap: 0.25, columnGap: 2 }}>
        {rows.map(([label, count]) => (
          <Box key={label} sx={{ display: 'contents' }}>
            <Typography component="dt" variant="body2" sx={{ color: tokens.muted }}>
              {label}
            </Typography>
            <Typography component="dd" variant="body2" sx={{ m: 0, textAlign: 'right', fontVariantNumeric: 'tabular-nums' }}>
              {count}
            </Typography>
          </Box>
        ))}
      </Box>
    </Paper>
  )
}

/** BR-25: this month's SMS against the cap. At the cap, only summons still go by SMS (D12). */
function SmsMeter({ usage }: { usage: SmsUsage }) {
  const { t, i18n } = useTranslation()
  const number = new Intl.NumberFormat(i18n.language)
  const share = usage.monthlyCap > 0 ? Math.min(1, usage.sentThisMonth / usage.monthlyCap) : 1
  const label = t('dashboard.smsUsed', { sent: number.format(usage.sentThisMonth), cap: number.format(usage.monthlyCap) })
  return (
    <Paper variant="outlined" component="section" aria-label={t('dashboard.smsMonth')} sx={{ px: 2, py: 1.5 }}>
      <Typography variant="body2" sx={{ color: tokens.muted }}>
        {t('dashboard.smsMonth')}
      </Typography>
      <Typography sx={{ fontSize: 24, fontWeight: 700, fontVariantNumeric: 'tabular-nums' }}>{number.format(usage.sentThisMonth)}</Typography>
      <Typography variant="body2" sx={{ color: tokens.muted, mb: 1 }}>
        {t('dashboard.smsCap', { cap: number.format(usage.monthlyCap) })}
      </Typography>
      <LinearProgress
        variant="determinate"
        value={share * 100}
        aria-label={label}
        color={usage.capReached ? 'warning' : 'primary'}
        sx={{ height: 8, borderRadius: 4, bgcolor: tokens.rule }}
      />
      {usage.capReached ? (
        <Stack direction="row" spacing={0.75} sx={{ mt: 1, alignItems: 'flex-start', color: tokens.warning }}>
          <WarningIcon size={18} aria-hidden style={{ flex: 'none', marginTop: 1 }} />
          <Typography variant="body2" sx={{ color: tokens.pill.warning.ink }}>
            {t('dashboard.capReached')}
          </Typography>
        </Stack>
      ) : (
        <Typography variant="body2" sx={{ mt: 1, color: tokens.muted }}>
          {t('dashboard.capLeft', { count: usage.monthlyCap - usage.sentThisMonth, left: number.format(usage.monthlyCap - usage.sentThisMonth) })}
        </Typography>
      )}
    </Paper>
  )
}
