import {
  Alert,
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  FormControlLabel,
  Radio,
  RadioGroup,
  Stack,
  TextField,
  Typography,
} from '@mui/material'
import { useState, type FormEvent, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { errorMessage } from '../../api/errors'
import { needsNote, REASONS, takesNote, type Decision, type Meeting, type Reason } from '../../api/dm'

/**
 * The form inside each of the DM's dialogs: the fields, the server's refusal in its own words (NFR-12), and
 * one button. The dialog closes only once the server has accepted the request.
 */
function ActionForm({
  title,
  intro,
  submitLabel,
  canSubmit,
  danger = false,
  onSubmit,
  onClose,
  children,
}: {
  title: string
  intro?: string
  submitLabel: string
  canSubmit: boolean
  danger?: boolean
  onSubmit: () => Promise<void>
  onClose: () => void
  children: ReactNode
}) {
  const { t } = useTranslation()
  const [error, setError] = useState<unknown>(null)
  const [saving, setSaving] = useState(false)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setSaving(true)
    setError(null)
    try {
      await onSubmit()
    } catch (e) {
      setError(e)
    } finally {
      setSaving(false)
    }
  }

  return (
    <Box component="form" onSubmit={submit} noValidate>
        <DialogTitle>{title}</DialogTitle>
        <DialogContent>
          {intro && <DialogContentText sx={{ mb: 1 }}>{intro}</DialogContentText>}
          {error !== null && (
            <Alert severity="error" sx={{ my: 1 }}>
              {errorMessage(error, t)}
            </Alert>
          )}
          {children}
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose}>{t('crud.cancel')}</Button>
          <Button type="submit" variant="contained" color={danger ? 'error' : 'primary'} disabled={!canSubmit} loading={saving}>
            {submitLabel}
          </Button>
        </DialogActions>
    </Box>
  )
}

/** D31: by default the next school day at the start of school; or a day and time the DM picks. */
function MeetingFields({ meeting, onChange }: { meeting: Meeting | null; onChange: (meeting: Meeting | null) => void }) {
  const { t } = useTranslation()
  return (
    <Box sx={{ mt: 1 }}>
      <Typography variant="subtitle2" component="p">
        {t('dm.meeting')}
      </Typography>
      <RadioGroup
        value={meeting === null ? 'default' : 'chosen'}
        onChange={(e) => onChange(e.target.value === 'default' ? null : { meetingDate: '', meetingTime: '08:00' })}
      >
        <FormControlLabel value="default" control={<Radio />} label={t('dm.meetingDefault')} />
        <FormControlLabel value="chosen" control={<Radio />} label={t('dm.meetingChoose')} />
      </RadioGroup>
      {meeting !== null && (
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
          <TextField
            type="date"
            label={t('dm.meetingDate')}
            value={meeting.meetingDate}
            onChange={(e) => onChange({ ...meeting, meetingDate: e.target.value })}
            required
            slotProps={{ inputLabel: { shrink: true } }}
          />
          <TextField
            type="time"
            label={t('dm.meetingTime')}
            value={meeting.meetingTime}
            onChange={(e) => onChange({ ...meeting, meetingTime: e.target.value })}
            required
            slotProps={{ inputLabel: { shrink: true } }}
          />
        </Stack>
      )}
    </Box>
  )
}

const meetingComplete = (meeting: Meeting | null) => meeting === null || Boolean(meeting.meetingDate && meeting.meetingTime)

/** UC-D2, D30, D31: the DM's decision on one alert; summon takes a meeting, the others a note. */
export function DecisionDialog({
  decision,
  studentName,
  onSubmit,
  onClose,
}: {
  /** null: closed */
  decision: Decision | null
  studentName: string
  onSubmit: (note: string, meeting: Meeting | null) => Promise<void>
  onClose: () => void
}) {
  return (
    <Dialog open={decision !== null} onClose={onClose} fullWidth maxWidth="sm">
      {decision !== null && (
        <DecisionForm key={decision} decision={decision} studentName={studentName} onSubmit={onSubmit} onClose={onClose} />
      )}
    </Dialog>
  )
}

function DecisionForm({
  decision,
  studentName,
  onSubmit,
  onClose,
}: {
  decision: Decision
  studentName: string
  onSubmit: (note: string, meeting: Meeting | null) => Promise<void>
  onClose: () => void
}) {
  const { t } = useTranslation()
  const [note, setNote] = useState('')
  const [meeting, setMeeting] = useState<Meeting | null>(null)
  const summon = decision === 'SUMMON'
  const complete = (!needsNote(decision) || note.trim() !== '') && meetingComplete(meeting)
  return (
    <ActionForm
      title={summon ? t('dm.summonTitle', { name: studentName }) : t(`dm.decision.${decision}`)}
      intro={t(`dm.decisionIntro.${decision}`)}
      submitLabel={t(`dm.decision.${decision}`)}
      canSubmit={complete}
      onSubmit={() => onSubmit(note.trim(), meeting)}
      onClose={onClose}
    >
      {summon && <MeetingFields meeting={meeting} onChange={setMeeting} />}
      <TextField
        label={t(`dm.noteLabel.${decision}`)}
        value={note}
        onChange={(e) => setNote(e.target.value)}
        required={needsNote(decision)}
        multiline
        minRows={3}
        fullWidth
        autoFocus={!summon}
        helperText={summon ? t('dm.summonReasonHelp') : undefined}
        slotProps={{ htmlInput: { maxLength: summon ? 300 : 500 } }}
      />
    </ActionForm>
  )
}

