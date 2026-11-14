import {
  Alert,
  Box,
  Button,
  Checkbox,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  FormControlLabel,
  Radio,
  Stack,
  TextField,
  Typography,
} from '@mui/material'
import { useState, type FormEvent, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { ApiError } from '../../api/client'
import { errorMessage } from '../../api/errors'
import { PATHS, query, type AcademicYear, type SchoolClass } from '../../api/structure'
import {
  listName,
  RELATIONS,
  type GuardianRequest,
  type GuardianUpdate,
  type Language,
  type NewStudent,
  type Relation,
  type Sex,
  type Student,
  type StudentDetails,
  type StudentGuardian,
  type StudentRow,
} from '../../api/students'
import { YearPicker } from '../../components/YearPicker'
import { useList } from '../../hooks/useList'
import { PlusIcon, TrashIcon } from '../../icons'
import { tokens } from '../../theme'

const native = { select: { native: true }, inputLabel: { shrink: true } } as const

/** A dialog whose form is remounted on each opening, so the fields start from the current values. */
function FormDialog({ open, onClose, wide = false, children }: { open: boolean; onClose: () => void; wide?: boolean; children: ReactNode }) {
  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth={wide ? 'sm' : 'xs'}>
      {open && children}
    </Dialog>
  )
}

/** Runs a submit, keeping the refusal for the form to show. */
function useSubmit() {
  const [error, setError] = useState<unknown>(null)
  const [saving, setSaving] = useState(false)
  async function run(event: FormEvent, action: () => Promise<void>) {
    event.preventDefault()
    setSaving(true)
    setError(null)
    try {
      await action()
    } catch (e) {
      setError(e)
    } finally {
      setSaving(false)
    }
  }
  const fieldError = (name: string) => (error instanceof ApiError ? error.messageFor(name) : undefined)
  return { error, saving, run, fieldError }
}

/** The server's refusal, unless a field already shows it. */
function Refusal({ error, placed = false }: { error: unknown; placed?: boolean }) {
  const { t } = useTranslation()
  if (error === null || placed) return null
  return (
    <Alert severity="error" sx={{ mb: 1 }}>
      {errorMessage(error, t)}
    </Alert>
  )
}

/** A year, then one of its classes: classes are per academic year (UC-A1). */
function ClassFields({ classId, onChange, label, exclude }: { classId: string; onChange: (id: string, name: string) => void; label: string; exclude?: string }) {
  const { t } = useTranslation()
  const years = useList<AcademicYear>(PATHS.years)
  const [yearId, setYearId] = useState('')
  const classes = useList<SchoolClass>(yearId ? PATHS.classes + query({ academicYearId: yearId }) : null)
  return (
    <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ mt: 1.5 }}>
      <YearPicker
        years={years.items}
        value={yearId}
        onChange={(id) => {
          setYearId(id)
          onChange('', '')
        }}
      />
      <TextField
        select
        label={label}
        value={classId}
        onChange={(e) => onChange(e.target.value, classes.items.find((c) => c.id === e.target.value)?.name ?? '')}
        margin="none"
        required sx={{ minWidth: 180 }} slotProps={native}>
        <option value="" disabled>
          {t('crud.choose')}
        </option>
        {classes.items
          .filter((c) => c.id !== exclude)
          .map((c) => (
            <option key={c.id} value={c.id}>
              {c.name}
            </option>
          ))}
      </TextField>
    </Stack>
  )
}

interface DetailsValues {
  matricule: string
  lastName: string
  firstName: string
  sex: Sex
  birthYear: string
}

const detailsOf = (v: DetailsValues): StudentDetails => ({
  matricule: v.matricule.trim(),
  lastName: v.lastName.trim(),
  firstName: v.firstName.trim(),
  sex: v.sex,
  birthYear: Number(v.birthYear),
})

