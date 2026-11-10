import { Alert, Button, Stack } from '@mui/material'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useLocation } from 'react-router'
import { errorMessage } from '../../api/errors'
import { MESSAGE_PATHS, type SentMessage } from '../../api/messages'
import { PageHeader } from '../../components/PageHeader'
import { useResource } from '../../hooks/useResource'
import { ChatTextIcon, PlusIcon } from '../../icons'
import { SentMessagesTable } from './SentMessagesTable'

/** Set by the composer when it comes back here after sending. */
export interface SentState {
  sent?: boolean
}

/** UC-D4, D38: the DM's messages to parents, newest first, with their delivery. */
export function MessagesPage() {
  const { t } = useTranslation()
  const location = useLocation()
  const [justSent, setJustSent] = useState(Boolean((location.state as SentState | null)?.sent))
  const messages = useResource<SentMessage[]>(MESSAGE_PATHS.sent('DM_MESSAGE'))

  return (
    <Stack>
      <PageHeader
        icon={ChatTextIcon}
        title={t('messages.title')}
        description={t('messages.intro')}
        actions={
          <Button component={Link} to="/messages/new" variant="contained" startIcon={<PlusIcon size={18} aria-hidden />}>
            {t('messages.newMessage')}
          </Button>
        }
      />
      <Stack spacing={2}>
        {justSent && (
          <Alert severity="success" onClose={() => setJustSent(false)}>
            {t('messages.sent')}
          </Alert>
        )}
        {messages.error !== null && <Alert severity="error">{errorMessage(messages.error, t)}</Alert>}
        {messages.data && <SentMessagesTable items={messages.data} />}
      </Stack>
    </Stack>
  )
}
