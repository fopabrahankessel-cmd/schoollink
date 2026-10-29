import {
  Alert,
  Box,
  Button,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TablePagination,
  TableRow,
  Typography,
} from '@mui/material'
import { useEffect, useState, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useParams } from 'react-router'
import { errorMessage } from '../../api/errors'
import { errorsCsv, importPath, IMPORT_PATH, type ImportCommit, type ImportError, type StudentImport } from '../../api/imports'
import { PATHS, type AcademicYear } from '../../api/structure'
import { useAuth } from '../../auth/useAuth'
import { ConfirmDialog } from '../../components/ConfirmDialog'
import { PageHeader } from '../../components/PageHeader'
import { useList } from '../../hooks/useList'
import { CheckCircleIcon, DownloadSimpleIcon, FileArrowUpIcon, UploadSimpleIcon, WarningCircleIcon, type Icon } from '../../icons'
import { tokens } from '../../theme'
import { ImportSteps } from './ImportSteps'

const PAGE_SIZE = 50

/**
 * UC-A3 steps 2 and 3: the checked file with its errors, then the commit. D19: only a file
 * without errors commits, all or nothing and once; otherwise the admin corrects it and uploads again.
 */
export function ImportPreviewPage() {
  const { id = '' } = useParams()
  // a new state for each import, so nothing of the previous one shows meanwhile
  return <ImportPreview key={id} id={id} />
}

function ImportPreview({ id }: { id: string }) {
  const { t } = useTranslation()
  const { client } = useAuth()
  const years = useList<AcademicYear>(PATHS.years)
  const [preview, setPreview] = useState<StudentImport | null>(null)
  const [loadError, setLoadError] = useState<unknown>(null)
  const [confirming, setConfirming] = useState(false)
  const [committing, setCommitting] = useState(false)
  const [commitError, setCommitError] = useState<unknown>(null)
  const [result, setResult] = useState<ImportCommit | null>(null)

  useEffect(() => {
    let live = true // an answer for an import no longer shown is dropped
    client.request<StudentImport>(importPath(id)).then(
      (found) => live && setPreview(found),
      (err: unknown) => live && setLoadError(err),
    )
    return () => {
      live = false
    }
  }, [client, id])

  const commit = async () => {
    setCommitting(true)
    setCommitError(null)
    try {
      setResult(await client.request<ImportCommit>(`${importPath(id)}/commit`, { method: 'POST' }))
    } catch (err) {
      setCommitError(err)
    }
    setCommitting(false)
  }

  const year = years.items.find((y) => y.id === preview?.academicYearId)?.label ?? ''
  const uploadAgain = preview ? `${IMPORT_PATH}?year=${preview.academicYearId}` : IMPORT_PATH
  const done = result !== null || preview?.status === 'COMMITTED'

  return (
    <Stack>
      <PageHeader
        icon={FileArrowUpIcon}
        title={t('import.title')}
        description={preview ? t('import.fileInfo', { file: preview.fileName, year }) : t('import.intro')}
      />
      <ImportSteps active={done ? 3 : preview?.errorCount === 0 ? 2 : 1} />

      {loadError !== null && (
        <Stack spacing={2} sx={{ alignItems: 'flex-start' }}>
          <Alert severity="error">{errorMessage(loadError, t)}</Alert>
          <Button component={Link} to={IMPORT_PATH} variant="outlined">
            {t('import.another')}
          </Button>
        </Stack>
      )}

      {preview && !done && (
        <Box sx={{ display: 'grid', gap: { xs: 1, sm: 2 }, gridTemplateColumns: 'repeat(3, minmax(0, 1fr))', mb: 3 }}>
          <Count label={t('import.rowsRead')} value={preview.rowCount} />
          <Count label={t('import.rowsValid')} value={preview.validCount} tone="positive" />
          <Count label={t('import.rowsWithErrors')} value={preview.errorCount} tone={preview.errorCount > 0 ? 'negative' : undefined} />
        </Box>
      )}

      {preview?.status === 'EXPIRED' && (
        <Stack spacing={2} sx={{ alignItems: 'flex-start' }}>
          <Alert severity="info">{t('import.expired')}</Alert>
          <UploadAgain to={uploadAgain} />
        </Stack>
      )}

      {preview?.status === 'PREVIEW' && result === null && preview.errorCount > 0 && (
        <ErrorList preview={preview} uploadAgain={uploadAgain} />
      )}

      {preview?.status === 'PREVIEW' && result === null && preview.errorCount === 0 && (
        <Outcome icon={CheckCircleIcon} title={t('import.readyTitle', { count: preview.rowCount })} text={t('import.readyText')}>
          {commitError !== null && (
            <Alert severity="error" sx={{ mb: 2 }}>
              {errorMessage(commitError, t)}
            </Alert>
          )}
          <Stack direction={{ xs: 'column', sm: 'row' }} sx={{ gap: 1.5 }}>
            <Button variant="contained" loading={committing} onClick={() => setConfirming(true)}>
              {t('import.commit', { count: preview.rowCount })}
            </Button>
            {commitError !== null && <UploadAgain to={uploadAgain} />}
          </Stack>
        </Outcome>
      )}

      {done && (
        <Outcome
          icon={CheckCircleIcon}
          title={result ? t('import.doneTitle') : t('import.committedBefore')}
          text={result ? t('import.doneText') : undefined}
        >
          {result && (
            <Box component="dl" sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', sm: 'repeat(3, minmax(0, 1fr))' }, m: 0, mb: 3 }}>
              <Figure label={t('import.studentsCreated')} value={result.studentsCreated} />
              <Figure label={t('import.guardiansCreated')} value={result.guardiansCreated} />
              <Figure label={t('import.guardiansLinked')} value={result.guardiansLinked} />
            </Box>
          )}
          <Stack direction={{ xs: 'column', sm: 'row' }} sx={{ gap: 1.5 }}>
            <Button component={Link} to={IMPORT_PATH} variant="contained">
              {t('import.another')}
            </Button>
            <Button component={Link} to="/" variant="outlined">
              {t('import.backHome')}
            </Button>
          </Stack>
        </Outcome>
      )}

      <ConfirmDialog
        open={confirming}
        title={t('import.confirmTitle')}
        text={t('import.confirmText', { count: preview?.rowCount ?? 0, year })}
        confirmLabel={t('import.commit', { count: preview?.rowCount ?? 0 })}
        onConfirm={() => void commit()}
        onClose={() => setConfirming(false)}
      />
    </Stack>
  )
}

