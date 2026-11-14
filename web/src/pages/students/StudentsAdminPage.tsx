import {
  Alert,
  Box,
  Button,
  FormControlLabel,
  InputAdornment,
  Paper,
  Stack,
  Switch,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from '@mui/material'
import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate } from 'react-router'
import { errorMessage } from '../../api/errors'
import { PATHS, query, type AcademicYear, type SchoolClass } from '../../api/structure'
import { listName, STUDENTS_PATH, studentsPath, type Student, type StudentRow } from '../../api/students'
import { useAuth } from '../../auth/useAuth'
import { PageHeader } from '../../components/PageHeader'
import { PersonAvatar } from '../../components/PersonAvatar'
import { Pill } from '../../components/Pill'
import { YearPicker } from '../../components/YearPicker'
import { formatDate } from '../../format'
import { useList } from '../../hooks/useList'
import { ArrowRightIcon, MagnifyingGlassIcon, StudentIcon, UserPlusIcon } from '../../icons'
import { tokens } from '../../theme'
import { MoveClassDialog, NewStudentDialog } from './dialogs'

export const ADMIN_STUDENTS = '/school/students'

/** The search waits for a pause in the typing, and for two characters. */
function useSearch(text: string): string {
  const [settled, setSettled] = useState('')
  useEffect(() => {
    const value = text.trim().length >= 2 ? text.trim() : ''
    const timer = setTimeout(() => setSettled(value), 250)
    return () => clearTimeout(timer)
  }, [text])
  return settled
}

/**
 * D43: the students of the school after the import. The admin opens a class, or finds a student
 * of any class by name or matricule; adds one who arrives during the year; and at the end of the
 * year moves a whole class to its next class.
 */
