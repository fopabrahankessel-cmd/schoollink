import { Alert, Box, Button, IconButton, Paper, Stack, Typography } from '@mui/material'
import { useRef, useState, type DragEvent, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate, useSearchParams } from 'react-router'
import { errorMessage } from '../../api/errors'
import { fileProblem, IMPORT_PATH, importPath, REQUIRED_COLUMNS, type StudentImport } from '../../api/imports'
import { PATHS, type AcademicYear } from '../../api/structure'
import { useAuth } from '../../auth/useAuth'
import { PageHeader } from '../../components/PageHeader'
import { YearPicker } from '../../components/YearPicker'
import { formatFileSize } from '../../format'
import { useList } from '../../hooks/useList'
import { DownloadSimpleIcon, FileArrowUpIcon, FileTextIcon, UploadSimpleIcon, XIcon } from '../../icons'
import { tokens } from '../../theme'
import { ImportSteps } from './ImportSteps'

/** UC-A3 step 1: choose the year and the file; the server checks every row before anything is saved. */
export function ImportUploadPage() {
  const { t, i18n } = useTranslation()
  const { client } = useAuth()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const years = useList<AcademicYear>(PATHS.years)
  const [year, setYear] = useState(params.get('year') ?? '')
  const [file, setFile] = useState<File | null>(null)
  const [problem, setProblem] = useState<string | null>(null)
  const [error, setError] = useState<unknown>(null)
  const [sending, setSending] = useState(false)
  const [dragging, setDragging] = useState(false)
  const input = useRef<HTMLInputElement>(null)

  const choose = (chosen: File | undefined) => {
    setError(null)
    if (!chosen) return
    const why = fileProblem(chosen)
    setProblem(why)
    setFile(why ? null : chosen)
  }

  const drop = (e: DragEvent) => {
    e.preventDefault()
    setDragging(false)
    choose(e.dataTransfer.files[0])
  }

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    if (!file || !year) return
    const form = new FormData()
    form.append('academicYearId', year)
    form.append('file', file)
    setSending(true)
    setError(null)
    try {
      const created = await client.request<StudentImport>(IMPORT_PATH, { method: 'POST', body: form })
      navigate(importPath(created.id))
    } catch (err) {
      setError(err)
      setSending(false)
    }
  }

  return (
    <Stack>
      <PageHeader icon={FileArrowUpIcon} title={t('import.title')} description={t('import.intro')} />
      <ImportSteps active={0} />
      {years.error !== null && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {errorMessage(years.error, t)}
        </Alert>
      )}
      <Box sx={{ display: 'grid', gap: 3, gridTemplateColumns: { xs: '1fr', lg: 'minmax(0, 3fr) minmax(0, 2fr)' }, alignItems: 'start' }}>
        <Paper variant="outlined" component="form" onSubmit={submit} sx={{ p: { xs: 2, sm: 3 } }} noValidate>
          <Stack spacing={2.5}>
            <YearPicker years={years.items} value={year} onChange={setYear} />

            <Box
              onDragOver={(e) => {
                e.preventDefault()
                setDragging(true)
              }}
              onDragLeave={(e) => {
                // moving over a child is not leaving
                if (!e.currentTarget.contains(e.relatedTarget as Node | null)) setDragging(false)
              }}
              onDrop={drop}
              sx={{
                border: `1.5px dashed ${dragging ? tokens.brand : tokens.control}`,
                borderRadius: `${tokens.radius.card}px`,
                bgcolor: dragging ? tokens.brandTint : tokens.raised,
                px: 2,
                py: file ? 2 : 5,
                transition: `background-color 150ms ${tokens.easeOut}, border-color 150ms ${tokens.easeOut}`,
              }}
            >
              <input
                ref={input}
                type="file"
                accept=".csv,.xlsx,text/csv,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                aria-label={t('import.fileLabel')}
                tabIndex={-1} // the visible buttons open it
                onChange={(e) => {
                  choose(e.target.files?.[0])
                  e.target.value = '' // choosing the same file again still counts
                }}
                style={visuallyHidden}
              />
              {file ? (
                <Stack direction="row" sx={{ alignItems: 'center', gap: 1.5 }}>
                  <Box aria-hidden sx={{ ...tile, bgcolor: tokens.brandSoft, color: tokens.brandDark }}>
                    <FileTextIcon size={22} />
                  </Box>
                  <Box sx={{ minWidth: 0, flex: 1 }}>
                    <Typography sx={{ fontWeight: 600 }} noWrap title={file.name}>
                      {file.name}
                    </Typography>
                    <Typography variant="caption" color="text.secondary" component="p">
                      {formatFileSize(file.size, i18n.language)}
                    </Typography>
                  </Box>
                  <Button size="small" onClick={() => input.current?.click()} sx={{ flex: 'none' }}>
                    {t('import.replace')}
                  </Button>
                  <IconButton aria-label={t('import.remove')} onClick={() => setFile(null)} sx={{ flex: 'none' }}>
                    <XIcon size={18} />
                  </IconButton>
                </Stack>
              ) : (
                <Stack sx={{ alignItems: 'center', textAlign: 'center', gap: 1 }}>
                  <Box aria-hidden sx={{ ...tile, width: 48, height: 48, bgcolor: tokens.surface, color: tokens.brand, boxShadow: tokens.shadow.card }}>
                    <UploadSimpleIcon size={24} />
                  </Box>
                  <Typography sx={{ fontWeight: 600, mt: 0.5 }}>{t('import.dropTitle')}</Typography>
                  <Typography color="text.secondary" sx={{ fontSize: 13 }}>
                    {t('import.dropOr')}
                  </Typography>
                  <Button variant="outlined" onClick={() => input.current?.click()}>
                    {t('import.choose')}
                  </Button>
                  <Typography variant="caption" color="text.secondary" component="p" sx={{ mt: 0.5 }}>
                    {t('import.accepted')}
                  </Typography>
                </Stack>
              )}
            </Box>

            {problem && <Alert severity="error">{t(problem)}</Alert>}
            {error !== null && <Alert severity="error">{errorMessage(error, t)}</Alert>}

            <Stack direction={{ xs: 'column', sm: 'row' }} sx={{ gap: 2, alignItems: { sm: 'center' }, justifyContent: 'space-between' }}>
              <Typography color="text.secondary" sx={{ fontSize: 13 }}>
                {t('import.nothingYet')}
              </Typography>
              <Button
                type="submit"
                variant="contained"
                loading={sending}
                disabled={!file || !year}
                sx={{ flex: 'none' }}
              >
                {t('import.check')}
              </Button>
            </Stack>
          </Stack>
        </Paper>

        <Paper variant="outlined" component="aside" aria-label={t('import.prepareTitle')} sx={{ p: { xs: 2, sm: 3 } }}>
          <Typography variant="h6" component="h2">
            {t('import.prepareTitle')}
          </Typography>
          <Typography color="text.secondary" sx={{ mt: 0.5, fontSize: 14 }}>
            {t('import.templateHint')}
          </Typography>
          <Button
            component="a"
            href={TEMPLATE_HREF}
            download={t('import.templateFileName')}
            variant="outlined"
            startIcon={<DownloadSimpleIcon aria-hidden />}
            sx={{ mt: 1.5 }}
          >
            {t('import.template')}
          </Button>

          <Typography component="h3" sx={{ fontWeight: 600, fontSize: 14, mt: 3, mb: 1 }}>
            {t('import.requiredColumns')}
          </Typography>
          <Stack direction="row" sx={{ flexWrap: 'wrap', gap: 0.75 }}>
            {REQUIRED_COLUMNS.map((column) => (
              <Box
                key={column}
                component="code"
                sx={{
                  fontSize: 12.5,
                  px: 0.75,
                  py: 0.25,
                  borderRadius: `${tokens.radius.chip}px`,
                  bgcolor: tokens.pill.neutral.bg,
                  color: tokens.pill.neutral.ink,
                }}
              >
                {column}
              </Box>
            ))}
          </Stack>

          <Box component="ul" sx={{ m: 0, mt: 2.5, pl: 2.25, color: tokens.muted, fontSize: 14, '& li + li': { mt: 0.75 } }}>
            <li>{t('import.ruleHeaders')}</li>
            <li>{t('import.ruleClass')}</li>
            <li>{t('import.ruleBirth')}</li>
            <li>{t('import.ruleGuardian')}</li>
          </Box>
        </Paper>
      </Box>
    </Stack>
  )
}

// web/public/import-template.csv, documented in docs/IMPORT.md
const TEMPLATE_HREF = '/import-template.csv'

const tile = {
  width: 40,
  height: 40,
  flex: 'none',
  display: 'grid',
  placeItems: 'center',
  borderRadius: `${tokens.radius.control}px`,
} as const

const visuallyHidden = {
  position: 'absolute',
  width: 1,
  height: 1,
  overflow: 'hidden',
  clip: 'rect(0 0 0 0)',
  whiteSpace: 'nowrap',
} as const
