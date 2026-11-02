import { Box } from '@mui/material'
import type { CSSProperties } from 'react'
import { useTranslation } from 'react-i18next'
import type { LetterheadLayout, LetterheadLine, LetterheadSize } from '../../api/letterhead'
import { tokens } from '../../theme'

/**
 * The PDF's measures, in points (A4 portrait, 595 pt wide), shared with the phone's document
 * (UC-D9): 15 mm margins, a logo of at most 30 mm, text of 7 / 8.5 / 10 pt. The preview draws the
 * top of the page with these measures scaled to its width, so what the admin sees is what prints.
 */
const PAGE = 595.28
const MARGIN = 42.5
const LOGO = 85
const GAP = 12
const POINTS: Record<LetterheadSize, number> = { SMALL: 7, NORMAL: 8.5, LARGE: 10 }

/** n points at the sheet's scale (--pt is one point, set from the sheet's width). */
const pt = (n: number) => `calc(var(--pt) * ${n})`

interface Props {
  schoolName: string
  layout: LetterheadLayout
  french: LetterheadLine[]
  english: LetterheadLine[]
  /** An object URL of the logo, or null when there is none. */
  logoUrl: string | null
}

export function LetterheadPreview({ schoolName, layout, french, english, logoUrl }: Props) {
  const { t } = useTranslation()
  const logo = logoUrl && (
    <Box role="group" aria-label={t('letterhead.previewLogo')} sx={{ width: pt(LOGO), flex: 'none', display: 'flex', justifyContent: 'center' }}>
      <Box component="img" src={logoUrl} alt={t('letterhead.previewLogo')} sx={{ maxWidth: '100%', maxHeight: pt(LOGO), objectFit: 'contain' }} />
    </Box>
  )
  const frenchPart = <Column label={t('letterhead.previewFrench')} lines={french} />
  const englishPart = <Column label={t('letterhead.previewEnglish')} lines={english} />
  const shown = layout === 'BILINGUAL' ? [...french, ...english] : layout === 'FRENCH' ? french : english

  let header
  if (shown.length === 0) {
    // not set, or a logo alone: the school name, centred (D36)
    header = (
      <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: pt(6) }}>
        {logo}
        <Box style={{ fontWeight: 700, fontSize: pt(POINTS.LARGE + 2), textAlign: 'center' }}>{schoolName}</Box>
      </Box>
    )
  } else if (layout === 'BILINGUAL') {
    header = (
      <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: pt(GAP) }}>
        {frenchPart}
        {logo ?? <Box aria-hidden sx={{ width: pt(LOGO), flex: 'none' }} />}
        {englishPart}
      </Box>
    )
  } else {
    header = (
      <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: pt(6) }}>
        {logo}
        {layout === 'FRENCH' ? frenchPart : englishPart}
      </Box>
    )
  }

  return (
    <Box
      component="figure"
      aria-label={t('letterhead.preview')}
      sx={{
        m: 0,
        px: { xs: 1.5, sm: 4 },
        pt: { xs: 2, sm: 4 },
        borderRadius: `${tokens.radius.card}px`,
        // the desk the sheet lies on
        background: `radial-gradient(120% 90% at 50% 0%, ${tokens.surface} 0%, ${tokens.ground} 70%)`,
        border: `1px solid ${tokens.border}`,
        overflow: 'hidden',
      }}
    >
      <Box sx={{ maxWidth: 860, mx: 'auto', containerType: 'inline-size' }}>
        <Box
          sx={{
            '--pt': `calc(100cqw / ${PAGE})`,
            bgcolor: '#fff',
            color: '#111',
            fontFamily: '"Noto Sans", "Helvetica Neue", Arial, sans-serif',
            lineHeight: 1.3,
            px: pt(MARGIN),
            pt: pt(MARGIN),
            pb: pt(28),
            borderRadius: '3px 3px 0 0',
            boxShadow: '0 0 0 1px rgba(16, 32, 31, 0.06), 0 22px 48px -24px rgba(16, 32, 31, 0.45)',
            // the page goes on below: it fades into the desk
            maskImage: 'linear-gradient(to bottom, #000 72%, transparent)',
            overflowWrap: 'anywhere',
          }}
        >
          {header}
          <Box aria-hidden sx={{ mt: pt(10), borderTop: `${pt(0.8)} solid #111` }} />
          <ReportGhost />
        </Box>
      </Box>
    </Box>
  )
}

function Column({ label, lines }: { label: string; lines: LetterheadLine[] }) {
  return (
    <Box role="group" aria-label={label} sx={{ flex: 1, minWidth: 0 }} style={{ textAlign: 'center' }}>
      {lines.map((line, i) => (
        <Box key={i} style={lineStyle(line)}>
          {line.text.trim() || ' '}
        </Box>
      ))}
    </Box>
  )
}

const lineStyle = (line: LetterheadLine): CSSProperties => ({ fontWeight: line.bold ? 700 : 400, fontSize: pt(POINTS[line.size]) })

/** The body of the report, sketched: where the header sits on the real document. */
function ReportGhost() {
  const bar = (width: string, height: number, mt: number, shade = '#e7eded') => (
    <Box sx={{ width, height: pt(height), mt: pt(mt), bgcolor: shade, borderRadius: pt(2), mx: 'auto' }} />
  )
  return (
    <Box aria-hidden>
      {bar('46%', 9, 18)}
      {bar('30%', 14, 8, '#dfe7e7')}
      {bar('52%', 6, 8)}
      <Box sx={{ mt: pt(20), display: 'grid', gridTemplateColumns: '8% 1fr 22%', gap: pt(3) }}>
        {Array.from({ length: 12 }, (_, i) => (
          <Box key={i} sx={{ height: pt(10), bgcolor: i < 3 ? '#dfe7e7' : '#f0f4f4', borderRadius: pt(1.5) }} />
        ))}
      </Box>
    </Box>
  )
}
