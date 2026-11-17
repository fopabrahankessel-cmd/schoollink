import { Box, Button, LinearProgress, Paper, Stack, Typography } from '@mui/material'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { shortTime } from '../api/structure'
import { formatDate } from '../format'
import { PageHeader } from '../components/PageHeader'
import {
  ArrowRightIcon,
  CalendarBlankIcon,
  ChalkboardTeacherIcon,
  ClockIcon,
  SquaresFourIcon,
  TableIcon,
  UsersThreeIcon,
  type Icon,
} from '../icons'
import { tokens } from '../theme'
import { StepRow } from './setup/steps'
import { useSetup } from './setup/useSetup'

/**
 * UC-A1, UC-A2: the admin's home answers one question, "can teachers take attendance this year?",
 * with the setup steps in the order they depend on each other and what each still lacks (BR-1:
 * teachers record attendance for the classes and periods of the timetable).
 */
export function SetupOverview() {
  const { t, i18n } = useTranslation()
  const date = (iso: string) => formatDate(iso, i18n.language)

  const {
    years,
    year,
    terms,
    periods,
    blocks,
    classes,
    slots,
    staff,
    teachers,
    yearSlots,
    capacity,
    currentTerm,
    currentSequence,
    firstPeriod,
    lastPeriod,
    steps,
  } = useSetup()

  const loading = steps.some((s) => s.lists.some((l) => l.loading))
  const open = steps.filter((s) => s.state !== 'ready')
  const next = open[0]
  const readyCount = steps.length - open.length

  const stats: Stat[] = [
    {
      key: 'classes',
      icon: ChalkboardTeacherIcon,
      label: t('setup.statClasses'),
      value: classes.items.length,
      note: t('setup.blockCount', { count: blocks.items.length }),
      loading: classes.loading || blocks.loading,
    },
    {
      key: 'teachers',
      icon: UsersThreeIcon,
      label: t('setup.statTeachers'),
      value: teachers.length,
      note: t('setup.staffCount', { count: staff.items.length }),
      loading: staff.loading,
    },
    {
      key: 'lessons',
      icon: TableIcon,
      label: t('setup.statLessons'),
      value: yearSlots.length,
      note: t('setup.statLessonsOf', { count: capacity }),
      loading: slots.loading || classes.loading || periods.loading,
    },
    {
      key: 'periods',
      icon: ClockIcon,
      label: t('setup.statPeriods'),
      value: periods.items.length,
      note: firstPeriod && lastPeriod ? `${shortTime(firstPeriod.startTime)} – ${shortTime(lastPeriod.endTime)}` : '',
      loading: periods.loading,
    },
  ]

  return (
    <Stack>
      <PageHeader
        icon={SquaresFourIcon}
        title={t('setup.title')}
        description={
          <Box component="span" sx={{ display: 'block', minHeight: 21 }}>
            {loading ? '' : open.length === 0 ? t('setup.allReady') : t('setup.remaining', { count: open.length })}
          </Box>
        }
        actions={
          !loading &&
          next && (
            <Button component={Link} to={next.to} variant="contained" endIcon={<ArrowRightIcon weight="bold" aria-hidden />}>
              {t('setup.continue', { step: next.title })}
            </Button>
          )
        }
      />

      <Stack spacing={3}>
        <Box
          component="section"
          aria-label={t('setup.statsLabel')}
          sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: 'repeat(2, minmax(0, 1fr))', lg: 'repeat(4, minmax(0, 1fr))' } }}
        >
          {stats.map((stat) => (
            <StatCard key={stat.key} stat={stat} />
          ))}
        </Box>

        <Box
          sx={{
            display: 'grid',
            gap: 3,
            gridTemplateColumns: { xs: '1fr', lg: 'minmax(0, 1fr) 360px' },
            alignItems: 'start',
          }}
        >
          <Paper variant="outlined" component="section" aria-label={t('setup.stepsLabel')} sx={{ overflow: 'hidden' }}>
            <Stack direction="row" sx={{ alignItems: 'center', gap: 2, px: 2.5, py: 2, borderBottom: `1px solid ${tokens.border}` }}>
              <ProgressRing value={loading ? 0 : readyCount} total={steps.length} />
              <Box sx={{ minWidth: 0 }}>
                <Typography variant="h6" component="h2">
                  {t('setup.stepsTitle')}
                </Typography>
                <Typography variant="caption" color="text.secondary" component="p" sx={{ minHeight: 17 }}>
                  {loading ? '' : t('setup.progress', { ready: readyCount, total: steps.length })}
                </Typography>
              </Box>
            </Stack>
            <Box component="ol" sx={{ listStyle: 'none', m: 0, p: 0 }}>
              {steps.map((step) => (
                <StepRow key={step.key} step={step} loading={step.lists.some((l) => l.loading)} current={step === next} />
              ))}
            </Box>
          </Paper>

          <Paper variant="outlined" component="section" aria-labelledby="setup-year" sx={{ p: 2.5 }}>
            <Stack direction="row" sx={{ alignItems: 'center', gap: 1.5, mb: 2 }}>
              <Box aria-hidden sx={{ ...tile, bgcolor: tokens.amberSoft, color: tokens.amber }}>
                <CalendarBlankIcon size={20} />
              </Box>
              <Box sx={{ minWidth: 0 }}>
                <Typography id="setup-year" variant="h6" component="h2">
                  {year ? year.label : t('setup.yearTitle')}
                </Typography>
                {year && (
                  <Typography variant="caption" color="text.secondary" component="p">
                    {date(year.startDate)} – {date(year.endDate)}
                  </Typography>
                )}
              </Box>
            </Stack>
            {year ? (
              <>
                {currentTerm && (
                  <Box
                    sx={{
                      mb: 2,
                      p: 1.75,
                      borderRadius: `${tokens.radius.control}px`,
                      bgcolor: tokens.brandTint,
                      border: `1px solid ${tokens.brandSoft}`,
                    }}
                  >
                    <Typography sx={{ fontWeight: 600, mb: 1 }}>
                      {t('setup.termWeek', { week: currentTerm.week, weeks: currentTerm.weeks, term: currentTerm.term.label })}
                    </Typography>
                    <LinearProgress
                      variant="determinate"
                      value={(currentTerm.week / currentTerm.weeks) * 100}
                      aria-label={t('setup.termWeek', { week: currentTerm.week, weeks: currentTerm.weeks, term: currentTerm.term.label })}
                      sx={{ bgcolor: tokens.surface }}
                    />
                    {currentSequence && (
                      <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
                        {t('setup.currentSequence', { label: currentSequence.label })}
                      </Typography>
                    )}
                  </Box>
                )}
                {terms.loading ? null : terms.items.length === 0 ? (
                  <Empty>{t('setup.noTerms')}</Empty>
                ) : (
                  <Stack component="ul" sx={{ listStyle: 'none', m: 0, p: 0 }}>
                    {terms.items.map((term) => {
                      const now = term.id === currentTerm?.term.id
                      return (
                        <Stack
                          component="li"
                          key={term.id}
                          direction="row"
                          sx={{ py: 1.25, alignItems: 'center', gap: 1.25, '& + &': { borderTop: `1px solid ${tokens.rule}` } }}
                        >
                          <Box
                            aria-hidden
                            sx={{ width: 8, height: 8, borderRadius: 999, flex: 'none', bgcolor: now ? tokens.brand : tokens.control }}
                          />
                          <Typography sx={{ fontWeight: now ? 700 : 500, flex: 'none' }}>{term.label}</Typography>
                          <Typography color="text.secondary" sx={{ textAlign: 'right', ml: 'auto', fontSize: 13 }}>
                            {date(term.startDate)} – {date(term.endDate)}
                          </Typography>
                        </Stack>
                      )
                    })}
                  </Stack>
                )}
              </>
            ) : (
              <Empty>{years.loading ? '' : t('setup.noYear')}</Empty>
            )}
          </Paper>
        </Box>
      </Stack>
    </Stack>
  )
}

