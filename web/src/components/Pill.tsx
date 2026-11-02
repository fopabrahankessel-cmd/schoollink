import { Box } from '@mui/material'
import type { ReactNode } from 'react'
import { tokens } from '../theme'

/** A short status in a rounded pill: always a word, never colour alone. */
export function Pill({ tone, children }: { tone: keyof typeof tokens.pill; children: ReactNode }) {
  const { bg, ink } = tokens.pill[tone]
  return (
    <Box
      component="span"
      sx={{
        display: 'inline-flex',
        alignItems: 'center',
        px: 1,
        py: 0.25,
        borderRadius: `${tokens.radius.chip}px`,
        bgcolor: bg,
        color: ink,
        fontSize: 12,
        fontWeight: 600,
        lineHeight: 1.6,
        whiteSpace: 'nowrap',
      }}
    >
      {children}
    </Box>
  )
}
