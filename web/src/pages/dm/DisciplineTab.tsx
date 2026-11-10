import {
  Alert,
  IconButton,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Tooltip,
  Typography,
} from '@mui/material'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { DM_PATHS, type DisciplinaryRecord, type IncidentRequest } from '../../api/dm'
import { errorMessage } from '../../api/errors'
import { useAuth } from '../../auth/useAuth'
import { Pill } from '../../components/Pill'
import { formatDate } from '../../format'
import { useResource } from '../../hooks/useResource'
import { CheckCircleIcon, ProhibitIcon } from '../../icons'
import { tokens } from '../../theme'
import { CancelRecordDialog, CloseIncidentDialog, RecordIncidentDialog } from './dialogs'

/**
 * UC-D5, D37: one student's disciplinary incidents, newest first. The DM records one (the page's
 * "Record an incident" button opens the dialog), closes it once with an optional note, or cancels
 * it when it was entered by mistake; a cancelled incident is no longer listed. Nothing here is
 * shown or sent to the parents.
 */
export function DisciplineTab({
  studentId,
  recording,
  onRecordingDone,
}: {
  studentId: string
  /** The page asked to record an incident. */
  recording: boolean
  onRecordingDone: () => void
}) {
  const { t, i18n } = useTranslation()
  const { client } = useAuth()
  const records = useResource<DisciplinaryRecord[]>(DM_PATHS.disciplinary(studentId))
  const [closing, setClosing] = useState<DisciplinaryRecord | null>(null)
  const [cancelling, setCancelling] = useState<DisciplinaryRecord | null>(null)
  const [done, setDone] = useState<string | null>(null)

  const describe = (record: DisciplinaryRecord) =>
    `${formatDate(record.occurredOn, i18n.language)}, ${t(`dm.categories.${record.category}`)}`

  const finish = async (message: string) => {
    setDone(message)
    await records.reload()
  }

  const record = async (incident: IncidentRequest) => {
    await client.request(DM_PATHS.disciplinary(studentId), { method: 'POST', body: incident })
    onRecordingDone()
    await finish(t('dm.incidentRecorded'))
  }

  const close = async (note: string) => {
    if (!closing) return
    await client.request(DM_PATHS.closeIncident(closing.id), { method: 'POST', body: note ? { note } : {} })
    setClosing(null)
    await finish(t('dm.incidentClosedDone'))
  }

  const cancel = async (reason: string) => {
    if (!cancelling) return
    await client.request(DM_PATHS.cancelIncident(cancelling.id), { method: 'POST', body: { reason } })
    setCancelling(null)
    await finish(t('dm.incidentCancelled'))
  }

  const list = records.data

  return (
    <Stack spacing={2}>
      {done && (
        <Alert severity="success" onClose={() => setDone(null)}>
          {done}
        </Alert>
      )}
      {records.error !== null && <Alert severity="error">{errorMessage(records.error, t)}</Alert>}
      {list &&
        (list.length === 0 ? (
          <Paper variant="outlined" sx={{ px: 2, py: 5, textAlign: 'center' }}>
            <Typography sx={{ fontWeight: 600 }}>{t('dm.noIncidents')}</Typography>
            <Typography color="text.secondary" sx={{ mt: 0.5 }}>
              {t('dm.noIncidentsBody')}
            </Typography>
          </Paper>
        ) : (
          <TableContainer component={Paper} variant="outlined" sx={{ maxHeight: '70vh' }}>
            <Table size="small" stickyHeader sx={{ minWidth: 760 }}>
              <TableHead>
                <TableRow>
                  <TableCell>{t('dm.date')}</TableCell>
                  <TableCell>{t('dm.incidentCategory')}</TableCell>
                  <TableCell>{t('dm.incidentMeasure')}</TableCell>
                  <TableCell>{t('dm.incidentDescription')}</TableCell>
                  <TableCell>{t('dm.incidentRecordedBy')}</TableCell>
                  <TableCell>{t('dm.status')}</TableCell>
                  <TableCell align="right">{t('crud.actions')}</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {list.map((r) => {
                  const open = r.status === 'OPEN'
                  return (
                    <TableRow key={r.id} data-testid={`incident-${r.id}`} sx={{ verticalAlign: 'top' }}>
                      <TableCell sx={{ whiteSpace: 'nowrap' }}>{formatDate(r.occurredOn, i18n.language)}</TableCell>
                      <TableCell sx={{ fontWeight: 600 }}>{t(`dm.categories.${r.category}`)}</TableCell>
                      <TableCell sx={{ color: r.measure === 'NONE' ? tokens.muted : undefined }}>
                        {t(`dm.measures.${r.measure}`)}
                      </TableCell>
                      <TableCell sx={{ maxWidth: 360 }}>
                        <Typography variant="body2" sx={{ overflowWrap: 'anywhere' }}>
                          {r.description}
                        </Typography>
                        {r.closeNote && (
                          <Typography variant="body2" sx={{ color: tokens.muted, mt: 0.5, overflowWrap: 'anywhere' }}>
                            {r.closeNote}
                          </Typography>
                        )}
                      </TableCell>
                      <TableCell>{r.recordedByName}</TableCell>
                      <TableCell>
                        <Pill tone={open ? 'warning' : 'positive'}>{open ? t('dm.incidentOpen') : t('dm.incidentClosed')}</Pill>
                      </TableCell>
                      <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                        {open && (
                          <Tooltip title={t('dm.closeIncidentTitle')}>
                            <IconButton
                              size="small"
                              aria-label={t('dm.closeIncidentOf', { what: describe(r) })}
                              onClick={() => setClosing(r)}
                            >
                              <CheckCircleIcon size={18} aria-hidden />
                            </IconButton>
                          </Tooltip>
                        )}
                        <Tooltip title={t('dm.cancelRecord')}>
                          <IconButton
                            size="small"
                            aria-label={t('dm.cancelIncidentOf', { what: describe(r) })}
                            onClick={() => setCancelling(r)}
                          >
                            <ProhibitIcon size={18} aria-hidden />
                          </IconButton>
                        </Tooltip>
                      </TableCell>
                    </TableRow>
                  )
                })}
              </TableBody>
            </Table>
          </TableContainer>
        ))}
      <RecordIncidentDialog open={recording} onSubmit={record} onClose={onRecordingDone} />
      <CloseIncidentDialog what={closing ? describe(closing) : null} onSubmit={close} onClose={() => setClosing(null)} />
      <CancelRecordDialog
        what={cancelling ? describe(cancelling) : null}
        intro={cancelling ? t('dm.cancelIncidentIntro', { what: describe(cancelling) }) : undefined}
        onSubmit={cancel}
        onClose={() => setCancelling(null)}
      />
    </Stack>
  )
}