export function StudentsAdminPage() {
  const { t, i18n } = useTranslation()
  const { client } = useAuth()
  const navigate = useNavigate()
  const years = useList<AcademicYear>(PATHS.years)
  const [yearId, setYearId] = useState('')
  const [classId, setClassId] = useState('')
  const classes = useList<SchoolClass>(yearId ? PATHS.classes + query({ academicYearId: yearId }) : null)
  const [text, setText] = useState('')
  const q = useSearch(text)
  const [withLeft, setWithLeft] = useState(false)
  // a search covers the whole school; without one, the chosen class
  const path = q ? studentsPath({ q, left: withLeft }) : classId ? studentsPath({ classId, left: withLeft }) : null
  const students = useList<StudentRow>(path)
  const [adding, setAdding] = useState(false)
  const [moving, setMoving] = useState(false)
  const [moved, setMoved] = useState<{ count: number; name: string } | null>(null)

  const chosen = classes.items.find((c) => c.id === classId)
  const enrolled = students.items.filter((s) => s.leftOn === null)
  const inClass = !q && chosen !== undefined

  return (
    <Stack>
      <PageHeader
        icon={StudentIcon}
        title={t('roster.title')}
        description={t('roster.intro')}
        actions={
          <Button variant="contained" startIcon={<UserPlusIcon weight="bold" aria-hidden />} onClick={() => setAdding(true)}>
            {t('roster.add')}
          </Button>
        }
      />
      <Stack spacing={2}>
        {years.error !== null && <Alert severity="error">{errorMessage(years.error, t)}</Alert>}
        {students.error !== null && <Alert severity="error">{errorMessage(students.error, t)}</Alert>}
        {moved && (
          <Alert severity="success" onClose={() => setMoved(null)}>
            {t('roster.moved', { count: moved.count, name: moved.name })}
          </Alert>
        )}
        <Paper variant="outlined" sx={{ overflow: 'hidden' }}>
          <Stack direction={{ xs: 'column', lg: 'row' }} sx={{ gap: 2, px: 2, py: 2, alignItems: { lg: 'center' }, justifyContent: 'space-between' }}>
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
              <YearPicker
                years={years.items}
                value={yearId}
                onChange={(id) => {
                  setYearId(id)
                  setClassId('')
                }}
              />
              <TextField
                select
                label={t('roster.class')}
                value={classId}
                onChange={(e) => {
                  setClassId(e.target.value)
                  setMoved(null)
                }}
                margin="none"
                sx={{ minWidth: 160 }}
                slotProps={{ select: { native: true }, inputLabel: { shrink: true } }}
              >
                <option value="" disabled>
                  {t('crud.choose')}
                </option>
                {classes.items.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.name}
                  </option>
                ))}
              </TextField>
            </Stack>
            <Stack direction={{ xs: 'column', sm: 'row' }} sx={{ gap: 2, alignItems: { sm: 'center' } }}>
              <FormControlLabel
                control={<Switch size="small" checked={withLeft} onChange={(e) => setWithLeft(e.target.checked)} />}
                label={t('roster.showLeft')}
                sx={{ mr: 0, '& .MuiFormControlLabel-label': { fontSize: 14 } }}
              />
              <TextField
                label={t('roster.search')}
                value={text}
                onChange={(e) => setText(e.target.value)}
                margin="none"
                type="search"
                sx={{ width: { sm: 280 }, '& .MuiInputLabel-root': visuallyHidden }}
                slotProps={{
                  input: {
                    startAdornment: (
                      <InputAdornment position="start">
                        <MagnifyingGlassIcon size={18} aria-hidden />
                      </InputAdornment>
                    ),
                  },
                  htmlInput: { placeholder: t('roster.search') },
                }}
              />
            </Stack>
          </Stack>

          {path === null ? (
            <Typography color="text.secondary" sx={{ px: 2, py: 5, textAlign: 'center', borderTop: `1px solid ${tokens.border}` }}>
              {t('roster.pick')}
            </Typography>
          ) : (
            <>
              <Stack
                direction="row"
                sx={{ px: 2, py: 1.25, gap: 2, alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', borderTop: `1px solid ${tokens.border}`, bgcolor: tokens.brandSoft }}
              >
                <Typography sx={{ fontSize: 14, fontWeight: 600, color: tokens.brandDark }}>
                  {q ? t('roster.results', { q }) : chosen?.name}
                  {!students.loading && (
                    <Box component="span" sx={{ ml: 1.5, fontWeight: 400, color: tokens.muted }}>
                      {t('roster.count', { count: students.items.length })}
                    </Box>
                  )}
                </Typography>
                {/* the end of the year: the whole class goes to its next class */}
                {inClass && enrolled.length > 0 && (
                  <Button size="small" variant="outlined" endIcon={<ArrowRightIcon aria-hidden />} onClick={() => setMoving(true)} sx={{ bgcolor: tokens.surface }}>
                    {t('roster.moveClass')}
                  </Button>
                )}
              </Stack>
              {!students.loading && students.items.length === 0 ? (
                <Typography color="text.secondary" sx={{ px: 2, py: 5, textAlign: 'center', borderTop: `1px solid ${tokens.border}` }}>
                  {q ? t('roster.noMatch') : t('roster.empty')}
                </Typography>
              ) : (
                <TableContainer sx={{ borderTop: `1px solid ${tokens.border}` }}>
                  <Table size="small" sx={{ minWidth: 640, '& .MuiTableCell-root:first-of-type': { pl: 2 } }}>
                    <TableHead>
                      <TableRow>
                        <TableCell>{t('roster.name')}</TableCell>
                        <TableCell>{t('roster.matricule')}</TableCell>
                        <TableCell>{t('roster.class')}</TableCell>
                        <TableCell align="right">{t('roster.guardians')}</TableCell>
                        <TableCell>{t('roster.state')}</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {students.items.map((s) => (
                        <TableRow key={s.id} hover sx={s.leftOn ? { '& td': { color: tokens.muted } } : undefined}>
                          <TableCell>
                            <Stack direction="row" sx={{ alignItems: 'center', gap: 1.5, minWidth: 0 }}>
                              <PersonAvatar id={s.id} firstName={s.firstName} lastName={s.lastName} />
                              <Box component={Link} to={`${ADMIN_STUDENTS}/${s.id}`} sx={{ color: s.leftOn ? tokens.muted : tokens.text }}>
                                {listName(s)}
                              </Box>
                            </Stack>
                          </TableCell>
                          <TableCell sx={{ whiteSpace: 'nowrap', fontVariantNumeric: 'tabular-nums' }}>{s.matricule}</TableCell>
                          <TableCell sx={{ whiteSpace: 'nowrap' }}>{s.className}</TableCell>
                          <TableCell align="right" sx={{ fontVariantNumeric: 'tabular-nums' }}>
                            {s.guardians}
                          </TableCell>
                          <TableCell>
                            {s.leftOn ? (
                              <Pill tone="neutral">{t('roster.leftOn', { date: formatDate(s.leftOn, i18n.language) })}</Pill>
                            ) : (
                              <Typography component="span" color="text.secondary" sx={{ fontSize: 13 }}>
                                {t('roster.enrolled')}
                              </Typography>
                            )}
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </TableContainer>
              )}
            </>
          )}
        </Paper>
      </Stack>

      <NewStudentDialog
        open={adding}
        classId={classId}
        onClose={() => setAdding(false)}
        onSubmit={async (request) => {
          const created = await client.request<Student>(STUDENTS_PATH, { method: 'POST', body: request })
          setAdding(false)
          // next: check the guardians on the student's page
          navigate(`${ADMIN_STUDENTS}/${created.id}`)
        }}
      />
      <MoveClassDialog
        open={moving}
        className={chosen?.name ?? ''}
        classId={classId}
        students={enrolled}
        onClose={() => setMoving(false)}
        onSubmit={async (studentIds, target) => {
          const result = await client.request<{ moved: number }>(`${STUDENTS_PATH}/move`, {
            method: 'POST',
            body: { studentIds, classId: target.id },
          })
          setMoving(false)
          setMoved({ count: result.moved, name: target.name })
          void students.reload()
        }}
      />
    </Stack>
  )
}

// The search field is labelled for screen readers; its placeholder and icon say the same on screen.
const visuallyHidden = {
  position: 'absolute',
  width: '1px',
  height: '1px',
  overflow: 'hidden',
  clip: 'rect(0 0 0 0)',
  whiteSpace: 'nowrap',
} as const