interface Stat {
  key: string
  icon: Icon
  label: string
  value: number
  note: string
  loading: boolean
}

const tile = {
  width: 40,
  height: 40,
  flex: 'none',
  display: 'grid',
  placeItems: 'center',
  borderRadius: `${tokens.radius.control}px`,
} as const

function StatCard({ stat }: { stat: Stat }) {
  const StatIcon = stat.icon
  return (
    <Paper variant="outlined" sx={{ p: 2, display: 'flex', flexDirection: 'column', gap: 1.5, minWidth: 0 }}>
      <Stack direction="row" sx={{ alignItems: 'center', justifyContent: 'space-between', gap: 1 }}>
        <Typography color="text.secondary" sx={{ fontWeight: 600, fontSize: 13 }} noWrap>
          {stat.label}
        </Typography>
        <Box aria-hidden sx={{ ...tile, width: 32, height: 32, bgcolor: tokens.brandSoft, color: tokens.brandDark }}>
          <StatIcon size={18} />
        </Box>
      </Stack>
      <Box>
        <Typography component="p" sx={{ fontSize: 28, fontWeight: 700, lineHeight: 1.1, letterSpacing: '-0.02em', minHeight: 31 }}>
          {stat.loading ? '' : stat.value}
        </Typography>
        <Typography variant="caption" color="text.secondary" component="p" noWrap sx={{ minHeight: 17 }}>
          {stat.loading ? '' : stat.note}
        </Typography>
      </Box>
    </Paper>
  )
}

/** x of n steps ready, as a ring; the caption beside it says the same in words. */
function ProgressRing({ value, total }: { value: number; total: number }) {
  const r = 18
  const length = 2 * Math.PI * r
  return (
    <Box aria-hidden sx={{ position: 'relative', width: 44, height: 44, flex: 'none' }}>
      <svg width="44" height="44" viewBox="0 0 44 44">
        <circle cx="22" cy="22" r={r} fill="none" stroke={tokens.rule} strokeWidth="5" />
        <circle
          cx="22"
          cy="22"
          r={r}
          fill="none"
          stroke={value === total ? tokens.positive : tokens.brand}
          strokeWidth="5"
          strokeLinecap="round"
          strokeDasharray={length}
          strokeDashoffset={length * (1 - value / total)}
          transform="rotate(-90 22 22)"
          style={{ transition: `stroke-dashoffset 600ms ${tokens.easeOut}` }}
        />
      </svg>
      <Box sx={{ position: 'absolute', inset: 0, display: 'grid', placeItems: 'center', fontSize: 12, fontWeight: 700 }}>
        {value}/{total}
      </Box>
    </Box>
  )
}

function Empty({ children }: { children: ReactNode }) {
  return (
    <Typography
      color="text.secondary"
      sx={{
        border: `1px dashed ${tokens.control}`,
        borderRadius: `${tokens.radius.control}px`,
        bgcolor: tokens.raised,
        px: 1.5,
        py: 1.25,
        minHeight: 44,
      }}
    >
      {children}
    </Typography>
  )
}
