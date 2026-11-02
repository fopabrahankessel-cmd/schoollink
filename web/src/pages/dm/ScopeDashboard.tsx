import { Alert, Stack, Typography } from '@mui/material'
import { useTranslation } from 'react-i18next'
import { DM_PATHS, type ScopeDashboard as Dashboard } from '../../api/dm'
import { errorMessage } from '../../api/errors'
import { absenceRate, usePercent, useRange } from '../../components/dashboard/figures'
import { ClassTable, Figure, Figures, RangeFields, RuleTable, Section } from '../../components/dashboard/parts'
import { LazyTrendCharts } from '../../components/dashboard/LazyTrendCharts'
import { PageHeader } from '../../components/PageHeader'
import { useResource } from '../../hooks/useResource'
import { SquaresFourIcon } from '../../icons'

/**
 * UC-D8, D34: the DM's home. Absences and lateness in their own classes over a range (the last 30
 * days by default), day by day and class by class, and the alerts each rule raised. Absences and
 * lateness are counted apart and never merged (BR-8, BR-9).
 */
export function ScopeDashboard() {
  const { t } = useTranslation()
  const percent = usePercent()
  const { range, setRange, valid } = useRange()
  const dashboard = useResource<Dashboard>(valid ? DM_PATHS.dashboard(range.from, range.to) : null)
  const data = dashboard.data

  return (
    <Stack>
      <PageHeader icon={SquaresFourIcon} title={t('dm.dashboardTitle')} description={t('dm.dashboardIntro')} />
      <Stack spacing={3}>
        <RangeFields range={range} valid={valid} onChange={setRange} />
        {dashboard.error !== null && <Alert severity="error">{errorMessage(dashboard.error, t)}</Alert>}
        {data && (
          <>
            <Figures>
              <Figure label={t('dm.kpi.absenceRate')} value={percent(absenceRate(data.totals))} />
              <Figure label={t('dm.kpi.unjustifiedAbsences')} value={data.totals.unjustifiedAbsentPeriods} />
              <Figure label={t('dm.kpi.lateness')} value={data.totals.lateness} />
              <Figure
                label={t('dm.kpi.openAlerts')}
                value={data.alerts.reduce((sum, r) => sum + r.open, 0)}
                to="/alerts"
                linkLabel={t('dm.seeAlerts')}
              />
            </Figures>
            <Section title={t('dashboard.byDay')}>
              {data.days.length === 0 ? (
                <Typography color="text.secondary">{t('dm.noActivity')}</Typography>
              ) : (
                <LazyTrendCharts days={data.days} />
              )}
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
