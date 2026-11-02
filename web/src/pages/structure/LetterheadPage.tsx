import { Alert, Box, Button, IconButton, Snackbar, Stack, TextField, ToggleButton, Tooltip, Typography } from '@mui/material'
import { useEffect, useId, useState, type ChangeEvent, type FormEvent, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import type { ApiClient } from '../../api/client'
import { errorMessage } from '../../api/errors'
import {
  fetchLogo,
  LETTERHEAD_PATH,
  LOGO_TYPES,
  MAX_LINES,
  MAX_LOGO_BYTES,
  MAX_TEXT,
  removeLogo,
  saveLetterhead,
  uploadLogo,
  type Letterhead,
  type LetterheadLayout,
  type LetterheadLine,
  type LetterheadSize,
} from '../../api/letterhead'
import { useAuth } from '../../auth/useAuth'
import { PageHeader } from '../../components/PageHeader'
import { useResource } from '../../hooks/useResource'
import { ArrowDownIcon, ArrowUpIcon, ImageSquareIcon, PlusIcon, StampIcon, TextBIcon, TrashIcon, UploadSimpleIcon } from '../../icons'
import { hover, theme, tokens } from '../../theme'
import { LetterheadPreview } from './LetterheadPreview'

/**
 * UC-A7, D36: the admin sets the letterhead of the DM's reports: the lines of each language, their
 * order, weight and size, the layout and the logo. The preview above draws it as the PDF will.
 * The logo is saved as soon as it is chosen; the text with the Save button.
 */
export function LetterheadPage() {
  const { t } = useTranslation()
  const letterhead = useResource<Letterhead>(LETTERHEAD_PATH)
  return (
    <Stack>
      <PageHeader icon={StampIcon} title={t('letterhead.title')} description={t('letterhead.intro')} />
      {letterhead.error !== null && !letterhead.data && <Alert severity="error">{errorMessage(letterhead.error, t)}</Alert>}
      {letterhead.data && <Editor initial={letterhead.data} />}
    </Stack>
  )
}

type Language = 'french' | 'english'
/** A line being edited; the key keeps a row (and its focus) in place when lines move. */
type Row = LetterheadLine & { key: number }

let nextKey = 0
const toRows = (lines: LetterheadLine[]): Row[] => lines.map((line) => ({ ...line, key: nextKey++ }))
const toLines = (rows: Row[]): LetterheadLine[] => rows.map(({ text, bold, size }) => ({ text: text.trim(), bold, size }))
const snapshot = (layout: LetterheadLayout, french: Row[], english: Row[]) =>
  JSON.stringify([layout, toLines(french), toLines(english)])
const validText = (text: string) => text.trim().length > 0 && text.trim().length <= MAX_TEXT

function Editor({ initial }: { initial: Letterhead }) {
  const { t } = useTranslation()
  const { client } = useAuth()
  const [server, setServer] = useState(initial)
  const [layout, setLayout] = useState(initial.layout)
  const [rows, setRows] = useState({ french: toRows(initial.french), english: toRows(initial.english) })
  const [saved, setSaved] = useState(() => snapshot(initial.layout, rows.french, rows.english))
  const [showErrors, setShowErrors] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const [done, setDone] = useState(false)
  const logoUrl = useLogoUrl(client, server.hasLogo ? server.logoVersion : null)

  const dirty = snapshot(layout, rows.french, rows.english) !== saved
  const valid = [...rows.french, ...rows.english].every((row) => validText(row.text))

  const change = (language: Language, update: (rows: Row[]) => Row[]) =>
    setRows((current) => ({ ...current, [language]: update(current[language]) }))

  function revert() {
    const [savedLayout, french, english] = JSON.parse(saved) as [LetterheadLayout, LetterheadLine[], LetterheadLine[]]
    setLayout(savedLayout)
    setRows({ french: toRows(french), english: toRows(english) })
    setShowErrors(false)
  }

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (!valid) {
      setShowErrors(true)
      return
    }
    setSaving(true)
    setError(null)
    try {
      const result = await saveLetterhead(client, { layout, french: toLines(rows.french), english: toLines(rows.english) })
      setServer(result)
      const french = toRows(result.french)
      const english = toRows(result.english)
      setLayout(result.layout)
      setRows({ french, english })
      setSaved(snapshot(result.layout, french, english))
      setShowErrors(false)
      setDone(true)
    } catch (e) {
      setError(e)
    } finally {
      setSaving(false)
    }
  }

  return (
    <Stack component="form" onSubmit={submit} noValidate spacing={3.5}>
      <LetterheadPreview
        schoolName={server.schoolName}
        layout={layout}
        french={rows.french}
        english={rows.english}
        logoUrl={logoUrl}
      />

      <LayoutChoice value={layout} onChange={setLayout} />

      <Box
        sx={{
          display: 'grid',
          gap: 2,
          gridTemplateColumns: { xs: '1fr', md: '1fr 200px 1fr' },
          alignItems: 'start',
        }}
      >
        <LinesEditor
          language="french"
          rows={rows.french}
          printed={layout !== 'ENGLISH'}
          showErrors={showErrors}
          onChange={(update) => change('french', update)}
        />
        <LogoWell client={client} logoUrl={logoUrl} onChanged={setServer} />
        <LinesEditor
          language="english"
          rows={rows.english}
          printed={layout !== 'FRENCH'}
          showErrors={showErrors}
          onChange={(update) => change('english', update)}
        />
      </Box>

      <Box
        sx={{
          position: 'sticky',
          bottom: 0,
          zIndex: 2,
          mx: { xs: -2, sm: -3, lg: -5 },
          px: { xs: 2, sm: 3, lg: 5 },
          py: 1.5,
          bgcolor: tokens.surface,
          borderTop: `1px solid ${tokens.border}`,
          display: 'flex',
          flexWrap: 'wrap',
          alignItems: 'center',
          gap: 1.5,
        }}
      >
        <Box sx={{ flex: 1, minWidth: 180, display: 'flex', alignItems: 'center', gap: 1 }}>
          <Box
            aria-hidden
            sx={{ width: 8, height: 8, borderRadius: '50%', bgcolor: dirty ? tokens.amber : tokens.positive, transition: 'background-color 200ms ease' }}
          />
          <Typography variant="body2" sx={{ color: tokens.muted }}>
            {dirty ? t('letterhead.unsaved') : t('letterhead.upToDate')}
          </Typography>
        </Box>
        {error !== null && (
          <Alert severity="error" sx={{ flexBasis: '100%', order: -1 }}>
            {errorMessage(error, t)}
          </Alert>
        )}
        <Button onClick={revert} disabled={!dirty || saving}>
          {t('letterhead.revert')}
        </Button>
        <Button type="submit" variant="contained" disabled={!dirty} loading={saving}>
          {t('crud.save')}
        </Button>
      </Box>

      <Snackbar
        open={done}
        autoHideDuration={4000}
        onClose={() => setDone(false)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        <Alert severity="success" variant="filled" onClose={() => setDone(false)}>
          {t('letterhead.saved')}
        </Alert>
      </Snackbar>
    </Stack>
  )
}

/** The logo as an object URL, fetched again when its version changes; released when replaced. */
function useLogoUrl(client: ApiClient, version: string | null) {
  const [url, setUrl] = useState<{ version: string; url: string } | null>(null)
  useEffect(() => {
    if (version === null) return
    let live = true
    let created: string | null = null
    fetchLogo(client)
      .then((blob) => {
        if (!live || blob === null) return
        created = URL.createObjectURL(blob)
        setUrl({ version, url: created })
      })
      .catch(() => undefined) // the preview then shows no logo; the page stays usable
    return () => {
      live = false
      if (created !== null) URL.revokeObjectURL(created)
    }
  }, [client, version])
  return version !== null && url?.version === version ? url.url : null
}

const LAYOUTS: LetterheadLayout[] = ['BILINGUAL', 'FRENCH', 'ENGLISH']

/** Three cards, each with a small drawing of the header it gives. */
function LayoutChoice({ value, onChange }: { value: LetterheadLayout; onChange: (layout: LetterheadLayout) => void }) {
  const { t } = useTranslation()
  const id = useId()
  return (
    <Box component="fieldset" sx={{ border: 0, p: 0, m: 0, minWidth: 0 }}>
      <Typography component="legend" variant="subtitle1" sx={{ fontWeight: 600, mb: 1 }}>
        {t('letterhead.layout')}
      </Typography>
      <Box sx={{ display: 'grid', gap: 1.5, gridTemplateColumns: { xs: '1fr', sm: 'repeat(3, 1fr)' } }}>
        {LAYOUTS.map((layout) => {
          const checked = layout === value
          return (
            <Box
              key={layout}
              component="label"
              sx={{
                position: 'relative',
                display: 'flex',
                alignItems: 'center',
                gap: 1.5,
                p: 1.5,
                cursor: 'pointer',
                borderRadius: `${tokens.radius.card}px`,
                border: `1.5px solid ${checked ? tokens.brand : tokens.border}`,
                bgcolor: checked ? tokens.brandTint : tokens.surface,
                transition: `border-color 160ms ${tokens.easeOut}, background-color 160ms ${tokens.easeOut}`,
                [hover]: { '&:hover': { borderColor: checked ? tokens.brand : tokens.control } },
                '&:has(input:focus-visible)': { outline: `2px solid ${tokens.brand}`, outlineOffset: 2 },
              }}
            >
              <Box
                component="input"
                type="radio"
                name="layout"
                value={layout}
                checked={checked}
                onChange={() => onChange(layout)}
                aria-labelledby={`${id}-${layout}-name`}
                aria-describedby={`${id}-${layout}`}
                sx={{ position: 'absolute', inset: 0, width: '100%', height: '100%', m: 0, opacity: 0, cursor: 'pointer' }}
              />
              <Pictogram layout={layout} active={checked} />
              <Box sx={{ minWidth: 0 }}>
                <Typography id={`${id}-${layout}-name`} variant="body2" sx={{ fontWeight: 600, color: tokens.text }}>
                  {t(`letterhead.layouts.${layout}`)}
                </Typography>
                <Typography id={`${id}-${layout}`} variant="caption" sx={{ color: tokens.muted, display: 'block' }}>
                  {t(`letterhead.layoutHelp.${layout}`)}
                </Typography>
              </Box>
            </Box>
          )
        })}
      </Box>
    </Box>
  )
}

/** A miniature header: text strokes beside or under a round seal. */
function Pictogram({ layout, active }: { layout: LetterheadLayout; active: boolean }) {
  const ink = active ? tokens.brand : tokens.control
  const strokes = (widths: number[]) => (
    <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '3px' }}>
      {widths.map((w, i) => (
        <Box key={i} sx={{ width: w, height: i === 0 ? 3 : 2, borderRadius: 1, bgcolor: ink, transition: 'background-color 160ms ease' }} />
      ))}
    </Box>
  )
  const seal = (size: number) => (
    <Box sx={{ width: size, height: size, borderRadius: '50%', border: `2px solid ${ink}`, flex: 'none', transition: 'border-color 160ms ease' }} />
  )
  return (
    <Box
      aria-hidden
      sx={{
        width: 72,
        height: 48,
        flex: 'none',
        borderRadius: '6px',
        bgcolor: tokens.surface,
        border: `1px solid ${tokens.border}`,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        gap: '5px',
        flexDirection: layout === 'BILINGUAL' ? 'row' : 'column',
      }}
    >
      {layout === 'BILINGUAL' ? (
        <>
          {strokes([18, 14, 16])}
          {seal(14)}
          {strokes([18, 14, 16])}
        </>
      ) : (
        <>
          {seal(12)}
          {strokes([26, 18])}
        </>
      )}
    </Box>
  )
}

