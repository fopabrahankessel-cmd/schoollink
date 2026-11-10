import { Stack } from '@mui/material'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'
import { PageHeader } from '../../components/PageHeader'
import { MegaphoneIcon } from '../../icons'
import { MessageComposer } from './MessageComposer'
import type { SentState } from './MessagesPage'

/** UC-A5, D38: a new announcement to every parent of the school, or of one class. */
export function ComposeAnnouncementPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  return (
    <Stack>
      <PageHeader
        icon={MegaphoneIcon}
        title={t('announcements.write')}
        description={t('announcements.writeIntro')}
        back={{ to: '/announcements', label: t('announcements.title') }}
      />
      <MessageComposer
        kind="ANNOUNCEMENT"
        onSent={() => navigate('/announcements', { state: { sent: true } satisfies SentState })}
      />
    </Stack>
  )
}
