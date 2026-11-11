import {
  Alert,
  Box,
  Button,
  GlobalStyles,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from '@mui/material'
import { useEffect, useState } from 'react'
import type { TFunction } from 'i18next'
import { useTranslation } from 'react-i18next'
import { useSearchParams } from 'react-router'
import { isoDate } from '../../api/dm'
import { errorMessage } from '../../api/errors'
import { LETTERHEAD_PATH, type Letterhead } from '../../api/letterhead'
import { DEFAULT_DATE_PATH, reportPath, type FullDayReport, type ReportClass } from '../../api/report'
import { useAuth } from '../../auth/useAuth'
import { PageHeader } from '../../components/PageHeader'
import { useLogoUrl } from '../../hooks/useLogoUrl'
import { useResource } from '../../hooks/useResource'
import { FileTextIcon, PrinterIcon } from '../../icons'
import { tokens } from '../../theme'
import { LetterheadHeader } from '../structure/LetterheadPreview'

/**
 * Printing shows the sheet alone, on A4, without the app around it. The browser's print dialog
 * also saves it as a PDF, so the page needs no PDF of its own (D41).
 */
const PRINT_STYLES = (
  <GlobalStyles
    styles={{
      '@media print': {
        '@page': { size: 'A4', margin: 0 },
        'body *': { visibility: 'hidden' },
        '.report-sheet, .report-sheet *': { visibility: 'visible' },
        // doubled to outrank the outlined Paper's own border and corners
        '.report-sheet.report-sheet': { position: 'absolute', left: 0, top: 0, width: '210mm', boxShadow: 'none', border: 0, borderRadius: 0 },
      },
    }}
  />
)

/**
 * UC-D9, D41: "Rapport de la journée" on the web. The students of the DM's classes absent from
 * every timetabled period of a day, grouped by class, under the school letterhead (D36), as
 * Android's PDF shows them. The day is in the address (`?date=`); without one, the server's default
 * day (today from 17 h, else the school day before).
 */
export function ReportPage() {
  const { t, i18n } = useTranslation()
  const { client } = useAuth()
  const [params, setParams] = useSearchParams()
  const date = params.get('date')
  const fallback = useResource<{ date: string }>(date ? null : DEFAULT_DATE_PATH)
  const report = useResource<FullDayReport>(date ? reportPath(date) : null)
  const letterhead = useResource<Letterhead>(LETTERHEAD_PATH)
  const logoUrl = useLogoUrl(client, letterhead.data?.hasLogo ? letterhead.data.logoVersion : null)
  // read once: the page does not follow midnight
  const [today] = useState(() => isoDate(new Date()))
  const [generatedAt] = useState(() => new Date())

  const defaultDate = fallback.data?.date
  useEffect(() => {
    if (!date && defaultDate) setParams({ date: defaultDate }, { replace: true })
  }, [date, defaultDate, setParams])

  const longDate = (iso: string) =>
    new Intl.DateTimeFormat(i18n.language, { dateStyle: 'full' }).format(new Date(`${iso}T12:00:00`))
  const data = report.data
  const ready = data !== null && data.blocks.length > 0
  const error = report.error ?? fallback.error

  return (
    <Stack>
      {PRINT_STYLES}
      <PageHeader
        icon={FileTextIcon}
        title={t('report.title')}
        description={t('report.intro')}
        actions={
          <Stack direction="row" spacing={1} sx={{ alignItems: 'center', flexWrap: 'wrap', rowGap: 1 }}>
            <TextField
              id="report-date"
              type="date"
              size="small"
              label={t('report.date')}
              value={date ?? ''}
              onChange={(e) => e.target.value && setParams({ date: e.target.value })}
              slotProps={{ htmlInput: { max: today }, inputLabel: { shrink: true } }}
            />
            {ready && (
              <Button variant="contained" startIcon={<PrinterIcon size={18} aria-hidden />} onClick={() => window.print()}>
                {t('report.print')}
              </Button>
            )}
          </Stack>
        }
      />
      <Stack spacing={2}>
        {error !== null && error !== undefined && <Alert severity="error">{errorMessage(error, t)}</Alert>}
        {data && !ready && (
          <Paper variant="outlined" sx={{ px: 2, py: 5, textAlign: 'center' }}>
            <Typography sx={{ fontWeight: 600 }}>{t('report.nobody', { date: longDate(data.date) })}</Typography>
            {/* nobody absent where the roll was called: the classes that were not called stay named */}
            {data.unrecorded.length > 0 && (
              <Typography variant="body2" color="text.secondary" sx={{ mt: 1, fontStyle: 'italic' }}>
                {unrecordedLine(data, t)}
              </Typography>
            )}
          </Paper>
        )}
        {/* without its letterhead (not readable), the sheet still prints with the school's rows */}
        {ready && (letterhead.data !== null || letterhead.error !== null) && (
          <Sheet
            report={data}
            letterhead={letterhead.data}
            logoUrl={logoUrl}
            date={capitalised(longDate(data.date))}
            footer={t('report.footer', {
              date: new Intl.DateTimeFormat(i18n.language, { dateStyle: 'medium' }).format(generatedAt),
              time: new Intl.DateTimeFormat(i18n.language, { timeStyle: 'short' }).format(generatedAt),
            })}
          />
        )}
      </Stack>
    </Stack>
  )
}

const capitalised = (text: string) => text.charAt(0).toUpperCase() + text.slice(1)

/** "Heures non saisies …: 4e C (H2, H3)", or '' when every lesson was recorded. */
function unrecordedLine(report: FullDayReport, t: TFunction): string {
  if (report.unrecorded.length === 0) return ''
  const list = report.unrecorded
    .map((u) => `${u.className} (${u.periods.map((p) => t('report.period', { number: p })).join(', ')})`)
    .join(', ')
  return t('report.unrecorded', { list })
}

/** The A4 sheet: letterhead, title, day, who and how many, one table per class, then the footer. */
function Sheet({
  report,
  letterhead,
  logoUrl,
  date,
  footer,
}: {
  report: FullDayReport
  letterhead: Letterhead | null
  logoUrl: string | null
  date: string
  footer: string
}) {
  const { t } = useTranslation()
  const summary = [
    report.blocks.map((b) => b.blockName).join(', '),
    report.generatedBy ? t('report.by', { name: report.generatedBy }) : '',
    t('report.summary', {
      students: t('report.students', { count: report.studentCount }),
      classes: t('report.classes', { count: report.classCount }),
    }),
  ]
    .filter(Boolean)
    .join(' – ')
  const unrecorded = unrecordedLine(report, t)

  return (
    <Paper
      className="report-sheet"
      variant="outlined"
      sx={{ width: '100%', maxWidth: '210mm', mx: 'auto', p: { xs: 2, sm: '15mm' }, bgcolor: '#fff', color: '#000' }}
    >
      {letterhead && (
        <LetterheadHeader
          schoolName={letterhead.schoolName}
          layout={letterhead.layout}
          french={letterhead.french}
          english={letterhead.english}
          logoUrl={logoUrl}
        />
      )}
      <Typography component="h2" sx={{ mt: 3, fontSize: 18, fontWeight: 700, textAlign: 'center' }}>
        {t('report.sheetTitle')}
      </Typography>
      <Typography sx={{ textAlign: 'center', fontWeight: 600 }}>{date}</Typography>
      <Typography variant="body2" sx={{ textAlign: 'center', mt: 0.5, mb: 2 }}>
        {summary}
      </Typography>
      {report.blocks.flatMap((block) =>
        block.classes.map((c) => <ClassTable key={`${block.blockName}/${c.className}`} schoolClass={c} />),
      )}
      {unrecorded && (
        <Typography variant="body2" sx={{ mt: 2, fontStyle: 'italic' }}>
          {unrecorded}
        </Typography>
      )}
      <Typography variant="caption" component="p" sx={{ mt: 3, color: tokens.muted, textAlign: 'right' }}>
        {footer}
      </Typography>
    </Paper>
  )
}

function ClassTable({ schoolClass }: { schoolClass: ReportClass }) {
  const { t } = useTranslation()
  const heading = t('report.classHeading', { className: schoolClass.className, count: schoolClass.students.length })
  return (
    <Box sx={{ mt: 2, breakInside: 'avoid' }}>
      <Typography component="h3" sx={{ fontWeight: 700, fontSize: 14, mb: 0.5 }}>
        {heading}
      </Typography>
      <Table size="small" aria-label={heading} sx={{ '& td, & th': { borderColor: '#c3ccda', px: 1 } }}>
        <TableHead>
          <TableRow>
            <TableCell sx={{ width: 48, fontWeight: 700 }}>{t('report.columnNumber')}</TableCell>
            <TableCell sx={{ fontWeight: 700 }}>{t('report.columnName')}</TableCell>
            <TableCell sx={{ width: 140, fontWeight: 700 }}>{t('report.columnMatricule')}</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {schoolClass.students.map((s, i) => (
            <TableRow key={`${s.matricule}-${i}`}>
              <TableCell sx={{ fontVariantNumeric: 'tabular-nums' }}>{i + 1}</TableCell>
              <TableCell>{`${s.lastName.toUpperCase()} ${s.firstName}`}</TableCell>
              <TableCell>{s.matricule}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </Box>
  )
}
