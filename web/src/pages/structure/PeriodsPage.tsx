import { Stack } from '@mui/material'
import { useTranslation } from 'react-i18next'
import { PATHS, shortTime, type Period } from '../../api/structure'
import { CrudSection } from '../../components/CrudSection'
import { PageHeader } from '../../components/PageHeader'
import { ClockIcon } from '../../icons'

/** UC-A1, D1: the school's lesson periods; the server refuses overlapping times. */
export function PeriodsPage() {
  const { t } = useTranslation()
  return (
    <Stack sx={{ maxWidth: 880 }}>
      <PageHeader icon={ClockIcon} title={t('structure.periods')} description={t('structure.periodsIntro')} />
      <CrudSection<Period>
        icon={ClockIcon}
        hint={t('structure.periodsHint')}
        title={t('structure.periods')}
        itemName={t('structure.period')}
        path={PATHS.periods}
        listPath={PATHS.periods}
        emptyText={t('structure.noPeriods')}
        columns={[
          { label: t('structure.number'), render: (p) => p.number },
          { label: t('structure.startTime'), render: (p) => shortTime(p.startTime) },
          { label: t('structure.endTime'), render: (p) => shortTime(p.endTime) },
        ]}
        fields={[
          { name: 'number', label: t('structure.number'), type: 'number' },
          { name: 'startTime', label: t('structure.startTime'), type: 'time' },
          { name: 'endTime', label: t('structure.endTime'), type: 'time' },
        ]}
        toValues={(p) => ({ number: String(p.number), startTime: shortTime(p.startTime), endTime: shortTime(p.endTime) })}
      />
    </Stack>
  )
}
