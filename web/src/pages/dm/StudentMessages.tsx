import { Alert, Button, Paper, Typography } from '@mui/material'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { errorMessage } from '../../api/errors'
import { MESSAGE_PATHS, type SentMessage } from '../../api/messages'
import { useResource } from '../../hooks/useResource'
import { ChatTextIcon } from '../../icons'
import { SentMessagesTable } from '../messages/SentMessagesTable'

/** D41, UC-D4: the messages the DM wrote to this one student's parents, newest first. */
export function StudentMessages({ studentId }: { studentId: string }) {
  const { t } = useTranslation()
  const messages = useResource<SentMessage[]>(MESSAGE_PATHS.ofStudent(studentId))
  const write = `/messages/new?studentId=${encodeURIComponent(studentId)}`

  if (messages.error !== null) return <Alert severity="error">{errorMessage(messages.error, t)}</Alert>
  if (!messages.data) return null
  if (messages.data.length === 0) {
    return (
      <Paper variant="outlined" sx={{ px: 2, py: 5, textAlign: 'center' }}>
        <Typography sx={{ fontWeight: 600 }}>{t('messages.studentNone')}</Typography>
        <Typography color="text.secondary" sx={{ mt: 0.5, mb: 2 }}>
          {t('messages.noneBody')}
        </Typography>
        <Button component={Link} to={write} variant="contained" startIcon={<ChatTextIcon size={18} aria-hidden />}>
          {t('messages.writeToParents')}
        </Button>
      </Paper>
    )
  }
  return <SentMessagesTable items={messages.data} />
}
