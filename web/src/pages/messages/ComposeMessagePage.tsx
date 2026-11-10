import { Alert, Stack } from '@mui/material'
import { useTranslation } from 'react-i18next'
import { useNavigate, useSearchParams } from 'react-router'
import { DM_PATHS, type StudentHistory } from '../../api/dm'
import { errorMessage } from '../../api/errors'
import { PageHeader } from '../../components/PageHeader'
import { useResource } from '../../hooks/useResource'
import { ChatTextIcon } from '../../icons'
import { MessageComposer } from './MessageComposer'
import type { SentState } from './MessagesPage'

/**
 * UC-D4, D38: a new message from the DM, to a class of their blocks, or to one student's parents
 * when opened from the student page (?studentId=).
 */
export function ComposeMessagePage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const studentId = params.get('studentId')
  const history = useResource<StudentHistory>(studentId ? DM_PATHS.history(studentId) : null)
  const student = history.data ? { id: history.data.studentId, name: `${history.data.lastName} ${history.data.firstName}` } : undefined
  const back = studentId
    ? { to: `/students/${encodeURIComponent(studentId)}`, label: student?.name ?? t('dm.historyTitle') }
    : { to: '/messages', label: t('messages.title') }

  return (
    <Stack>
      <PageHeader icon={ChatTextIcon} title={t('messages.writeToParents')} description={t('messages.writeIntro')} back={back} />
      {history.error !== null && <Alert severity="error">{errorMessage(history.error, t)}</Alert>}
      {/* a student's message waits for the name, so its SMS default is set once */}
      {(!studentId || student) && (
        <MessageComposer
          kind="DM_MESSAGE"
          student={student}
          onSent={() => navigate('/messages', { state: { sent: true } satisfies SentState })}
        />
      )}
    </Stack>
  )
}