function ErrorList({ preview, uploadAgain }: { preview: StudentImport; uploadAgain: string }) {
  const { t } = useTranslation()
  const [page, setPage] = useState(0)
  const shown = preview.errors.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE)

  const download = () => {
    const csv = errorsCsv(preview.errors, [t('import.colRow'), t('import.colColumn'), t('import.colProblem')])
    const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }))
    const a = document.createElement('a')
    a.href = url
    a.download = t('import.errorsFileName', { file: preview.fileName.replace(/\.[^.]+$/, '') })
    a.click()
    URL.revokeObjectURL(url)
  }

  return (
    <Paper variant="outlined" component="section" aria-label={t('import.errorsTitle')} sx={{ overflow: 'hidden' }}>
      <Stack
        direction={{ xs: 'column', md: 'row' }}
        sx={{ gap: 2, p: { xs: 2, sm: 2.5 }, justifyContent: 'space-between', alignItems: { md: 'flex-start' } }}
      >
        <Stack direction="row" sx={{ gap: 1.5, alignItems: 'flex-start', minWidth: 0 }}>
          <Box aria-hidden sx={{ ...tile, bgcolor: tokens.pill.negative.bg, color: tokens.pill.negative.ink }}>
            <WarningCircleIcon size={22} />
          </Box>
          <Box sx={{ minWidth: 0 }}>
            <Stack direction="row" sx={{ alignItems: 'baseline', gap: 1.25, flexWrap: 'wrap' }}>
              <Typography variant="h6" component="h2">
                {t('import.errorsTitle')}
              </Typography>
              <Typography color="text.secondary" sx={{ fontSize: 13 }}>
                {t('import.problems', { count: preview.errors.length })}
              </Typography>
            </Stack>
            <Typography color="text.secondary" sx={{ mt: 0.25, maxWidth: 620 }}>
              {t('import.errorsIntro', { count: preview.errorCount })}
            </Typography>
          </Box>
        </Stack>
        <Stack direction={{ xs: 'column', sm: 'row' }} sx={{ gap: 1, flex: 'none' }}>
          <Button variant="outlined" startIcon={<DownloadSimpleIcon aria-hidden />} onClick={download}>
            {t('import.downloadErrors')}
          </Button>
          <UploadAgain to={uploadAgain} corrected />
        </Stack>
      </Stack>
      <TableContainer sx={{ borderTop: `1px solid ${tokens.border}` }}>
        <Table size="small" sx={{ minWidth: 560, '& .MuiTableCell-root:first-of-type': { pl: { xs: 2, sm: 2.5 } } }}>
          <TableHead>
            <TableRow>
              <TableCell sx={{ width: 88 }}>{t('import.colRow')}</TableCell>
              <TableCell sx={{ width: 200 }}>{t('import.colColumn')}</TableCell>
              <TableCell>{t('import.colProblem')}</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {shown.map((error, i) => (
              <ErrorRow
                key={`${error.row}-${error.column}-${error.code}-${i}`}
                error={error}
                first={i === 0 || shown[i - 1].row !== error.row}
                last={i === shown.length - 1 || shown[i + 1].row !== error.row}
              />
            ))}
          </TableBody>
        </Table>
      </TableContainer>
      {preview.errors.length > PAGE_SIZE && (
        <TablePagination
          component="div"
          count={preview.errors.length}
          page={page}
          onPageChange={(_, next) => setPage(next)}
          rowsPerPage={PAGE_SIZE}
          rowsPerPageOptions={[]}
          labelDisplayedRows={({ from, to, count }) => t('import.pageInfo', { from, to, count })}
          sx={{ borderTop: `1px solid ${tokens.border}` }}
        />
      )}
    </Paper>
  )
}

