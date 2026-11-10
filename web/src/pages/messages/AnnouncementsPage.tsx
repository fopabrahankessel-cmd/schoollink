import { Alert, Button, Stack } from '@mui/material'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useLocation } from 'react-router'
import { errorMessage } from '../../api/errors'
import { MESSAGE_PATHS, type SentMessage } from '../../api/messages'
import { PageHeader } from '../../components/PageHeader'
import { useResource } from '../../hooks/useResource'
import { MegaphoneIcon, PlusIcon } from '../../icons'
import type { SentState } from './MessagesPage'
import { SentMessagesTable } from './SentMessagesTable'

/** UC-A5, D38: the school's announcements to parents, newest first, with their author and delivery. */
export function AnnouncementsPage() {
  const { t } = useTranslation()
  const location = useLocation()
  const [justSent, setJustSent] = useState(Boolean((location.state as SentState | null)?.sent))
  const announcements = useResource<SentMessage[]>(MESSAGE_PATHS.sent('ANNOUNCEMENT'))

  return (
    <Stack>
      <PageHeader
        icon={MegaphoneIcon}
        title={t('announcements.title')}
        description={t('announcements.intro')}
        actions={
          <Button component={Link} to="/announcements/new" variant="contained" startIcon={<PlusIcon size={18} aria-hidden />}>
            {t('announcements.newAnnouncement')}
          </Button>
        }
      />
      <Stack spacing={2}>
        {justSent && (
          <Alert severity="success" onClose={() => setJustSent(false)}>
            {t('announcements.sent')}
          </Alert>
        )}
        {announcements.error !== null && <Alert severity="error">{errorMessage(announcements.error, t)}</Alert>}
        {announcements.data && (
          <SentMessagesTable items={announcements.data} showAuthor empty={t('announcements.none')} />
        )}
      </Stack>
    </Stack>
  )
}
