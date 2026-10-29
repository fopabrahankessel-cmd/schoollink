import { ToggleButton, ToggleButtonGroup } from '@mui/material'
import { useTranslation } from 'react-i18next'
import { LANGUAGES, setLanguage, type Language } from '../i18n'

/** NFR-12: switches the interface between French and English; remembered on this device. */
export function LanguageSwitch({ color }: { color?: 'inherit' }) {
  const { t, i18n } = useTranslation()
  return (
    <ToggleButtonGroup
      size="small"
      exclusive
      value={i18n.resolvedLanguage}
      onChange={(_, value: Language | null) => value && setLanguage(value)}
      aria-label={t('language.label')}
      sx={
        color === 'inherit'
          ? {
              '& .MuiToggleButton-root': { color: 'inherit', borderColor: 'currentColor' },
              '& .MuiToggleButton-root.Mui-selected': { color: 'primary.main', bgcolor: 'common.white' },
              '& .MuiToggleButton-root.Mui-selected:hover': { bgcolor: 'grey.100' },
            }
          : undefined
      }
    >
      {LANGUAGES.map((language) => (
        <ToggleButton key={language} value={language} lang={language} aria-label={t(`language.${language}`)}>
          {language.toUpperCase()}
        </ToggleButton>
      ))}
    </ToggleButtonGroup>
  )
}