/** Errors come sorted by row; a row with several problems reads as one group, its number shown once. */
function ErrorRow({ error, first, last }: { error: ImportError; first: boolean; last: boolean }) {
  return (
    <TableRow sx={last ? undefined : { '& > td': { borderBottomColor: 'transparent' } }}>
      <TableCell sx={{ fontVariantNumeric: 'tabular-nums', fontWeight: 600, color: first ? tokens.text : 'transparent' }}>
        {error.row}
      </TableCell>
      <TableCell>
        {error.column && (
          <Box
            component="code"
            sx={{
              fontSize: 12.5,
              px: 0.75,
              py: 0.25,
              borderRadius: `${tokens.radius.chip}px`,
              bgcolor: tokens.pill.neutral.bg,
              color: tokens.pill.neutral.ink,
              wordBreak: 'break-all',
            }}
          >
            {error.column}
          </Box>
        )}
      </TableCell>
      <TableCell>{error.message}</TableCell>
    </TableRow>
  )
}

/** Back to step 1 for the same year: with a corrected file, or the same one when the school changed. */
function UploadAgain({ to, corrected = false }: { to: string; corrected?: boolean }) {
  const { t } = useTranslation()
  return (
    <Button component={Link} to={to} variant="contained" startIcon={<UploadSimpleIcon aria-hidden />}>
      {corrected ? t('import.uploadAgain') : t('import.uploadFresh')}
    </Button>
  )
}

function Count({ label, value, tone }: { label: string; value: number; tone?: 'positive' | 'negative' }) {
  return (
    <Paper variant="outlined" sx={{ p: { xs: 1.5, sm: 2 } }}>
      <Typography color="text.secondary" sx={{ fontWeight: 600, fontSize: { xs: 12, sm: 13 }, lineHeight: 1.3 }}>
        {label}
      </Typography>
      <Typography
        component="p"
        sx={{
          fontSize: 28,
          fontWeight: 700,
          lineHeight: 1.2,
          letterSpacing: '-0.02em',
          fontVariantNumeric: 'tabular-nums',
          color: tone === 'negative' ? tokens.negative : tone === 'positive' ? tokens.positive : tokens.text,
        }}
      >
        {value}
      </Typography>
    </Paper>
  )
}

function Outcome({ icon: OutcomeIcon, title, text, children }: { icon: Icon; title: string; text?: string; children: ReactNode }) {
  return (
    <Paper variant="outlined" component="section" aria-label={title} sx={{ p: { xs: 2, sm: 3 }, maxWidth: 880 }}>
      <Stack direction="row" sx={{ gap: 1.5, alignItems: 'flex-start', mb: 2.5 }}>
        <Box aria-hidden sx={{ ...tile, bgcolor: tokens.pill.positive.bg, color: tokens.pill.positive.ink }}>
          <OutcomeIcon size={22} weight="fill" />
        </Box>
        <Box>
          <Typography variant="h6" component="h2">
            {title}
          </Typography>
          {text && (
            <Typography color="text.secondary" sx={{ mt: 0.25, maxWidth: 620 }}>
              {text}
            </Typography>
          )}
        </Box>
      </Stack>
      {children}
    </Paper>
  )
}

function Figure({ label, value }: { label: string; value: number }) {
  return (
    <Box sx={{ bgcolor: tokens.raised, borderRadius: `${tokens.radius.control}px`, p: 2 }}>
      <Typography component="dt" color="text.secondary" sx={{ fontSize: 13, fontWeight: 600 }}>
        {label}
      </Typography>
      <Typography component="dd" sx={{ m: 0, fontSize: 24, fontWeight: 700, fontVariantNumeric: 'tabular-nums' }}>
        {value}
      </Typography>
    </Box>
  )
}

const tile = {
  width: 40,
  height: 40,
  flex: 'none',
  display: 'grid',
  placeItems: 'center',
  borderRadius: `${tokens.radius.control}px`,
} as const
