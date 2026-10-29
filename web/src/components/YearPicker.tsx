import { TextField } from '@mui/material'
import { useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import type { AcademicYear } from '../api/structure'
import { currentYear } from '../format'

/** Chooses an academic year; starts on the current one. */
export function YearPicker({
  years,
  value,
  onChange,
}: {
  years: AcademicYear[]
  value: string
  onChange: (id: string) => void
}) {
  const { t } = useTranslation()
  useEffect(() => {
    if (!value && years.length > 0) onChange(currentYear(years)?.id ?? '')
  }, [years, value, onChange])

  return (
    <TextField
      select
      label={t('structure.year')}
      value={value}
      onChange={(e) => onChange(e.target.value)}
      margin="none"
      sx={{ maxWidth: 260 }}
      slotProps={{ select: { native: true }, inputLabel: { shrink: true } }}
    >
      <option value="" disabled>
        {t('crud.choose')}
      </option>
      {years.map((y) => (
        <option key={y.id} value={y.id}>
          {y.label}
        </option>
      ))}
    </TextField>
  )
}
