import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { isoDate, type Counts } from '../../api/dm'

// What both dashboards compute from the counts (UC-A6, UC-D8, D34).

/** BR-15: cancelled records are already left out of the counts by the server. */
export const absenceRate = (c: Counts) => (c.studentPeriods === 0 ? null : c.absentPeriods / c.studentPeriods)

/** A share in the reader's language; a dash when there is nothing to divide. */
export function usePercent() {
  const { i18n } = useTranslation()
  return (rate: number | null) =>
    rate === null ? '–' : new Intl.NumberFormat(i18n.language, { style: 'percent', maximumFractionDigits: 1 }).format(rate)
}

export interface Range {
  from: string
  to: string
}

/** The last 30 days by default, as the server does without dates (D34). */
export function useRange(days = 30) {
  const [range, setRange] = useState<Range>(() => {
    const today = new Date()
    const from = new Date(today)
    from.setDate(from.getDate() - (days - 1))
    return { from: isoDate(from), to: isoDate(today) }
  })
  const valid = range.from !== '' && range.to !== '' && range.from <= range.to
  return { range, setRange, valid }
}
