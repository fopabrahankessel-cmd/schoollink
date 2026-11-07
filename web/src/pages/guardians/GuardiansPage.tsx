import {
  Alert,
  Box,
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
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { errorMessage } from '../../api/errors'
import { guardiansPath, STATUSES, type Guardian, type GuardianStatus } from '../../api/guardians'
import { PATHS, query, type AcademicYear, type SchoolClass } from '../../api/structure'
import { PageHeader } from '../../components/PageHeader'
import { PersonAvatar } from '../../components/PersonAvatar'
import { YearPicker } from '../../components/YearPicker'
import { useList } from '../../hooks/useList'
import { UsersFourIcon } from '../../icons'
import { tokens } from '../../theme'

type Pill = keyof typeof tokens.pill

const PILL: Record<GuardianStatus, Pill> = {
  ACCOUNT: 'positive',
  NO_ACCOUNT: 'neutral',
}

/**
 * UC-A4: a class's guardians and who has signed in to the app (D7). Nothing to send from here: a
 * parent signs in with their phone number and an SMS code (UC-P1).
 */
export function GuardiansPage() {
  const { t } = useTranslation()
  const years = useList<AcademicYear>(PATHS.years)
  const [yearId, setYearId] = useState('')
  const [classId, setClassId] = useState('')
  const classes = useList<SchoolClass>(yearId ? PATHS.classes + query({ academicYearId: yearId }) : null)
  const guardians = useList<Guardian>(classId ? guardiansPath(classId) : null)

  const className = (id: string) => classes.items.find((c) => c.id === id)?.name
  const count = (status: GuardianStatus) => guardians.items.filter((g) => g.status === status).length

  return (
    <Stack>
      <PageHeader
        icon={UsersFourIcon}
        title={t('guardians.title')}
        description={t('guardians.intro')}
      />
      <Stack spacing={2.5}>
        {years.error !== null && <Alert severity="error">{errorMessage(years.error, t)}</Alert>}
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
            label={t('guardians.class')}
            value={classId}
            onChange={(e) => {
              setClassId(e.target.value)
            }}
            margin="none"
            sx={{ maxWidth: 260 }}
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

        {guardians.error !== null && <Alert severity="error">{errorMessage(guardians.error, t)}</Alert>}

        {!classId ? (
          <Paper variant="outlined" sx={{ px: 2, py: 5, textAlign: 'center' }}>
            <Typography color="text.secondary">{t('guardians.pickClass')}</Typography>
          </Paper>
        ) : (
          <Paper variant="outlined" sx={{ overflow: 'hidden' }}>
            <Stack
              direction="row"
              component="ul"
              aria-label={t('guardians.summary')}
              sx={{ listStyle: 'none', m: 0, px: 2, py: 1.75, gap: 1, flexWrap: 'wrap' }}
            >
              {STATUSES.map((status) => (
                <Box component="li" key={status}>
                  {/* only the statuses that are there take colour */}
                  <StatusPill status={status} quiet={guardians.loading || count(status) === 0}>
                    {t(`guardians.count.${status}`, { count: guardians.loading ? 0 : count(status) })}
                  </StatusPill>
                </Box>
              ))}
            </Stack>
            {!guardians.loading && guardians.items.length === 0 ? (
              <Typography color="text.secondary" sx={{ px: 2, py: 5, textAlign: 'center', borderTop: `1px solid ${tokens.border}` }}>
                {t('guardians.empty')}
              </Typography>
            ) : (
              <TableContainer sx={{ borderTop: `1px solid ${tokens.border}` }}>
                <Table size="small" sx={{ minWidth: 760, '& .MuiTableCell-root:first-of-type': { pl: 2 } }}>
                  <TableHead>
                    <TableRow>
                      <TableCell>{t('guardians.guardian')}</TableCell>
                      <TableCell>{t('guardians.children')}</TableCell>
                      <TableCell>{t('guardians.status')}</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {guardians.items.map((g) => (
                      <TableRow key={g.id} hover>
                        <TableCell>
                          <Stack direction="row" sx={{ alignItems: 'center', gap: 1.5 }}>
                            <PersonAvatar id={g.id} firstName={g.name} lastName={g.name.split(' ').at(-1) ?? ''} />
                            <Box sx={{ minWidth: 0 }}>
                              <Typography sx={{ fontWeight: 600, fontSize: 14 }}>{g.name}</Typography>
                              <Typography variant="caption" color="text.secondary" component="p" sx={{ whiteSpace: 'nowrap' }}>
                                {g.phone}, {t('guardians.smsIn', { language: t(`language.${g.language.toLowerCase()}`) })}
                              </Typography>
                            </Box>
                          </Stack>
                        </TableCell>
                        <TableCell>
                          <Box component="ul" sx={{ listStyle: 'none', m: 0, p: 0, fontSize: 13.5 }}>
                            {g.children.map((c) => (
                              <li key={c.id}>
                                {t('guardians.child', {
                                  name: `${c.firstName} ${c.lastName}`,
                                  relation: t(`guardians.relation.${c.relation}`),
                                })}
                                {c.classId !== classId && className(c.classId) && (
                                  <Box component="span" sx={{ color: tokens.muted }}>
                                    {' '}
                                    {t('guardians.inClass', { name: className(c.classId) })}
                                  </Box>
                                )}
                              </li>
                            ))}
                          </Box>
                        </TableCell>
                        <TableCell>
                          <StatusPill status={g.status}>{t(`guardians.state.${g.status}`)}</StatusPill>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            )}
          </Paper>
        )}
      </Stack>
    </Stack>
  )
}

function StatusPill({ status, quiet = false, children }: { status: GuardianStatus; quiet?: boolean; children: string }) {
  const pill = tokens.pill[quiet ? 'neutral' : PILL[status]]
  return (
    <Box
      component="span"
      sx={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 0.75,
        px: 1,
        py: 0.25,
        borderRadius: `${tokens.radius.chip}px`,
        bgcolor: pill.bg,
        color: pill.ink,
        fontSize: 12.5,
        fontWeight: 600,
        whiteSpace: 'nowrap',
      }}
    >
      {children}
    </Box>
  )
}