/** UC-D3, D31: a summons on the DM's own initiative; the reason is required. */
export function SummonDialog({
  open,
  studentName,
  onSubmit,
  onClose,
}: {
  open: boolean
  studentName: string
  onSubmit: (reason: string, meeting: Meeting | null) => Promise<void>
  onClose: () => void
}) {
  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      {open && <SummonForm studentName={studentName} onSubmit={onSubmit} onClose={onClose} />}
    </Dialog>
  )
}

function SummonForm({
  studentName,
  onSubmit,
  onClose,
}: {
  studentName: string
  onSubmit: (reason: string, meeting: Meeting | null) => Promise<void>
  onClose: () => void
}) {
  const { t } = useTranslation()
  const [reason, setReason] = useState('')
  const [meeting, setMeeting] = useState<Meeting | null>(null)
  return (
    <ActionForm
      title={t('dm.summonTitle', { name: studentName })}
      intro={t('dm.decisionIntro.SUMMON')}
      submitLabel={t('dm.sendSummons')}
      canSubmit={reason.trim() !== '' && meetingComplete(meeting)}
      onSubmit={() => onSubmit(reason.trim(), meeting)}
      onClose={onClose}
    >
      <MeetingFields meeting={meeting} onChange={setMeeting} />
      <TextField
        label={t('dm.summonReason')}
        value={reason}
        onChange={(e) => setReason(e.target.value)}
        required
        multiline
        minRows={2}
        fullWidth
        helperText={t('dm.summonReasonHelp')}
        slotProps={{ htmlInput: { maxLength: 300 } }}
      />
    </ActionForm>
  )
}

/** BR-11, BR-12, D26: one reason for every selected record. An illness takes no note (NFR-10). */
export function JustifyDialog({
  count,
  onSubmit,
  onClose,
}: {
  /** 0: closed */
  count: number
  onSubmit: (reason: Reason, note: string) => Promise<void>
  onClose: () => void
}) {
  return (
    <Dialog open={count > 0} onClose={onClose} fullWidth maxWidth="sm">
      {count > 0 && <JustifyForm count={count} onSubmit={onSubmit} onClose={onClose} />}
    </Dialog>
  )
}

function JustifyForm({
  count,
  onSubmit,
  onClose,
}: {
  count: number
  onSubmit: (reason: Reason, note: string) => Promise<void>
  onClose: () => void
}) {
  const { t } = useTranslation()
  const [reason, setReason] = useState<Reason | ''>('')
  const [note, setNote] = useState('')
  return (
    <ActionForm
      title={t('dm.justifyTitle', { count })}
      intro={t('dm.justifyIntro')}
      submitLabel={t('dm.justify')}
      canSubmit={reason !== ''}
      onSubmit={() => onSubmit(reason as Reason, reason !== '' && takesNote(reason) ? note.trim() : '')}
      onClose={onClose}
    >
      <TextField
        select
        label={t('dm.reason')}
        value={reason}
        onChange={(e) => setReason(e.target.value as Reason)}
        required
        fullWidth
        slotProps={{ select: { native: true }, inputLabel: { shrink: true } }}
      >
        <option value="" disabled>
          {t('crud.choose')}
        </option>
        {REASONS.map((r) => (
          <option key={r} value={r}>
            {t(`dm.reasons.${r}`)}
          </option>
        ))}
      </TextField>
      {reason === 'ILLNESS' ? (
        <Typography color="text.secondary" sx={{ mt: 1 }}>
          {t('dm.illnessNoNote')}
        </Typography>
      ) : (
        <TextField
          label={t('dm.note')}
          value={note}
          onChange={(e) => setNote(e.target.value)}
          multiline
          minRows={2}
          fullWidth
          slotProps={{ htmlInput: { maxLength: 300 } }}
        />
      )}
    </ActionForm>
  )
}

/** BR-13, BR-14, D27: cancels a record entered by mistake; the reason is mandatory. */
export function CancelRecordDialog({
  what,
  onSubmit,
  onClose,
}: {
  /** The record as the history shows it; null: closed. */
  what: string | null
  onSubmit: (reason: string) => Promise<void>
  onClose: () => void
}) {
  return (
    <Dialog open={what !== null} onClose={onClose} fullWidth maxWidth="sm">
      {what !== null && <CancelForm what={what} onSubmit={onSubmit} onClose={onClose} />}
    </Dialog>
  )
}

function CancelForm({ what, onSubmit, onClose }: { what: string; onSubmit: (reason: string) => Promise<void>; onClose: () => void }) {
  const { t } = useTranslation()
  const [reason, setReason] = useState('')
  return (
    <ActionForm
      title={t('dm.cancelTitle')}
      intro={t('dm.cancelIntro', { what })}
      submitLabel={t('dm.cancelConfirm')}
      danger
      canSubmit={reason.trim() !== ''}
      onSubmit={() => onSubmit(reason.trim())}
      onClose={onClose}
    >
      <TextField
        label={t('dm.cancelReason')}
        value={reason}
        onChange={(e) => setReason(e.target.value)}
        required
        multiline
        minRows={2}
        fullWidth
        autoFocus
        slotProps={{ htmlInput: { maxLength: 300 } }}
      />
    </ActionForm>
  )
}
