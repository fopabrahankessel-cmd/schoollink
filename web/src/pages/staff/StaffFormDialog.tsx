import { Alert, Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, TextField } from '@mui/material'
import { useState, type ChangeEvent, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { ApiError } from '../../api/client'
import { errorMessage } from '../../api/errors'
import type { StaffMember, StaffRequest } from '../../api/staff'

const FIELDS = ['firstName', 'lastName', 'phone', 'email', 'language'] as const

/**
 * Creates or edits a staff member's identity (UC-A2). D15: a phone or an email is required, and
 * each identifies one account in the school; the server checks both and normalizes the phone.
 */
export function StaffFormDialog({
  open,
  staff,
  onSubmit,
  onClose,
}: {
  open: boolean
  /** null to create */
  staff: StaffMember | null
  onSubmit: (request: StaffRequest) => Promise<void>
  onClose: () => void
}) {
  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="xs">
      {/* remounted on each opening, so the fields start from the current values */}
      {open && <StaffForm staff={staff} onSubmit={onSubmit} onClose={onClose} />}
    </Dialog>
  )
}

function StaffForm({
  staff,
  onSubmit,
  onClose,
}: {
  staff: StaffMember | null
  onSubmit: (request: StaffRequest) => Promise<void>
  onClose: () => void
}) {
  const { t } = useTranslation()
  const [values, setValues] = useState({
    firstName: staff?.firstName ?? '',
    lastName: staff?.lastName ?? '',
    phone: staff?.phone ?? '',
    email: staff?.email ?? '',
    language: staff?.language ?? '',
  })
  const [error, setError] = useState<unknown>(null)
  const [saving, setSaving] = useState(false)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setSaving(true)
    setError(null)
    try {
      await onSubmit({
        firstName: values.firstName.trim(),
        lastName: values.lastName.trim(),
        phone: values.phone.trim() || null,
        email: values.email.trim() || null,
        language: values.language === 'FR' || values.language === 'EN' ? values.language : null,
      })
    } catch (e) {
      setError(e)
    } finally {
      setSaving(false)
    }
  }

  const apiError = error instanceof ApiError ? error : null
  const fieldError = (name: string) => apiError?.messageFor(name)
  const unplaced = error !== null && !FIELDS.some((f) => fieldError(f))
  const field = (name: (typeof FIELDS)[number]) => ({
    name,
    value: values[name],
    onChange: (e: ChangeEvent<HTMLInputElement>) => setValues((v) => ({ ...v, [name]: e.target.value })),
    error: Boolean(fieldError(name)),
  })

  return (
    <Box component="form" onSubmit={submit} noValidate>
      <DialogTitle>{staff ? t('staff.editTitle') : t('staff.addTitle')}</DialogTitle>
      <DialogContent>
        {unplaced && (
          <Alert severity="error" sx={{ mt: 1 }}>
            {errorMessage(error, t)}
          </Alert>
        )}
        <TextField {...field('firstName')} label={t('staff.firstName')} required helperText={fieldError('firstName')} />
        <TextField {...field('lastName')} label={t('staff.lastName')} required helperText={fieldError('lastName')} />
        <TextField
          {...field('phone')}
          label={t('staff.phone')}
          type="tel"
          autoComplete="off"
          helperText={fieldError('phone') ?? t('staff.phoneHint')}
        />
        <TextField
          {...field('email')}
          label={t('staff.email')}
          type="email"
          autoComplete="off"
          helperText={fieldError('email') ?? t('staff.contactHint')}
        />
        <TextField
          {...field('language')}
          select
          label={t('staff.language')}
          helperText={fieldError('language') ?? (staff ? undefined : t('staff.languageHint'))}
          slotProps={{ select: { native: true }, inputLabel: { shrink: true } }}
        >
          {!staff && <option value="">{t('staff.languageSchool')}</option>}
          <option value="FR">{t('language.fr')}</option>
          <option value="EN">{t('language.en')}</option>
        </TextField>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>{t('crud.cancel')}</Button>
        <Button type="submit" variant="contained" loading={saving}>
          {staff ? t('crud.save') : t('staff.create')}
        </Button>
      </DialogActions>
    </Box>
  )
}
