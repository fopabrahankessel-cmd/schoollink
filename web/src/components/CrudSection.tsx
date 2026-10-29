import {
  Alert,
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from '@mui/material'
import { useState, type FormEvent, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { ApiError } from '../api/client'
import { errorMessage } from '../api/errors'
import { useAuth } from '../auth/useAuth'
import { useList } from '../hooks/useList'
import { tokens } from '../theme'

export interface Column<T> {
  label: string
  render: (item: T) => ReactNode
}

export interface Field {
  /** The request property, also used to place the server's field errors. */
  name: string
  label: string
  type: 'text' | 'date' | 'time' | 'number' | 'select'
  options?: { value: string; label: string }[]
  /** Value for a new item. */
  initial?: string
}

type Values = Record<string, string>

interface Props<T extends { id: string }> {
  title: string
  /** One item, for the dialog titles ("Add: Term"). */
  itemName: string
  /** The resource: POST here, PUT and DELETE at path/{id}. */
  path: string
  /** The list to show (path with filters); null shows `waitingText` instead. */
  listPath: string | null
  waitingText?: string
  emptyText: string
  columns: Column<T>[]
  fields: Field[]
  toValues: (item: T) => Values
  /** Values sent but not edited here, e.g. the selected parent. */
  fixed?: Record<string, string>
  selectedId?: string | null
  onSelect?: (item: T) => void
  /** After any change, e.g. to reload what depends on this list. */
  onChanged?: () => void
}

/**
 * A list with create, edit and delete (UC-A1): the server checks every rule and its messages,
 * already translated, are shown on the matching field or above the form.
 */
export function CrudSection<T extends { id: string }>(props: Props<T>) {
  const { t } = useTranslation()
  const { client } = useAuth()
  const list = useList<T>(props.listPath)
  const [editing, setEditing] = useState<{ item: T | null; values: Values } | null>(null)
  const [formError, setFormError] = useState<unknown>(null)
  const [saving, setSaving] = useState(false)
  const [deleting, setDeleting] = useState<T | null>(null)
  const [listError, setListError] = useState<unknown>(null)

  function open(item: T | null) {
    const initial = Object.fromEntries(props.fields.map((f) => [f.name, f.initial ?? '']))
    setEditing({ item, values: item ? props.toValues(item) : initial })
    setFormError(null)
  }

  async function save(event: FormEvent) {
    event.preventDefault()
    if (!editing) return
    setSaving(true)
    setFormError(null)
    const body: Record<string, unknown> = { ...props.fixed }
    for (const field of props.fields) {
      const raw = editing.values[field.name]?.trim() ?? ''
      body[field.name] = raw === '' ? null : field.type === 'number' ? Number(raw) : raw
    }
    try {
      if (editing.item) {
        await client.request(`${props.path}/${editing.item.id}`, { method: 'PUT', body })
      } else {
        await client.request(props.path, { method: 'POST', body })
      }
      setEditing(null)
      await list.reload()
      props.onChanged?.()
    } catch (e) {
      setFormError(e)
    } finally {
      setSaving(false)
    }
  }

  async function remove() {
    if (!deleting) return
    const item = deleting
    setDeleting(null)
    setListError(null)
    try {
      await client.request(`${props.path}/${item.id}`, { method: 'DELETE' })
      await list.reload()
      props.onChanged?.()
    } catch (e) {
      setListError(e)
    }
  }

  const apiError = formError instanceof ApiError ? formError : null
  const fieldNames = props.fields.map((f) => f.name)
  const unplaced = formError !== null && !fieldNames.some((name) => apiError?.messageFor(name))
  const shownError = listError ?? list.error

  return (
    <Paper variant="outlined" sx={{ p: 2.5 }} component="section" aria-label={props.title}>
      <Stack direction="row" sx={{ alignItems: 'center', justifyContent: 'space-between', mb: 1 }}>
        <Typography variant="h6" component="h2">
          {props.title}
        </Typography>
        <Button variant="outlined" size="small" onClick={() => open(null)} disabled={props.listPath === null}>
          {t('crud.add')}
        </Button>
      </Stack>
      {shownError !== null && (
        <Alert severity="error" sx={{ mb: 1 }}>
          {errorMessage(shownError, t)}
        </Alert>
      )}
      {props.listPath === null ? (
        <EmptyNote>{props.waitingText}</EmptyNote>
      ) : !list.loading && list.items.length === 0 ? (
        <EmptyNote>{props.emptyText}</EmptyNote>
      ) : (
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow>
                {props.columns.map((c) => (
                  <TableCell key={c.label}>{c.label}</TableCell>
                ))}
                <TableCell align="right">
                  <Box component="span" sx={visuallyHidden}>
                    {t('crud.actions')}
                  </Box>
                </TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {list.items.map((item) => (
                <TableRow
                  key={item.id}
                  hover={Boolean(props.onSelect)}
                  selected={props.selectedId === item.id}
                  aria-selected={props.onSelect ? props.selectedId === item.id : undefined}
                  onClick={props.onSelect ? () => props.onSelect?.(item) : undefined}
                  sx={props.onSelect ? { cursor: 'pointer' } : undefined}
                >
                  {props.columns.map((c) => (
                    <TableCell key={c.label}>{c.render(item)}</TableCell>
                  ))}
                  <TableCell align="right" sx={{ whiteSpace: 'nowrap' }} onClick={(e) => e.stopPropagation()}>
                    {/* quiet on every row; the red is kept for the confirmation */}
                    <Button size="small" color="inherit" onClick={() => open(item)}>
                      {t('crud.edit')}
                    </Button>
                    <Button size="small" sx={{ color: 'text.secondary' }} onClick={() => setDeleting(item)}>
                      {t('crud.delete')}
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}

      <Dialog open={editing !== null} onClose={() => setEditing(null)} fullWidth maxWidth="xs">
        <Box component="form" onSubmit={save} noValidate>
          <DialogTitle>{editing?.item ? t('crud.editTitle', { name: props.itemName }) : t('crud.addTitle', { name: props.itemName })}</DialogTitle>
          <DialogContent>
            {unplaced && (
              <Alert severity="error" sx={{ mt: 1 }}>
                {errorMessage(formError, t)}
              </Alert>
            )}
            {props.fields.map((field) => {
              const message = apiError?.messageFor(field.name)
              return (
                <TextField
                  key={field.name}
                  name={field.name}
                  label={field.label}
                  type={field.type === 'select' ? undefined : field.type}
                  select={field.type === 'select'}
                  required
                  value={editing?.values[field.name] ?? ''}
                  onChange={(e) =>
                    setEditing((current) =>
                      current && { ...current, values: { ...current.values, [field.name]: e.target.value } },
                    )
                  }
                  error={Boolean(message)}
                  helperText={message}
                  slotProps={{
                    inputLabel: { shrink: true },
                    ...(field.type === 'select' ? { select: { native: true } } : {}),
                  }}
                >
                  {field.type === 'select' && [
                    <option key="" value="" disabled>
                      {t('crud.choose')}
                    </option>,
                    ...(field.options ?? []).map((o) => (
                      <option key={o.value} value={o.value}>
                        {o.label}
                      </option>
                    )),
                  ]}
                </TextField>
              )
            })}
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setEditing(null)}>{t('crud.cancel')}</Button>
            <Button type="submit" variant="contained" loading={saving}>
              {t('crud.save')}
            </Button>
          </DialogActions>
        </Box>
      </Dialog>

      <Dialog open={deleting !== null} onClose={() => setDeleting(null)}>
        <DialogTitle>{t('crud.deleteTitle')}</DialogTitle>
        <DialogContent>
          <DialogContentText>{t('crud.deleteText')}</DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleting(null)}>{t('crud.cancel')}</Button>
          <Button color="error" variant="contained" onClick={remove}>
            {t('crud.delete')}
          </Button>
        </DialogActions>
      </Dialog>
    </Paper>
  )
}

// Pixel strings: in sx, a number like 1 for width/height means 100%.
const visuallyHidden = {
  border: 0,
  clip: 'rect(0 0 0 0)',
  height: '1px',
  margin: '-1px',
  overflow: 'hidden',
  padding: 0,
  position: 'absolute',
  whiteSpace: 'nowrap',
  width: '1px',
} as const

/** An empty section keeps its frame and says what to do in one sentence. */
function EmptyNote({ children }: { children: ReactNode }) {
  return (
    <Typography
      color="text.secondary"
      sx={{ border: `1px dashed ${tokens.control}`, borderRadius: `${tokens.radius.card}px`, px: 1.5, py: 1 }}
    >
      {children}
    </Typography>
  )
}
