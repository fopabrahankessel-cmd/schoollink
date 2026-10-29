import { Step, StepLabel, Stepper } from '@mui/material'
import { useTranslation } from 'react-i18next'

const STEPS = ['import.stepFile', 'import.stepCheck', 'import.stepImport'] as const

/** UC-A3 is a real sequence (file, check, import), so its steps are numbered. 3 = all done. */
export function ImportSteps({ active }: { active: 0 | 1 | 2 | 3 }) {
  const { t } = useTranslation()
  return (
    <Stepper activeStep={active} aria-label={t('import.steps')} sx={{ maxWidth: 560, mb: 3 }}>
      {STEPS.map((key) => (
        <Step key={key}>
          <StepLabel>{t(key)}</StepLabel>
        </Step>
      ))}
    </Stepper>
  )
}
