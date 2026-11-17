import { Stack } from '@mui/material'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { PATHS, query, type AcademicYear, type Holiday, type Sequence, type Term } from '../../api/structure'
import { CrudSection, type Field } from '../../components/CrudSection'
import { PageHeader } from '../../components/PageHeader'
import { SETTINGS_PATH } from '../setup/paths'
import { CalendarBlankIcon, CalendarCheckIcon, CalendarDotsIcon, GraduationCapIcon, SquaresFourIcon } from '../../icons'
import { formatDate } from '../../format'

/**
 * UC-A1, D10, D44: academic years, their terms, and inside each term its two sequences and its
 * holidays. Select a year to see its terms, then a term to see its sequences and holidays. The server checks the dates (order, inside the
 * parent, no overlapping terms).
 */
export function CalendarPage() {
  const { t, i18n } = useTranslation()
  const [year, setYear] = useState<AcademicYear | null>(null)
  const [term, setTerm] = useState<Term | null>(null)
  // the term's place in its year: sequences are numbered across the year (Séquence 1 to 6)
  const [termIndex, setTermIndex] = useState(0)
  const date = (iso: string) => formatDate(iso, i18n.language)

  const dated: Field[] = [
    { name: 'label', label: t('structure.label'), type: 'text' },
    { name: 'startDate', label: t('structure.startDate'), type: 'date' },
    { name: 'endDate', label: t('structure.endDate'), type: 'date' },
  ]
  const datedColumns = <T extends { label: string; startDate: string; endDate: string }>() => [
    { label: t('structure.label'), render: (x: T) => x.label },
    { label: t('structure.startDate'), render: (x: T) => date(x.startDate) },
    { label: t('structure.endDate'), render: (x: T) => date(x.endDate) },
  ]
  const datedValues = (x: { label: string; startDate: string; endDate: string }) => ({
    label: x.label,
    startDate: x.startDate,
    endDate: x.endDate,
  })

  return (
    <Stack sx={{ maxWidth: 880 }}>
      <PageHeader icon={CalendarBlankIcon} title={t('structure.calendarTitle')} description={t('structure.calendarIntro')} back={{ to: SETTINGS_PATH, label: t('nav.schoolSettings') }} />
      <Stack spacing={3}>
      <CrudSection<AcademicYear>
        icon={GraduationCapIcon}
        hint={t('structure.yearsHint')}
        title={t('structure.years')}
        itemName={t('structure.year')}
        path={PATHS.years}
        listPath={PATHS.years}
        emptyText={t('structure.noYears')}
        columns={datedColumns<AcademicYear>()}
        fields={dated}
        toValues={datedValues}
        selectedId={year?.id}
        onSelect={(y) => {
          setYear(y)
          setTerm(null)
        }}
      />
      <CrudSection<Term>
        key={`terms-${year?.id ?? ''}`}
        icon={SquaresFourIcon}
        hint={year ? t('structure.termsHint') : undefined}
        title={year ? `${t('structure.terms')} · ${year.label}` : t('structure.terms')}
        itemName={t('structure.term')}
        path={PATHS.terms}
        listPath={year ? PATHS.terms + query({ academicYearId: year.id }) : null}
        waitingText={t('structure.pickYear')}
        emptyText={t('structure.noTerms')}
        columns={datedColumns<Term>()}
        fields={dated}
        toValues={datedValues}
        fixed={year ? { academicYearId: year.id } : undefined}
        selectedId={term?.id}
        onSelect={(x, all) => {
          setTerm(x)
          setTermIndex([...all].sort((a, b) => a.startDate.localeCompare(b.startDate)).findIndex((y) => y.id === x.id))
        }}
      />
      <CrudSection<Sequence>
        key={`sequences-${term?.id ?? ''}`}
        icon={CalendarDotsIcon}
        hint={term ? t('structure.sequencesHint') : undefined}
        title={term ? `${t('structure.sequences')} · ${term.label}` : t('structure.sequences')}
        itemName={t('structure.sequence')}
        path={PATHS.sequences}
        listPath={term ? PATHS.sequences + query({ termId: term.id }) : null}
        waitingText={t('structure.pickTermForSequences')}
        emptyText={t('structure.noSequences')}
        columns={[{ label: t('structure.number'), render: (x: Sequence) => x.number }, ...datedColumns<Sequence>()]}
        fields={[{ name: 'number', label: t('structure.number'), type: 'number' }, ...dated]}
        toValues={(x) => ({ number: String(x.number), ...datedValues(x) })}
        newValues={(items) => {
          const number = items.some((x) => x.number === 1) ? 2 : 1
          return { number: String(number), label: t('structure.sequenceLabel', { n: termIndex * 2 + number }) }
        }}
        fixed={term ? { termId: term.id } : undefined}
      />
      <CrudSection<Holiday>
        key={`holidays-${term?.id ?? ''}`}
        icon={CalendarCheckIcon}
        title={term ? `${t('structure.holidays')} · ${term.label}` : t('structure.holidays')}
        itemName={t('structure.holiday')}
        path={PATHS.holidays}
        listPath={term ? PATHS.holidays + query({ termId: term.id }) : null}
        waitingText={t('structure.pickTerm')}
        emptyText={t('structure.noHolidays')}
        columns={datedColumns<Holiday>()}
        fields={dated}
        toValues={datedValues}
        fixed={term ? { termId: term.id } : undefined}
      />
      </Stack>
    </Stack>
  )
}
