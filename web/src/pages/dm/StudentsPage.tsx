import {
  Alert,
  InputAdornment,
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
import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { DM_PATHS, type StudentCard } from '../../api/dm'
import { errorMessage } from '../../api/errors'
import { PageHeader } from '../../components/PageHeader'
import { useList } from '../../hooks/useList'
import { MagnifyingGlassIcon, StudentIcon } from '../../icons'

/** The search starts at two letters, as on the phone (D35). */
const MIN_QUERY = 2

/** D35: finds a student of the DM's classes by name or matricule, to open their history (UC-D8). */
export function StudentsPage() {
  const { t } = useTranslation()
  const [query, setQuery] = useState('')
  const [searched, setSearched] = useState('')
  // Waits for the typing to pause before asking the server.
  useEffect(() => {
    const timer = setTimeout(() => setSearched(query.trim()), 300)
    return () => clearTimeout(timer)
  }, [query])
  const results = useList<StudentCard>(searched.length >= MIN_QUERY ? DM_PATHS.students(searched) : null)

  return (
    <Stack>
      <PageHeader icon={StudentIcon} title={t('dm.studentsTitle')} description={t('dm.studentsIntro')} />
      <Stack spacing={2}>
        <TextField
          label={t('dm.searchLabel')}
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          autoFocus
          sx={{ maxWidth: 480 }}
          slotProps={{
            input: {
              startAdornment: (
                <InputAdornment position="start">
                  <MagnifyingGlassIcon size={18} aria-hidden />
                </InputAdornment>
              ),
            },
          }}
        />
        {results.error !== null && <Alert severity="error">{errorMessage(results.error, t)}</Alert>}
        {searched.length < MIN_QUERY ? (
          <Typography color="text.secondary">{t('dm.searchHint')}</Typography>
        ) : !results.loading && results.error === null && results.items.length === 0 ? (
          <Typography color="text.secondary">{t('dm.noStudentFound')}</Typography>
        ) : (
          results.items.length > 0 && (
            <TableContainer component={Paper} variant="outlined">
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>{t('dm.student')}</TableCell>
                    <TableCell>{t('dm.matricule')}</TableCell>
                    <TableCell>{t('dm.class')}</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {results.items.map((s) => (
                    <TableRow key={s.id}>
                      <TableCell>
                        <Link to={`/students/${s.id}`}>
                          {s.lastName} {s.firstName}
                        </Link>
                      </TableCell>
                      <TableCell>{s.matricule}</TableCell>
                      <TableCell>{s.className ?? ''}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          )
        )}
      </Stack>
    </Stack>
  )
}