const SIZES: LetterheadSize[] = ['SMALL', 'NORMAL', 'LARGE']

interface LinesEditorProps {
  language: Language
  rows: Row[]
  /** false: this column is kept but left off the document by the layout. */
  printed: boolean
  showErrors: boolean
  onChange: (update: (rows: Row[]) => Row[]) => void
}

function LinesEditor({ language, rows, printed, showErrors, onChange }: LinesEditorProps) {
  const { t } = useTranslation()
  const name = t(`letterhead.column.${language}`)
  const set = (index: number, change: Partial<LetterheadLine>) =>
    onChange((current) => current.map((row, i) => (i === index ? { ...row, ...change } : row)))
  const move = (index: number, by: number) =>
    onChange((current) => {
      const next = [...current]
      ;[next[index], next[index + by]] = [next[index + by], next[index]]
      return next
    })

  return (
    <Box
      role="group"
      aria-label={name}
      sx={{
        p: { xs: 1.5, sm: 2 },
        borderRadius: `${tokens.radius.card}px`,
        border: `1px solid ${tokens.border}`,
        bgcolor: tokens.surface,
        boxShadow: tokens.shadow.card,
      }}
    >
      <Box sx={{ display: 'flex', alignItems: 'baseline', gap: 1, mb: 1.5 }}>
        <Typography component="h2" variant="subtitle1" sx={{ fontWeight: 600, flex: 1 }}>
          {name}
        </Typography>
        <Typography variant="caption" sx={{ color: tokens.muted, fontVariantNumeric: 'tabular-nums' }}>
          {t('letterhead.lineCount', { count: rows.length, max: MAX_LINES })}
        </Typography>
      </Box>
      {!printed && (
        <Alert severity="info" variant="outlined" sx={{ mb: 1.5, py: 0 }}>
          {t('letterhead.notPrinted')}
        </Alert>
      )}
      <Stack spacing={1} sx={{ opacity: printed ? 1 : 0.6, transition: 'opacity 200ms ease' }}>
        {rows.length === 0 && (
          <Typography variant="body2" sx={{ color: tokens.muted, py: 1 }}>
            {t('letterhead.noLines')}
          </Typography>
        )}
        {rows.map((row, i) => {
          const invalid = showErrors && !validText(row.text)
          return (
            <Box
              key={row.key}
              role="group"
              aria-label={t('letterhead.line', { n: i + 1 })}
              sx={{
                display: 'grid',
                gridTemplateColumns: 'auto 1fr',
                gap: 1,
                p: 1,
                borderRadius: `${tokens.radius.control}px`,
                bgcolor: tokens.raised,
                border: `1px solid ${tokens.rule}`,
              }}
            >
              <Box
                aria-hidden
                sx={{ width: 18, pt: 1.1, textAlign: 'right', fontSize: 12, fontWeight: 600, color: tokens.muted, fontVariantNumeric: 'tabular-nums' }}
              >
                {i + 1}
              </Box>
              <Stack spacing={0.75} sx={{ minWidth: 0 }}>
                <TextField
                  size="small"
                  placeholder={t('letterhead.text')}
                  value={row.text}
                  onChange={(e) => set(i, { text: e.target.value })}
                  error={invalid}
                  helperText={invalid ? t('letterhead.emptyLine') : undefined}
                  slotProps={{
                    htmlInput: {
                      'aria-label': t('letterhead.text'),
                      maxLength: MAX_TEXT,
                      style: { fontWeight: row.bold ? 700 : 400 },
                    },
                  }}
                />
                <Stack direction="row" spacing={0.25} sx={{ alignItems: 'center' }}>
                  <Tooltip title={t('letterhead.bold')}>
                    <ToggleButton
                      value="bold"
                      size="small"
                      selected={row.bold}
                      onChange={() => set(i, { bold: !row.bold })}
                      aria-label={t('letterhead.bold')}
                      sx={{ width: 36, height: 36 }}
                    >
                      <TextBIcon size={18} weight={row.bold ? 'bold' : 'regular'} />
                    </ToggleButton>
                  </Tooltip>
                  <TextField
                    select
                    size="small"
                    value={row.size}
                    onChange={(e) => set(i, { size: e.target.value as LetterheadSize })}
                    slotProps={{ select: { native: true }, htmlInput: { 'aria-label': t('letterhead.size') } }}
                    sx={{ width: { xs: 100, sm: 120 } }}
                  >
                    {SIZES.map((size) => (
                      <option key={size} value={size}>
                        {t(`letterhead.sizes.${size}`)}
                      </option>
                    ))}
                  </TextField>
                  <Box sx={{ flex: 1 }} />
                  <RowAction label={t('letterhead.moveUp')} disabled={i === 0} onClick={() => move(i, -1)}>
                    <ArrowUpIcon size={18} />
                  </RowAction>
                  <RowAction label={t('letterhead.moveDown')} disabled={i === rows.length - 1} onClick={() => move(i, 1)}>
                    <ArrowDownIcon size={18} />
                  </RowAction>
                  <RowAction label={t('letterhead.remove')} onClick={() => onChange((current) => current.filter((_, j) => j !== i))}>
                    <TrashIcon size={18} />
                  </RowAction>
                </Stack>
              </Stack>
            </Box>
          )
        })}
      </Stack>
      <Button
        startIcon={<PlusIcon size={18} />}
        disabled={rows.length >= MAX_LINES}
        onClick={() => onChange((current) => [...current, { text: '', bold: false, size: 'NORMAL', key: nextKey++ }])}
        sx={{ mt: 1.5 }}
      >
        {t('letterhead.addLine')}
      </Button>
    </Box>
  )
}

