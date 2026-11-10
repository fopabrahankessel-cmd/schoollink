import { Paper, Stack, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Typography } from '@mui/material'
import { useTranslation } from 'react-i18next'
import type { SentMessage } from '../../api/messages'
import { Pill } from '../../components/Pill'
import { tokens } from '../../theme'

/**
 * D38: messages already sent, newest first, with what became of them (one count per guardian and
 * channel). Never edited or taken back; no phone numbers (NFR-9).
 */
export function SentMessagesTable({ items, showAuthor = false }: { items: SentMessage[]; showAuthor?: boolean }) {
  const { t, i18n } = useTranslation()
  const when = new Intl.DateTimeFormat(i18n.language, { dateStyle: 'medium', timeStyle: 'short' })

  if (items.length === 0) {
    return (
      <Paper variant="outlined" sx={{ px: 2, py: 5, textAlign: 'center' }}>
        <Typography sx={{ fontWeight: 600 }}>{t('messages.none')}</Typography>
        <Typography color="text.secondary" sx={{ mt: 0.5 }}>
          {t('messages.noneBody')}
        </Typography>
      </Paper>
    )
  }

  const audience = (m: SentMessage) =>
    m.audience === 'STUDENT' ? (m.studentName ?? '') : m.audience === 'CLASS' ? (m.className ?? '') : t('messages.wholeSchool')

  return (
    <TableContainer component={Paper} variant="outlined" sx={{ maxHeight: '70vh' }}>
      <Table size="small" stickyHeader sx={{ minWidth: 820 }}>
        <TableHead>
          <TableRow>
            <TableCell>{t('messages.date')}</TableCell>
            <TableCell>{t('messages.to')}</TableCell>
            <TableCell>{t('messages.text')}</TableCell>
            {showAuthor && <TableCell>{t('messages.author')}</TableCell>}
            <TableCell>{t('messages.sms')}</TableCell>
            <TableCell>{t('messages.delivery')}</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {items.map((m) => {
            const d = m.delivery
            const inProgress = d.pending + d.sent
            return (
              <TableRow key={m.id} data-testid={`sent-${m.id}`} sx={{ verticalAlign: 'top' }}>
                <TableCell sx={{ whiteSpace: 'nowrap', color: tokens.muted }}>{when.format(new Date(m.createdAt))}</TableCell>
                <TableCell sx={{ fontWeight: 600, whiteSpace: 'nowrap' }}>{audience(m)}</TableCell>
                <TableCell sx={{ maxWidth: 380 }}>
                  <Typography
                    variant="body2"
                    title={m.body}
                    sx={{
                      overflowWrap: 'anywhere',
                      display: '-webkit-box',
                      WebkitLineClamp: 3,
                      WebkitBoxOrient: 'vertical',
                      overflow: 'hidden',
                    }}
                  >
                    {m.body}
                  </Typography>
                </TableCell>
                {showAuthor && <TableCell sx={{ whiteSpace: 'nowrap' }}>{m.authorName}</TableCell>}
                <TableCell sx={{ whiteSpace: 'nowrap', color: m.sms ? undefined : tokens.muted }}>{m.sms ? t('messages.withSms') : t('messages.noSms')}</TableCell>
                <TableCell>
                  <Stack direction="row" useFlexGap sx={{ flexWrap: 'wrap', gap: 0.5 }}>
                    <Pill tone="neutral">{t('messages.recipients', { count: d.recipients })}</Pill>
                    {d.opened > 0 && <Pill tone="positive">{t('messages.opened', { count: d.opened })}</Pill>}
                    {d.delivered > 0 && <Pill tone="brand">{t('messages.received', { count: d.delivered })}</Pill>}
                    {inProgress > 0 && <Pill tone="warning">{t('messages.pending', { count: inProgress })}</Pill>}
                    {d.failed > 0 && <Pill tone="negative">{t('messages.failed', { count: d.failed })}</Pill>}
                  </Stack>
                </TableCell>
              </TableRow>
            )
          })}
        </TableBody>
      </Table>
    </TableContainer>
  )
}
