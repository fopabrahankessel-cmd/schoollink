import { Box, Paper, Stack, Typography } from '@mui/material'
import { useTranslation } from 'react-i18next'
import { PageHeader } from '../../components/PageHeader'
import { GearSixIcon } from '../../icons'
import { tokens } from '../../theme'
import { StepRow } from './steps'
import { useSetup, type Step } from './useSetup'

/**
 * D44: everything that defines the school, in one place. What is defined once (calendar with its
 * sequences, periods, classes, timetable, letterhead) comes first; the people, kept up to date all
 * year, follow and also stay in the menu. Each line opens the screen where it is done.
 */
export function SchoolSettingsPage() {
  const { t } = useTranslation()
  const { steps, termsWithoutSequences } = useSetup()
  const step = (key: string) => steps.find((s) => s.key === key)!

  // here the calendar is complete only with each term's two sequences (D44)
  const calendar = step('calendar')
  const defined: Step[] = [
    calendar.state === 'ready' && termsWithoutSequences > 0 ? { ...calendar, state: 'partial' } : calendar,
    step('periods'),
    step('classes'),
    step('timetable'),
    { key: 'letterhead', to: '/structure/letterhead', title: t('nav.letterhead'), detail: t('settings.letterheadDetail'), lists: [] },
  ]
  const people: Step[] = [
    step('staff'),
    { key: 'students', to: '/school/students', title: t('nav.students'), detail: t('settings.studentsDetail'), lists: [] },
  ]
  const loading = steps.some((s) => s.lists.some((l) => l.loading))
  const open = [...defined, ...people].filter((s) => s.state !== undefined && s.state !== 'ready').length

  return (
    <Stack sx={{ maxWidth: 1040 }}>
      <PageHeader
        icon={GearSixIcon}
        title={t('settings.title')}
        description={
          <>
            {t('settings.intro')}
            <Box component="span" sx={{ display: 'block', mt: 0.5, minHeight: 21, fontWeight: 600, color: tokens.text }}>
              {loading ? '' : open === 0 ? t('settings.allReady') : t('settings.remaining', { count: open })}
            </Box>
          </>
        }
      />
      <Stack spacing={3}>
        <Group title={t('settings.defined')} hint={t('settings.definedHint')} steps={defined} />
        <Group title={t('settings.people')} hint={t('settings.peopleHint')} steps={people} />
      </Stack>
    </Stack>
  )
}

function Group({ title, hint, steps }: { title: string; hint: string; steps: Step[] }) {
  return (
    <Paper variant="outlined" component="section" aria-label={title} sx={{ overflow: 'hidden' }}>
      <Box sx={{ px: 2.5, py: 2, borderBottom: `1px solid ${tokens.border}` }}>
        <Typography variant="h6" component="h2">
          {title}
        </Typography>
        <Typography variant="caption" color="text.secondary" component="p">
          {hint}
        </Typography>
      </Box>
      <Box component="ol" sx={{ listStyle: 'none', m: 0, p: 0 }}>
        {steps.map((s) => (
          <StepRow key={s.key} step={s} loading={s.lists.some((l) => l.loading)} current={false} />
        ))}
      </Box>
    </Paper>
  )
}
