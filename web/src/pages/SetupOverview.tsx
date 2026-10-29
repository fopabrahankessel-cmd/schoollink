import { Box, Button, Chip, LinearProgress, Link as MuiLink, Paper, Stack, Typography } from '@mui/material'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { errorMessage } from '../api/errors'
import { STAFF_PATH, type StaffMember } from '../api/staff'
import {
  PATHS,
  query,
  shortTime,
  WEEKDAYS,
  type AcademicYear,
  type Block,
  type Period,
  type SchoolClass,
  type Term,
  type TimetableSlot,
} from '../api/structure'
import { currentYear, formatDate } from '../format'
import { useList, type ListState } from '../hooks/useList'
import { tokens } from '../theme'

type State = 'ready' | 'todo' | 'partial'

interface Step {
  key: string
  to: string
  title: string
  state: State
  /** what is in place, in words and figures */
  detail: ReactNode
  /** one extra line under the detail: a gap to close, or a progress bar */
  extra?: ReactNode
  lists: ListState<unknown>[]
}

/**
 * UC-A1, UC-A2: the admin's home answers one question, "can teachers take attendance this year?",
 * with the setup steps in the order they depend on each other and what each still lacks (BR-1:
 * teachers record attendance for the classes and periods of the timetable).
 */
export function SetupOverview() {
  const { t, i18n } = useTranslation()
  const date = (iso: string) => formatDate(iso, i18n.language)

  const years = useList<AcademicYear>(PATHS.years)
  const year = currentYear(years.items)
  const terms = useList<Term>(year ? PATHS.terms + query({ academicYearId: year.id }) : null)
  const periods = useList<Period>(PATHS.periods)
  const blocks = useList<Block>(PATHS.blocks)
  const classes = useList<SchoolClass>(year ? PATHS.classes + query({ academicYearId: year.id }) : null)
  const slots = useList<TimetableSlot>(PATHS.timetable)
  const staff = useList<StaffMember>(STAFF_PATH)

  const classIds = new Set(classes.items.map((c) => c.id))
  const yearSlots = slots.items.filter((s) => classIds.has(s.classId))
  const capacity = classes.items.length * periods.items.length * WEEKDAYS.length
  const classesWithoutLessons = classes.items.filter((c) => !yearSlots.some((s) => s.classId === c.id)).length
  const teachers = staff.items.filter((s) => s.status !== 'DISABLED' && s.roles.some((r) => r.role === 'TEACHER'))
  const teachersWithoutClass = teachers.filter((s) => s.teaching.length === 0).length
  const currentTerm = termToday(terms.items)
  const firstPeriod = periods.items[0]
  const lastPeriod = periods.items[periods.items.length - 1]

  const steps: Step[] = [
    {
      key: 'calendar',
      to: '/structure/calendar',
      title: t('nav.calendar'),
      state: year && terms.items.length > 0 ? 'ready' : year ? 'partial' : 'todo',
      detail: year
        ? t('setup.calendarDetail', { year: year.label, count: terms.items.length })
        : t('setup.calendarNone'),
      lists: [years, terms],
    },
    {
      key: 'periods',
      to: '/structure/periods',
      title: t('nav.periods'),
      state: periods.items.length > 0 ? 'ready' : 'todo',
      detail:
        firstPeriod && lastPeriod
          ? t('setup.periodsDetail', {
              count: periods.items.length,
              start: shortTime(firstPeriod.startTime),
              end: shortTime(lastPeriod.endTime),
            })
          : t('setup.periodsNone'),
      lists: [periods],
    },
    {
      key: 'classes',
      to: '/structure/classes',
      title: t('nav.classes'),
      state: classes.items.length > 0 ? 'ready' : 'todo',
      detail:
        classes.items.length > 0
          ? t('setup.classesDetail', {
              classes: t('setup.classCount', { count: classes.items.length }),
              blocks: t('setup.blockCount', { count: blocks.items.length }),
            })
          : t('setup.classesNone'),
      lists: [classes, blocks],
    },
    {
      key: 'timetable',
      to: '/structure/timetable',
      title: t('nav.timetable'),
      state: yearSlots.length === 0 ? 'todo' : classesWithoutLessons > 0 ? 'partial' : 'ready',
      detail:
        capacity > 0
          ? t('setup.timetableDetail', { count: yearSlots.length, capacity })
          : t('setup.timetableNone'),
      extra:
        capacity > 0 ? (
          <Stack spacing={0.75}>
            <LinearProgress
              variant="determinate"
              value={(yearSlots.length / capacity) * 100}
              aria-label={t('setup.timetableDetail', { count: yearSlots.length, capacity })}
            />
            {classesWithoutLessons > 0 && <Gap>{t('setup.classesWithoutLessons', { count: classesWithoutLessons })}</Gap>}
          </Stack>
        ) : undefined,
      lists: [slots, classes, periods],
    },
    {
      key: 'staff',
      to: '/staff',
      title: t('nav.staff'),
      state: teachers.length === 0 ? 'todo' : teachersWithoutClass > 0 ? 'partial' : 'ready',
      detail:
        staff.items.length > 0
          ? t('setup.staffDetail', {
              people: t('setup.staffCount', { count: staff.items.length }),
              teachers: t('setup.teacherCount', { count: teachers.length }),
            })
          : t('setup.staffNone'),
      extra:
        teachersWithoutClass > 0 ? <Gap>{t('setup.teachersWithoutClass', { count: teachersWithoutClass })}</Gap> : undefined,
      lists: [staff],
    },
  ]

  const loading = steps.some((s) => s.lists.some((l) => l.loading))
  const open = steps.filter((s) => s.state !== 'ready')
  const next = open[0]

  return (
    <Stack spacing={3}>
      <Stack
        direction={{ xs: 'column', sm: 'row' }}
        spacing={2}
        sx={{ justifyContent: 'space-between', alignItems: { sm: 'flex-start' } }}
      >
        <Box>
          <Typography variant="h5" component="h1">
            {t('setup.title')}
          </Typography>
          <Typography color="text.secondary" sx={{ mt: 0.5, minHeight: 20 }}>
            {loading ? '' : open.length === 0 ? t('setup.allReady') : t('setup.remaining', { count: open.length })}
          </Typography>
        </Box>
        {!loading && next && (
          <Button component={Link} to={next.to} variant="contained" sx={{ flex: 'none', alignSelf: { xs: 'flex-start' } }}>
            {t('setup.continue', { step: next.title })}
          </Button>
        )}
      </Stack>

      <Box
        sx={{
          display: 'grid',
          gap: 3,
          // 1152 = 768 + 24 + 360: the register, then the year beside it
          gridTemplateColumns: { xs: '1fr', lg: 'minmax(0, 1fr) 360px' },
          alignItems: 'start',
        }}
      >
        <Paper variant="outlined" component="section" aria-label={t('setup.stepsLabel')}>
          <Box component="ol" sx={{ listStyle: 'none', m: 0, p: 0 }}>
            {steps.map((step) => (
              <StepRow key={step.key} step={step} loading={step.lists.some((l) => l.loading)} />
            ))}
          </Box>
        </Paper>

        <Paper variant="outlined" component="section" aria-labelledby="setup-year" sx={{ p: 2.5 }}>
          <Typography id="setup-year" variant="h6" component="h2">
            {year ? year.label : t('setup.yearTitle')}
          </Typography>
          {year ? (
            <>
              <Typography color="text.secondary" sx={{ mb: 2 }}>
                {date(year.startDate)} – {date(year.endDate)}
              </Typography>
              {terms.loading ? null : terms.items.length === 0 ? (
                <Empty>{t('setup.noTerms')}</Empty>
              ) : (
                <Stack component="ul" sx={{ listStyle: 'none', m: 0, p: 0 }}>
                  {terms.items.map((term) => (
                    <Stack
                      component="li"
                      key={term.id}
                      direction="row"
                      sx={{ py: 1.25, borderTop: `1px solid ${tokens.rule}`, justifyContent: 'space-between', gap: 2 }}
                    >
                      <Typography sx={{ fontWeight: 500, flex: 'none' }}>{term.label}</Typography>
                      <Typography color="text.secondary" sx={{ textAlign: 'right' }}>
                        {date(term.startDate)} – {date(term.endDate)}
                      </Typography>
                    </Stack>
                  ))}
                </Stack>
              )}
              {currentTerm && (
                <Stack spacing={0.75} sx={{ mt: 2 }}>
                  <Typography color="text.secondary">
                    {t('setup.termWeek', { week: currentTerm.week, weeks: currentTerm.weeks, term: currentTerm.term.label })}
                  </Typography>
                  <LinearProgress
                    variant="determinate"
                    value={(currentTerm.week / currentTerm.weeks) * 100}
                    aria-label={t('setup.termWeek', { week: currentTerm.week, weeks: currentTerm.weeks, term: currentTerm.term.label })}
                  />
                </Stack>
              )}
            </>
          ) : (
            <Empty>{years.loading ? '' : t('setup.noYear')}</Empty>
          )}
        </Paper>
      </Box>
    </Stack>
  )
}