/** NFR-10: the birth year only, never the full date. */
function DetailsFields({ values, set, fieldError }: { values: DetailsValues; set: (patch: Partial<DetailsValues>) => void; fieldError: (name: string) => string | undefined }) {
  const { t } = useTranslation()
  return (
    <>
      <TextField
        label={t('roster.matricule')}
        value={values.matricule}
        onChange={(e) => set({ matricule: e.target.value })}
        required
        error={Boolean(fieldError('matricule'))}
        helperText={fieldError('matricule')}
      />
      <TextField label={t('roster.lastName')} value={values.lastName} onChange={(e) => set({ lastName: e.target.value })} required error={Boolean(fieldError('lastName'))} helperText={fieldError('lastName')} />
      <TextField label={t('roster.firstName')} value={values.firstName} onChange={(e) => set({ firstName: e.target.value })} required error={Boolean(fieldError('firstName'))} helperText={fieldError('firstName')} />
      <Stack direction="row" spacing={2}>
        <TextField select label={t('roster.sex')} value={values.sex} onChange={(e) => set({ sex: e.target.value as Sex })} slotProps={native} sx={{ flex: 1 }}>
          <option value="F">{t('roster.sexValue.F')}</option>
          <option value="M">{t('roster.sexValue.M')}</option>
        </TextField>
        <TextField
          label={t('roster.birthYear')}
          value={values.birthYear}
          onChange={(e) => set({ birthYear: e.target.value })}
          required
          error={Boolean(fieldError('birthYear'))}
          helperText={fieldError('birthYear')}
          slotProps={{ htmlInput: { inputMode: 'numeric', maxLength: 4 } }}
          sx={{ flex: 1 }}
        />
      </Stack>
    </>
  )
}

interface GuardianValues {
  phone: string
  name: string
  relation: Relation
  language: '' | Language
}

const emptyGuardian: GuardianValues = { phone: '', name: '', relation: 'MOTHER', language: '' }

const guardianOf = (v: GuardianValues, primary: boolean): GuardianRequest => ({
  phone: v.phone.trim(),
  name: v.name.trim(),
  relation: v.relation,
  language: v.language === '' ? null : v.language,
  primary,
})

function GuardianFields({ values, set, legend, schoolLanguage = true }: { values: GuardianValues; set: (patch: Partial<GuardianValues>) => void; legend: string; schoolLanguage?: boolean }) {
  const { t } = useTranslation()
  return (
    <Box component="fieldset" sx={{ border: 0, p: 0, m: 0, minWidth: 0 }}>
      <Typography component="legend" variant="subtitle2" sx={{ p: 0, mb: 0.5 }}>
        {legend}
      </Typography>
      <TextField
        label={t('roster.phone')}
        value={values.phone}
        onChange={(e) => set({ phone: e.target.value })}
        type="tel"
        autoComplete="off"
        required
        helperText={schoolLanguage ? t('roster.phoneHint') : undefined}
        slotProps={{ htmlInput: { 'aria-label': `${legend} : ${t('roster.phone')}` } }}
      />
      <TextField
        label={t('roster.guardianName')}
        value={values.name}
        onChange={(e) => set({ name: e.target.value })}
        required
        slotProps={{ htmlInput: { 'aria-label': `${legend} : ${t('roster.guardianName')}` } }}
      />
      <Stack direction="row" spacing={2}>
        <TextField select label={t('roster.relation')} value={values.relation} onChange={(e) => set({ relation: e.target.value as Relation })} slotProps={native} sx={{ flex: 1 }}>
          {RELATIONS.map((r) => (
            <option key={r} value={r}>
              {t(`roster.relationValue.${r}`)}
            </option>
          ))}
        </TextField>
        <TextField select label={t('roster.language')} value={values.language} onChange={(e) => set({ language: e.target.value as GuardianValues['language'] })} slotProps={native} sx={{ flex: 1 }}>
          {schoolLanguage && <option value="">{t('roster.languageSchool')}</option>}
          <option value="FR">{t('language.fr')}</option>
          <option value="EN">{t('language.en')}</option>
        </TextField>
      </Stack>
    </Box>
  )
}

/** D43: one student who arrives during the year, with one or two guardians; the first is the primary. */
export function NewStudentDialog({ open, classId, onSubmit, onClose }: { open: boolean; classId: string; onSubmit: (request: NewStudent) => Promise<void>; onClose: () => void }) {
  return (
    <FormDialog open={open} onClose={onClose} wide>
      <NewStudentForm classId={classId} onSubmit={onSubmit} onClose={onClose} />
    </FormDialog>
  )
}

