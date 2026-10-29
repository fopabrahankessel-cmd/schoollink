import { Stack } from '@mui/material'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { PATHS, query, type AcademicYear, type Holiday, type Term } from '../../api/structure'
import { CrudSection, type Field } from '../../components/CrudSection'
import { PageHeader } from '../../components/PageHeader'
import { CalendarBlankIcon, CalendarCheckIcon, GraduationCapIcon, SquaresFourIcon } from '../../icons'
import { formatDate } from '../../format'

/**
 * UC-A1, D10: academic years, their terms, and the holidays inside each term. Select a year to
 * see its terms, then a term to see its holidays. The server checks the dates (order, inside the
 * parent, no overlapping terms).
 */
export function CalendarPage() {
  const { t, i18n } = useTranslation()
  const [year, setYear] = useState<AcademicYear | null>(null)
  const [term, setTerm] = useState<Term | null>(null)
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
      <PageHeader icon={CalendarBlankIcon} title={t('structure.calendarTitle')} description={t('structure.calendarIntro')} />
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
        onSelect={setTerm}
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