function StepRow({ step, loading }: { step: Step; loading: boolean }) {
  const { t } = useTranslation()
  const failed = step.lists.find((l) => l.error !== null)
  return (
    <Box
      component="li"
      sx={{
        display: 'grid',
        gridTemplateColumns: { xs: '1fr auto', sm: '150px minmax(0, 1fr) auto' },
        columnGap: 2,
        rowGap: 0.5,
        alignItems: 'start',
        px: 2.5,
        py: 2,
        '& + &': { borderTop: `1px solid ${tokens.rule}` },
      }}
    >
      <MuiLink component={Link} to={step.to} sx={{ fontWeight: 600, color: tokens.text }}>
        {step.title}
      </MuiLink>
      <Box sx={{ gridColumn: { xs: '1 / -1', sm: 'auto' }, gridRow: { xs: 2, sm: 'auto' }, minWidth: 0 }}>
        {failed ? (
          <Typography color="error">{errorMessage(failed.error, t)}</Typography>
        ) : loading ? (
          <Box sx={{ height: 12, width: 180, mt: 0.5, borderRadius: 1, bgcolor: tokens.raised }} />
        ) : (
          <Stack spacing={1}>
            <Typography>{step.detail}</Typography>
            {step.extra}
          </Stack>
        )}
      </Box>
      <Box sx={{ justifySelf: 'end' }}>{!loading && !failed && <StateChip state={step.state} />}</Box>
    </Box>
  )
}