function NewStudentForm({ classId: initialClass, onSubmit, onClose }: { classId: string; onSubmit: (request: NewStudent) => Promise<void>; onClose: () => void }) {
  const { t } = useTranslation()
  const { error, saving, run, fieldError } = useSubmit()
  const [details, setDetails] = useState<DetailsValues>({ matricule: '', lastName: '', firstName: '', sex: 'F', birthYear: '' })
  const [classId, setClassId] = useState(initialClass)
  const [guardians, setGuardians] = useState<GuardianValues[]>([emptyGuardian])
  const setGuardian = (index: number, patch: Partial<GuardianValues>) => setGuardians((list) => list.map((g, i) => (i === index ? { ...g, ...patch } : g)))
  const placed = ['matricule', 'lastName', 'firstName', 'birthYear'].some((f) => fieldError(f))

  return (
    <Box
      component="form"
      noValidate
      onSubmit={(e) => run(e, () => onSubmit({ ...detailsOf(details), classId, guardians: guardians.map((g, i) => guardianOf(g, i === 0)) }))}
    >
      <DialogTitle>{t('roster.addTitle')}</DialogTitle>
      <DialogContent>
        <Refusal error={error} placed={placed} />
        <DetailsFields values={details} set={(patch) => setDetails((v) => ({ ...v, ...patch }))} fieldError={fieldError} />
        {/* the class chosen on the list is kept; another one is picked by year */}
        {initialClass === '' && <ClassFields classId={classId} onChange={setClassId} label={t('roster.class')} />}
        <Stack spacing={2} sx={{ mt: 2.5, pt: 2, borderTop: `1px solid ${tokens.border}` }}>
          {guardians.map((g, i) => (
            <Box key={i}>
              <GuardianFields values={g} set={(patch) => setGuardian(i, patch)} legend={i === 0 ? t('roster.primaryGuardian') : t('roster.secondGuardian')} />
              {i > 0 && (
                <Button size="small" color="inherit" startIcon={<TrashIcon aria-hidden />} onClick={() => setGuardians((list) => list.filter((_, n) => n !== i))}>
                  {t('roster.dropSecondGuardian')}
                </Button>
              )}
            </Box>
          ))}
          {guardians.length < 2 && (
            <Box>
              <Button size="small" startIcon={<PlusIcon aria-hidden />} onClick={() => setGuardians((list) => [...list, emptyGuardian])}>
                {t('roster.addSecondGuardian')}
              </Button>
            </Box>
          )}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>{t('crud.cancel')}</Button>
        <Button type="submit" variant="contained" loading={saving} disabled={classId === ''}>
          {t('roster.create')}
        </Button>
      </DialogActions>
    </Box>
  )
}

export function StudentDetailsDialog({ open, student, onSubmit, onClose }: { open: boolean; student: Student; onSubmit: (request: StudentDetails) => Promise<void>; onClose: () => void }) {
  return (
    <FormDialog open={open} onClose={onClose}>
      <StudentDetailsForm student={student} onSubmit={onSubmit} onClose={onClose} />
    </FormDialog>
  )
}

function StudentDetailsForm({ student, onSubmit, onClose }: { student: Student; onSubmit: (request: StudentDetails) => Promise<void>; onClose: () => void }) {
  const { t } = useTranslation()
  const { error, saving, run, fieldError } = useSubmit()
  const [details, setDetails] = useState<DetailsValues>({
    matricule: student.matricule,
    lastName: student.lastName,
    firstName: student.firstName,
    sex: student.sex,
    birthYear: String(student.birthYear),
  })
  const placed = ['matricule', 'lastName', 'firstName', 'birthYear'].some((f) => fieldError(f))
  return (
    <Box component="form" noValidate onSubmit={(e) => run(e, () => onSubmit(detailsOf(details)))}>
      <DialogTitle>{t('roster.editTitle')}</DialogTitle>
      <DialogContent>
        <Refusal error={error} placed={placed} />
        <DetailsFields values={details} set={(patch) => setDetails((v) => ({ ...v, ...patch }))} fieldError={fieldError} />
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>{t('crud.cancel')}</Button>
        <Button type="submit" variant="contained" loading={saving}>
          {t('crud.save')}
        </Button>
      </DialogActions>
    </Box>
  )
}

