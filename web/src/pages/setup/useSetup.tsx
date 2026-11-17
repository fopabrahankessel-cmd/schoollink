import { LinearProgress, Stack } from '@mui/material'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { STAFF_PATH, type StaffMember } from '../../api/staff'
import {
  PATHS,
  query,
  shortTime,
  WEEKDAYS,
  type AcademicYear,
  type Block,
  type Period,
  type SchoolClass,
  type Sequence,
  type Term,
  type TimetableSlot,
} from '../../api/structure'
import { currentYear } from '../../format'
import { useList, type ListState } from '../../hooks/useList'
import { Gap } from './steps'

export type State = 'ready' | 'todo' | 'partial'

export interface Step {
  key: string
  to: string
  title: string
  /** none: optional, never blocks (the letterhead), or kept up all year (the students) */
  state?: State
  /** what is in place, in words and figures */
  detail: ReactNode
  /** one extra line under the detail: a gap to close, or a progress bar */
  extra?: ReactNode
  lists: ListState<unknown>[]
}

/**
 * UC-A1, UC-A2, D44: what the school has defined for the current year, step by step in the order
 * they depend on each other, and what each still lacks. The admin's home and School settings read
 * this one list, so they cannot disagree.
 */
export function useSetup() {
  const { t } = useTranslation()
  const years = useList<AcademicYear>(PATHS.years)
  const year = currentYear(years.items)
  const terms = useList<Term>(year ? PATHS.terms + query({ academicYearId: year.id }) : null)
  const sequences = useList<Sequence>(PATHS.sequences)
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
  const termIds = new Set(terms.items.map((x) => x.id))
  const yearSequences = sequences.items.filter((s) => termIds.has(s.termId))
  const termsWithoutSequences = terms.items.filter((x) => yearSequences.filter((s) => s.termId === x.id).length < 2).length
  const currentSequence = dated(yearSequences)
  const firstPeriod = periods.items[0]
  const lastPeriod = periods.items[periods.items.length - 1]

  const steps: Step[] = [
    {
      key: 'calendar',
      to: '/structure/calendar',
      title: t('nav.calendar'),
      // roll calls need a year and its terms; sequences are noted, they do not hold attendance back
      state: year && terms.items.length > 0 ? 'ready' : year ? 'partial' : 'todo',
      detail: year
        ? t('setup.calendarDetail', {
            year: year.label,
            terms: t('setup.termCount', { count: terms.items.length }),
            sequences: t('setup.sequenceCount', { count: yearSequences.length }),
          })
        : t('setup.calendarNone'),
      extra:
        year && termsWithoutSequences > 0 ? <Gap>{t('setup.termsWithoutSequences', { count: termsWithoutSequences })}</Gap> : undefined,
      lists: [years, terms, sequences],
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


  return {
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
    termsWithoutSequences,
    firstPeriod,
    lastPeriod,
    steps,
  }
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

/** The item whose dates hold today, e.g. the current sequence (D44). */
function dated<T extends { startDate: string; endDate: string }>(items: T[], today = new Date()): T | undefined {
  const iso = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`
  return items.find((x) => x.startDate <= iso && iso <= x.endDate)
}