function StateChip({ state }: { state: State }) {
  const { t } = useTranslation()
  if (state === 'ready') return <Chip size="small" color="success" label={t('setup.ready')} />
  if (state === 'partial') return <Chip size="small" color="warning" label={t('setup.partial')} />
  return <Chip size="small" label={t('setup.todo')} />
}

function Gap({ children }: { children: ReactNode }) {
  return (
    <Typography variant="caption" component="p" sx={{ color: tokens.warning }}>
      {children}
    </Typography>
  )
}

function Empty({ children }: { children: ReactNode }) {
  return (
    <Typography
      color="text.secondary"
      sx={{ border: `1px dashed ${tokens.control}`, borderRadius: `${tokens.radius.card}px`, px: 1.5, py: 1, minHeight: 38 }}
    >
      {children}
    </Typography>
  )
}

const DAY = 24 * 60 * 60 * 1000

/** The term containing today, and which of its weeks today falls in (week 1 starts on its first day). */
function termToday(terms: Term[], today = new Date()) {
  const day = (iso: string) => {
    const [y, m, d] = iso.split('-').map(Number)
    return Date.UTC(y, m - 1, d)
  }
  const now = Date.UTC(today.getFullYear(), today.getMonth(), today.getDate())
  const term = terms.find((x) => day(x.startDate) <= now && now <= day(x.endDate))
  if (!term) return null
  const weeks = Math.ceil((day(term.endDate) - day(term.startDate) + DAY) / (7 * DAY))
  const week = Math.floor((now - day(term.startDate)) / (7 * DAY)) + 1
  return { term, week, weeks }
}