/** Changes the class of one student, or brings back one who had left, into a class of any year. */
export function ClassDialog({ open, title, text, confirmLabel, exclude, onSubmit, onClose }: { open: boolean; title: string; text: string; confirmLabel: string; exclude?: string; onSubmit: (classId: string) => Promise<void>; onClose: () => void }) {
  return (
    <FormDialog open={open} onClose={onClose} wide>
      <ClassForm title={title} text={text} confirmLabel={confirmLabel} exclude={exclude} onSubmit={onSubmit} onClose={onClose} />
    </FormDialog>
  )
}

function ClassForm({ title, text, confirmLabel, exclude, onSubmit, onClose }: { title: string; text: string; confirmLabel: string; exclude?: string; onSubmit: (classId: string) => Promise<void>; onClose: () => void }) {
  const { t } = useTranslation()
  const { error, saving, run } = useSubmit()
  const [classId, setClassId] = useState('')
  return (
    <Box component="form" noValidate onSubmit={(e) => run(e, () => onSubmit(classId))}>
      <DialogTitle>{title}</DialogTitle>
      <DialogContent>
        <Refusal error={error} />
        <DialogContentText>{text}</DialogContentText>
        <ClassFields classId={classId} onChange={setClassId} label={t('roster.newClass')} exclude={exclude} />
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>{t('crud.cancel')}</Button>
        <Button type="submit" variant="contained" loading={saving} disabled={classId === ''}>
          {confirmLabel}
        </Button>
      </DialogActions>
    </Box>
  )
}

export interface TargetClass {
  id: string
  name: string
}

/**
 * The end of the year: the students of a class go to their class of the next year in one step.
 * All are ticked; the admin unticks those who repeat the year or leave, who stay where they are.
 */
export function MoveClassDialog({ open, className, classId, students, onSubmit, onClose }: { open: boolean; className: string; classId: string; students: StudentRow[]; onSubmit: (studentIds: string[], target: TargetClass) => Promise<void>; onClose: () => void }) {
  return (
    <FormDialog open={open} onClose={onClose} wide>
      <MoveClassForm className={className} classId={classId} students={students} onSubmit={onSubmit} onClose={onClose} />
    </FormDialog>
  )
}

