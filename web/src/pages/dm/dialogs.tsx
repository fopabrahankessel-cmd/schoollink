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
import {
  CATEGORIES,
  INCIDENT_DAYS_BACK,
  isoDate,
  MEASURES,
  needsNote,
  REASONS,
  takesNote,
  type Decision,
  type DisciplinaryCategory,
  type DisciplinaryMeasure,
  type IncidentRequest,
  type Meeting,
  type Reason,
} from '../../api/dm'

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
  onSubmit: (note: string, meeting: Meeting | null, kind: IncidentKind | null) => Promise<void>
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
  onSubmit: (note: string, meeting: Meeting | null, kind: IncidentKind | null) => Promise<void>
  onClose: () => void
}) {
  const { t } = useTranslation()
  const [note, setNote] = useState('')
  const [meeting, setMeeting] = useState<Meeting | null>(null)
  const [category, setCategory] = useState<DisciplinaryCategory | ''>('')
  const [measure, setMeasure] = useState<DisciplinaryMeasure>('NONE')
  const summon = decision === 'SUMMON'
  // D37: a disciplinary decision records an incident, so it takes its category and measure
  const disciplinary = decision === 'DISCIPLINARY'
  const complete =
    (!needsNote(decision) || note.trim() !== '') && meetingComplete(meeting) && (!disciplinary || category !== '')
  return (
    <ActionForm
      title={summon ? t('dm.summonTitle', { name: studentName }) : t(`dm.decision.${decision}`)}
      intro={t(`dm.decisionIntro.${decision}`)}
      submitLabel={t(`dm.decision.${decision}`)}
      canSubmit={complete}
      onSubmit={() => onSubmit(note.trim(), meeting, disciplinary && category !== '' ? { category, measure } : null)}
      onClose={onClose}
    >
      {summon && <MeetingFields meeting={meeting} onChange={setMeeting} />}
      {disciplinary && (
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ mt: 1, mb: 2 }}>
          <CategorySelect value={category} onChange={setCategory} />
          <MeasureSelect value={measure} onChange={setMeasure} />
        </Stack>
      )}
      <TextField
        label={t(`dm.noteLabel.${decision}`)}
        value={note}
        onChange={(e) => setNote(e.target.value)}
        required={needsNote(decision)}
        multiline
        minRows={3}
        fullWidth
        autoFocus={!summon && !disciplinary}
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
  intro,
  onSubmit,
  onClose,
}: {
  /** The record as the history shows it; null: closed. */
  what: string | null
  /** The explanation, when the default one (about the parents' correction) does not apply. */
  intro?: string
  onSubmit: (reason: string) => Promise<void>
  onClose: () => void
}) {
  return (
    <Dialog open={what !== null} onClose={onClose} fullWidth maxWidth="sm">
      {what !== null && <CancelForm what={what} intro={intro} onSubmit={onSubmit} onClose={onClose} />}
    </Dialog>
  )
}