function RowAction({ label, disabled, onClick, children }: { label: string; disabled?: boolean; onClick: () => void; children: ReactNode }) {
  return (
    <Tooltip title={label}>
      <span>
        <IconButton size="small" aria-label={label} disabled={disabled} onClick={onClick} sx={{ width: 36, height: 36 }}>
          {children}
        </IconButton>
      </span>
    </Tooltip>
  )
}

/** The seal-shaped well between the two columns, where the school's emblem is set. */
function LogoWell({ client, logoUrl, onChanged }: { client: ApiClient; logoUrl: string | null; onChanged: (l: Letterhead) => void }) {
  const { t } = useTranslation()
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function run(action: () => Promise<Letterhead>) {
    setBusy(true)
    setError(null)
    try {
      onChanged(await action())
    } catch (e) {
      setError(errorMessage(e, t))
    } finally {
      setBusy(false)
    }
  }

  function chosen(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    event.target.value = '' // the same file can be chosen again
    if (!file) return
    // the server's checks, made here first so a wrong file is not sent (D36)
    if (!LOGO_TYPES.includes(file.type)) return setError(t('letterhead.logoType'))
    if (file.size > MAX_LOGO_BYTES) return setError(t('letterhead.logoTooLarge'))
    void run(() => uploadLogo(client, file))
  }

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', textAlign: 'center', gap: 1.25, pt: { md: 1 } }}>
      <Typography component="h2" variant="subtitle1" sx={{ fontWeight: 600 }}>
        {t('letterhead.logo')}
      </Typography>
      <Box
        sx={{
          width: 132,
          height: 132,
          borderRadius: '50%',
          display: 'grid',
          placeItems: 'center',
          overflow: 'hidden',
          bgcolor: tokens.surface,
          border: logoUrl ? `1px solid ${tokens.border}` : `2px dashed ${tokens.control}`,
          // a double ring, like an embossed seal
          boxShadow: `0 0 0 6px ${tokens.surface}, 0 0 0 7px ${tokens.border}`,
          opacity: busy ? 0.6 : 1,
          transition: 'opacity 150ms ease',
        }}
      >
        {logoUrl ? (
          <Box component="img" src={logoUrl} alt="" sx={{ width: '82%', height: '82%', objectFit: 'contain' }} />
        ) : (
          <ImageSquareIcon size={36} color={tokens.control} aria-hidden />
        )}
      </Box>
      <Button
        component="label"
        variant="outlined"
        size="small"
        startIcon={<UploadSimpleIcon size={16} />}
        loading={busy}
        sx={{ mt: 1 }}
      >
        {logoUrl ? t('letterhead.replaceLogo') : t('letterhead.chooseLogo')}
        <input type="file" hidden accept={LOGO_TYPES.join(',')} onChange={chosen} />
      </Button>
      {logoUrl && (
        <Button size="small" color="error" disabled={busy} onClick={() => void run(() => removeLogo(client))}>
          {t('letterhead.removeLogo')}
        </Button>
      )}
      <Typography variant="caption" sx={{ color: tokens.muted, maxWidth: 200 }}>
        {t('letterhead.logoHelp')}
      </Typography>
      {error && (
        <Alert severity="error" sx={{ textAlign: 'left', width: '100%', [theme.breakpoints.up('md')]: { width: 220 } }}>
          {error}
        </Alert>
      )}
    </Box>
  )
}