function MoveClassForm({ className, classId: from, students, onSubmit, onClose }: { className: string; classId: string; students: StudentRow[]; onSubmit: (studentIds: string[], target: TargetClass) => Promise<void>; onClose: () => void }) {
  const { t } = useTranslation()
  const { error, saving, run } = useSubmit()
  const [target, setTarget] = useState<TargetClass>({ id: '', name: '' })
  const [staying, setStaying] = useState<Set<string>>(new Set())
  const moving = students.filter((s) => !staying.has(s.id))
  const toggle = (id: string) =>
    setStaying((current) => {
      const next = new Set(current)
      if (!next.delete(id)) next.add(id)
      return next
    })
  return (
    <Box component="form" noValidate onSubmit={(e) => run(e, () => onSubmit(moving.map((s) => s.id), target))}>
      <DialogTitle>{t('roster.moveTitle', { name: className })}</DialogTitle>
      <DialogContent>
        <Refusal error={error} />
        <DialogContentText>{t('roster.moveIntro')}</DialogContentText>
        <ClassFields classId={target.id} onChange={(id, name) => setTarget({ id, name })} label={t('roster.moveTo')} exclude={from} />
        <Box component="ul" aria-label={t('roster.moveList')} sx={{ listStyle: 'none', p: 0, m: 0, mt: 2, maxHeight: 280, overflowY: 'auto', border: `1px solid ${tokens.border}`, borderRadius: 1 }}>
          {students.map((s) => (
            <Box component="li" key={s.id} sx={{ px: 1.5, '& + li': { borderTop: `1px solid ${tokens.border}` } }}>
              <FormControlLabel
                control={<Checkbox size="small" checked={!staying.has(s.id)} onChange={() => toggle(s.id)} />}
                label={
                  <span>
                    {listName(s)}{' '}
                    <Typography component="span" variant="caption" color="text.secondary">
                      {s.matricule}
                    </Typography>
                  </span>
                }
              />
            </Box>
          ))}
        </Box>
        {staying.size > 0 && (
          <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
            {t('roster.moveStaying', { count: staying.size, name: className })}
          </Typography>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>{t('crud.cancel')}</Button>
        <Button type="submit" variant="contained" loading={saving} disabled={target.id === '' || moving.length === 0}>
          {t('roster.moveSubmit', { count: moving.length })}
        </Button>
      </DialogActions>
    </Box>
  )
}

/** From the leaving date the student is in no roll call; the history stays (D43). */
export function LeaveDialog({ open, name, today, onSubmit, onClose }: { open: boolean; name: string; today: string; onSubmit: (date: string) => Promise<void>; onClose: () => void }) {
  return (
    <FormDialog open={open} onClose={onClose}>
      <LeaveForm name={name} today={today} onSubmit={onSubmit} onClose={onClose} />
    </FormDialog>
  )
}

function LeaveForm({ name, today, onSubmit, onClose }: { name: string; today: string; onSubmit: (date: string) => Promise<void>; onClose: () => void }) {
  const { t } = useTranslation()
  const { error, saving, run, fieldError } = useSubmit()
  const [date, setDate] = useState(today)
  return (
    <Box component="form" noValidate onSubmit={(e) => run(e, () => onSubmit(date))}>
      <DialogTitle>{t('roster.leaveTitle', { name })}</DialogTitle>
      <DialogContent>
        <Refusal error={error} placed={Boolean(fieldError('date'))} />
        <DialogContentText>{t('roster.leaveText')}</DialogContentText>
        <TextField
          label={t('roster.leaveDate')}
          type="date"
          value={date}
          onChange={(e) => setDate(e.target.value)}
          required
          error={Boolean(fieldError('date'))}
          helperText={fieldError('date')}
          slotProps={{ inputLabel: { shrink: true }, htmlInput: { max: today } }}
          sx={{ mt: 2 }}
        />
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>{t('crud.cancel')}</Button>
        <Button type="submit" variant="contained" color="error" loading={saving} disabled={date === ''}>
          {t('roster.leaveConfirm')}
        </Button>
      </DialogActions>
    </Box>
  )
}

export function AddGuardianDialog({ open, onSubmit, onClose }: { open: boolean; onSubmit: (request: GuardianRequest) => Promise<void>; onClose: () => void }) {
  return (
    <FormDialog open={open} onClose={onClose}>
      <AddGuardianForm onSubmit={onSubmit} onClose={onClose} />
    </FormDialog>
  )
}

function AddGuardianForm({ onSubmit, onClose }: { onSubmit: (request: GuardianRequest) => Promise<void>; onClose: () => void }) {
  const { t } = useTranslation()
  const { error, saving, run } = useSubmit()
  const [values, setValues] = useState<GuardianValues>(emptyGuardian)
  const [primary, setPrimary] = useState(false)
  return (
    <Box component="form" noValidate onSubmit={(e) => run(e, () => onSubmit(guardianOf(values, primary)))}>
      <DialogTitle>{t('roster.addGuardian')}</DialogTitle>
      <DialogContent>
        <Refusal error={error} />
        <GuardianFields values={values} set={(patch) => setValues((v) => ({ ...v, ...patch }))} legend={t('roster.newGuardian')} />
        <FormControlLabel control={<Checkbox checked={primary} onChange={(e) => setPrimary(e.target.checked)} />} label={t('roster.makePrimary')} />
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>{t('crud.cancel')}</Button>
        <Button type="submit" variant="contained" loading={saving}>
          {t('roster.addGuardian')}
        </Button>
      </DialogActions>
    </Box>
  )
}

export interface GuardianEdit {
  guardian: GuardianUpdate
  relation: Relation
  primary: boolean
}

/**
 * A guardian's name, phone and language, and their link with this student. A new phone for a
 * guardian who uses the app signs them out: the dialog says so before saving (D24).
 */
export function EditGuardianDialog({ guardian, onSubmit, onClose }: { guardian: StudentGuardian | null; onSubmit: (edit: GuardianEdit) => Promise<void>; onClose: () => void }) {
  return (
    <FormDialog open={guardian !== null} onClose={onClose}>
      {guardian && <EditGuardianForm guardian={guardian} onSubmit={onSubmit} onClose={onClose} />}
    </FormDialog>
  )
}

function EditGuardianForm({ guardian, onSubmit, onClose }: { guardian: StudentGuardian; onSubmit: (edit: GuardianEdit) => Promise<void>; onClose: () => void }) {
  const { t } = useTranslation()
  const { error, saving, run, fieldError } = useSubmit()
  const [values, setValues] = useState<GuardianValues>({ phone: guardian.phone, name: guardian.name, relation: guardian.relation, language: guardian.language })
  const [primary, setPrimary] = useState(guardian.primary)
  const phoneChanged = values.phone.replace(/[\s.\-()]/g, '') !== guardian.phone
  return (
    <Box
      component="form"
      noValidate
      onSubmit={(e) =>
        run(e, () =>
          onSubmit({
            guardian: { name: values.name.trim(), phone: values.phone.trim(), language: values.language === '' ? guardian.language : values.language },
            relation: values.relation,
            primary,
          }),
        )
      }
    >
      <DialogTitle>{t('roster.editGuardian')}</DialogTitle>
      <DialogContent>
        <Refusal error={error} placed={Boolean(fieldError('phone'))} />
        <GuardianFields values={values} set={(patch) => setValues((v) => ({ ...v, ...patch }))} legend={guardian.name} schoolLanguage={false} />
        {fieldError('phone') && (
          <Typography variant="body2" color="error" role="alert">
            {fieldError('phone')}
          </Typography>
        )}
        {/* a student always has one primary guardian: it is given to another one, never just taken away */}
        <FormControlLabel
          control={<Checkbox checked={primary} disabled={guardian.primary} onChange={(e) => setPrimary(e.target.checked)} />}
          label={t('roster.makePrimary')}
        />
        {guardian.children > 1 && (
          <Typography variant="body2" color="text.secondary">
            {t('roster.siblings', { count: guardian.children })}
          </Typography>
        )}
        {phoneChanged && guardian.hasAccount && (
          <Alert severity="warning" sx={{ mt: 1.5 }}>
            {t('roster.phoneWarning')}
          </Alert>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>{t('crud.cancel')}</Button>
        <Button type="submit" variant="contained" loading={saving}>
          {t('crud.save')}
        </Button>
      </DialogActions>
    </Box>
  )
}

/**
 * Removes a guardian from this student only. When the primary goes and several stay, the admin
 * says who becomes the primary; with one left, that one does.
 */
export function RemoveGuardianDialog({ guardian, student, onSubmit, onClose }: { guardian: StudentGuardian | null; student: Student; onSubmit: (newPrimary: string | null) => Promise<void>; onClose: () => void }) {
  return (
    <FormDialog open={guardian !== null} onClose={onClose}>
      {guardian && <RemoveGuardianForm guardian={guardian} student={student} onSubmit={onSubmit} onClose={onClose} />}
    </FormDialog>
  )
}

function RemoveGuardianForm({ guardian, student, onSubmit, onClose }: { guardian: StudentGuardian; student: Student; onSubmit: (newPrimary: string | null) => Promise<void>; onClose: () => void }) {
  const { t } = useTranslation()
  const { error, saving, run } = useSubmit()
  const others = student.guardians.filter((g) => g.id !== guardian.id)
  const mustChoose = guardian.primary && others.length > 1
  const [newPrimary, setNewPrimary] = useState('')
  return (
    <Box component="form" noValidate onSubmit={(e) => run(e, () => onSubmit(mustChoose ? newPrimary : null))}>
      <DialogTitle>{t('roster.removeTitle', { name: guardian.name })}</DialogTitle>
      <DialogContent>
        <Refusal error={error} />
        <DialogContentText>{t('roster.removeText', { student: student.firstName })}</DialogContentText>
        {mustChoose && (
          <Box component="fieldset" sx={{ border: 0, p: 0, m: 0, mt: 2 }}>
            <Typography component="legend" variant="subtitle2" sx={{ p: 0 }}>
              {t('roster.newPrimary')}
            </Typography>
            {others.map((g) => (
              <FormControlLabel key={g.id} sx={{ display: 'flex' }} control={<Radio checked={newPrimary === g.id} onChange={() => setNewPrimary(g.id)} name="newPrimary" />} label={g.name} />
            ))}
          </Box>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>{t('crud.cancel')}</Button>
        <Button type="submit" variant="contained" color="error" loading={saving} disabled={mustChoose && newPrimary === ''}>
          {t('roster.remove')}
        </Button>
      </DialogActions>
    </Box>
  )
}