function CancelForm({
  what,
  intro,
  onSubmit,
  onClose,
}: {
  what: string
  intro?: string
  onSubmit: (reason: string) => Promise<void>
  onClose: () => void
}) {
  const { t } = useTranslation()
  const [reason, setReason] = useState('')
  return (
    <ActionForm
      title={t('dm.cancelTitle')}
      intro={intro ?? t('dm.cancelIntro', { what })}
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

/** D37: what a disciplinary decision or an incident records besides its text. */
export interface IncidentKind {
  category: DisciplinaryCategory
  measure: DisciplinaryMeasure
}

function CategorySelect({ value, onChange }: { value: DisciplinaryCategory | ''; onChange: (c: DisciplinaryCategory) => void }) {
  const { t } = useTranslation()
  return (
    <TextField
      select
      label={t('dm.incidentCategory')}
      value={value}
      onChange={(e) => onChange(e.target.value as DisciplinaryCategory)}
      required
      fullWidth
      slotProps={{ select: { native: true }, inputLabel: { shrink: true } }}
    >
      <option value="" disabled>
        {t('crud.choose')}
      </option>
      {CATEGORIES.map((c) => (
        <option key={c} value={c}>
          {t(`dm.categories.${c}`)}
        </option>
      ))}
    </TextField>
  )
}

function MeasureSelect({ value, onChange }: { value: DisciplinaryMeasure; onChange: (m: DisciplinaryMeasure) => void }) {
  const { t } = useTranslation()
  return (
    <TextField
      select
      label={t('dm.incidentMeasure')}
      value={value}
      onChange={(e) => onChange(e.target.value as DisciplinaryMeasure)}
      fullWidth
      slotProps={{ select: { native: true }, inputLabel: { shrink: true } }}
    >
      {MEASURES.map((m) => (
        <option key={m} value={m}>
          {t(`dm.measures.${m}`)}
        </option>
      ))}
    </TextField>
  )
}

/**
 * UC-D5, D37: records an incident for one student: the kind, the day (today by default, at most 30
 * days back), the measure and what happened. Nothing is sent to the parents.
 */
export function RecordIncidentDialog({
  open,
  onSubmit,
  onClose,
}: {
  open: boolean
  onSubmit: (incident: IncidentRequest) => Promise<void>
  onClose: () => void
}) {
  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      {open && <IncidentForm onSubmit={onSubmit} onClose={onClose} />}
    </Dialog>
  )
}

function IncidentForm({ onSubmit, onClose }: { onSubmit: (incident: IncidentRequest) => Promise<void>; onClose: () => void }) {
  const { t } = useTranslation()
  const [today] = useState(() => new Date())
  const earliest = new Date(today)
  earliest.setDate(earliest.getDate() - INCIDENT_DAYS_BACK)
  const [category, setCategory] = useState<DisciplinaryCategory | ''>('')
  const [measure, setMeasure] = useState<DisciplinaryMeasure>('NONE')
  const [occurredOn, setOccurredOn] = useState(() => isoDate(today))
  const [description, setDescription] = useState('')
  const complete = category !== '' && occurredOn !== '' && description.trim() !== ''
  return (
    <ActionForm
      title={t('dm.recordIncident')}
      submitLabel={t('dm.recordIncident')}
      canSubmit={complete}
      onSubmit={() =>
        onSubmit({ category: category as DisciplinaryCategory, measure, occurredOn, description: description.trim() })
      }
      onClose={onClose}
    >
      <Stack spacing={2} sx={{ mt: 1 }}>
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
          <CategorySelect value={category} onChange={setCategory} />
          <TextField
            type="date"
            label={t('dm.incidentDate')}
            value={occurredOn}
            onChange={(e) => setOccurredOn(e.target.value)}
            required
            fullWidth
            helperText={t('dm.incidentDateHelp')}
            slotProps={{ inputLabel: { shrink: true }, htmlInput: { min: isoDate(earliest), max: isoDate(today) } }}
          />
        </Stack>
        <MeasureSelect value={measure} onChange={setMeasure} />
        <TextField
          label={t('dm.incidentDescription')}
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          required
          multiline
          minRows={3}
          fullWidth
          helperText={t('dm.incidentDescriptionHelp')}
          slotProps={{ htmlInput: { maxLength: 500 } }}
        />
      </Stack>
    </ActionForm>
  )
}

/** D37: closes an incident once, with an optional note. */
export function CloseIncidentDialog({
  what,
  onSubmit,
  onClose,
}: {
  /** The incident as the list shows it; null: closed. */
  what: string | null
  onSubmit: (note: string) => Promise<void>
  onClose: () => void
}) {
  return (
    <Dialog open={what !== null} onClose={onClose} fullWidth maxWidth="sm">
      {what !== null && <CloseIncidentForm what={what} onSubmit={onSubmit} onClose={onClose} />}
    </Dialog>
  )
}

function CloseIncidentForm({ what, onSubmit, onClose }: { what: string; onSubmit: (note: string) => Promise<void>; onClose: () => void }) {
  const { t } = useTranslation()
  const [note, setNote] = useState('')
  return (
    <ActionForm
      title={t('dm.closeIncidentTitle')}
      intro={t('dm.closeIncidentIntro', { what })}
      submitLabel={t('dm.closeIncident')}
      canSubmit
      onSubmit={() => onSubmit(note.trim())}
      onClose={onClose}
    >
      <TextField
        label={t('dm.closeNote')}
        value={note}
        onChange={(e) => setNote(e.target.value)}
        multiline
        minRows={2}
        fullWidth
        autoFocus
        slotProps={{ htmlInput: { maxLength: 500 } }}
      />
    </ActionForm>
  )
}
