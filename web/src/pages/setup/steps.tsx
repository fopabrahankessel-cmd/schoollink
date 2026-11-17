import { Box, Chip, Link as MuiLink, Stack, Typography } from '@mui/material'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { errorMessage } from '../../api/errors'
import { CheckCircleIcon, CircleIcon, WarningCircleIcon } from '../../icons'
import { tokens } from '../../theme'
import type { State, Step } from './useSetup'

export function StepRow({ step, loading, current }: { step: Step; loading: boolean; current: boolean }) {
  const { t } = useTranslation()
  const failed = step.lists.find((l) => l.error !== null)
  return (
    <Box
      component="li"
      sx={{
        display: 'grid',
        gridTemplateColumns: { xs: '28px minmax(0, 1fr) auto', sm: '28px 150px minmax(0, 1fr) auto' },
        columnGap: 1.5,
        rowGap: 0.5,
        alignItems: 'start',
        px: 2.5,
        py: 2,
        bgcolor: current && !loading ? tokens.brandTint : 'transparent',
        '& + &': { borderTop: `1px solid ${tokens.rule}` },
      }}
    >
      <Box sx={{ lineHeight: 0 }}>{!loading && step.state && <StateIcon state={step.state} />}</Box>
      <MuiLink component={Link} to={step.to} sx={{ fontWeight: 700, color: tokens.text, lineHeight: '24px' }}>
        {step.title}
      </MuiLink>
      <Box sx={{ gridColumn: { xs: '2 / -1', sm: 'auto' }, gridRow: { xs: 2, sm: 'auto' }, minWidth: 0, pt: { sm: 0.25 } }}>
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
      <Box sx={{ justifySelf: 'end', gridColumn: { xs: 3, sm: 'auto' }, gridRow: { xs: 1, sm: 'auto' } }}>
        {!loading && !failed && step.state && <StateChip state={step.state} />}
      </Box>
    </Box>
  )
}

export function StateIcon({ state }: { state: State }) {
  if (state === 'ready') return <CheckCircleIcon size={24} weight="fill" color={tokens.positive} aria-hidden />
  if (state === 'partial') return <WarningCircleIcon size={24} weight="fill" color={tokens.amber} aria-hidden />
  return <CircleIcon size={24} color={tokens.control} aria-hidden />
}

export function StateChip({ state }: { state: State }) {
  const { t } = useTranslation()
  if (state === 'ready') return <Chip size="small" color="success" label={t('setup.ready')} />
  if (state === 'partial') return <Chip size="small" color="warning" label={t('setup.partial')} />
  return <Chip size="small" label={t('setup.todo')} />
}

export function Gap({ children }: { children: ReactNode }) {
  return (
    <Typography variant="caption" component="p" sx={{ color: tokens.warning, fontWeight: 600 }}>
      {children}
    </Typography>
  )
}
