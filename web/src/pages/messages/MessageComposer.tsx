import {
  Alert,
  Box,
  Button,
  Chip,
  FormControlLabel,
  LinearProgress,
  Paper,
  Radio,
  RadioGroup,
  Stack,
  Switch,
  TextField,
  Typography,
} from '@mui/material'
import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { errorMessage } from '../../api/errors'
import {
  MAX_MESSAGE_LENGTH,
  MESSAGE_PATHS,
  type ClassOption,
  type MessageDraft,
  type MessageKind,
  type MessagePreview,
} from '../../api/messages'
import { useAuth } from '../../auth/useAuth'
import { ConfirmDialog } from '../../components/ConfirmDialog'
import { useResource } from '../../hooks/useResource'
import { ChatTextIcon, PaperPlaneTiltIcon, StudentIcon } from '../../icons'
import { tokens } from '../../theme'

/** The preview is asked once the author pauses this long (D38). */
const PREVIEW_DELAY_MS = 400

interface Props {
  kind: MessageKind
  /** A message about one student: no class to choose, SMS on by default. */
  student?: { id: string; name: string }
  onSent: () => void
}

/**
 * UC-D4, UC-A5, D38: writes to parents. The form is on the left; on the right, what parents will
 * see (the app notification and, for those without the app, the SMS) with who gets it and what it
 * costs. Sending asks for confirmation. The id is made once here, so a double click or a retry
 * sends once.
 */
export function MessageComposer({ kind, student, onSent }: Props) {
  const { t, i18n } = useTranslation()
  const { client } = useAuth()
  const [id] = useState(() => crypto.randomUUID())
  const announcement = kind === 'ANNOUNCEMENT'
  const classes = useResource<ClassOption[]>(student ? null : MESSAGE_PATHS.classes)
  const [audience, setAudience] = useState<'SCHOOL' | 'CLASS'>(announcement ? 'SCHOOL' : 'CLASS')
  const [classId, setClassId] = useState('')
  const [body, setBody] = useState('')
  // D38: on by default for one student, off for a class or the school
  const [sms, setSms] = useState(Boolean(student))
  const [plain, setPlain] = useState(false)
  const [previewed, setPreviewed] = useState<{ key: string; preview: MessagePreview | null; error: unknown } | null>(null)
  const [confirming, setConfirming] = useState(false)
  const [sending, setSending] = useState(false)
  const [sendError, setSendError] = useState<unknown>(null)
  const inFlight = useRef(false)

  const target: Pick<MessageDraft, 'studentId' | 'classId'> | null = student
    ? { studentId: student.id }
    : audience === 'CLASS'
      ? classId
        ? { classId }
        : null
      : {}
  const draft: MessageDraft | null =
    target && body.trim() ? { id, ...target, body: body.trim(), sms, plainSms: plain } : null
  const key = draft ? JSON.stringify(draft) : null

  useEffect(() => {
    if (key === null) return
    let current = true
    const timer = setTimeout(() => {
      client
        .request<MessagePreview>(MESSAGE_PATHS.preview(kind), { method: 'POST', body: JSON.parse(key) })
        .then(
          (preview) => current && setPreviewed({ key, preview, error: null }),
          (error: unknown) => current && setPreviewed({ key, preview: null, error }),
        )
    }, PREVIEW_DELAY_MS)
    return () => {
      current = false
      clearTimeout(timer)
    }
  }, [client, kind, key])

  // the last answer, even for an older draft, stays on screen while the new one comes
  const preview = key !== null ? (previewed?.preview ?? null) : null
  const fresh = key !== null && previewed?.key === key
  const previewError = fresh ? previewed?.error : null
  const reachable = preview ? preview.byApp + preview.bySms > 0 : false
  const canSend = fresh && preview !== null && !preview.overCap && reachable && !sending

  const number = new Intl.NumberFormat(i18n.language)
  const counts = (p: MessagePreview) =>
    p.bySms > 0
      ? t('messages.countsSms', { count: p.recipients, byApp: p.byApp, bySms: p.bySms })
      : t('messages.counts', { count: p.recipients, byApp: p.byApp, without: p.unreached })
  const cost = (p: MessagePreview) =>
    t('messages.cost', { cost: number.format(p.estimatedCost), left: number.format(p.smsLeft) })

  const send = async () => {
    if (!draft || inFlight.current) return
    inFlight.current = true
    setSending(true)
    setSendError(null)
    try {
      await client.request(MESSAGE_PATHS.send(kind), { method: 'POST', body: draft })
      onSent()
    } catch (error) {
      setSendError(error)
      inFlight.current = false
      setSending(false)
    }
  }

  const showSms = preview !== null && preview.bySms > 0

  return (
    <Stack direction={{ xs: 'column', md: 'row' }} spacing={3} sx={{ alignItems: 'flex-start' }}>
      <Paper variant="outlined" sx={{ p: { xs: 2, sm: 3 }, flex: 3, minWidth: 0, width: '100%' }}>
        <Stack spacing={2.5}>
          {student ? (
            <Box>
              <Typography variant="body2" sx={{ color: tokens.muted, mb: 0.75 }}>
                {t('messages.student')}
              </Typography>
              <Chip icon={<StudentIcon size={18} aria-hidden />} label={student.name} variant="outlined" />
            </Box>
          ) : (
            <>
              {announcement && (
                <RadioGroup
                  aria-label={t('messages.audience')}
                  value={audience}
                  onChange={(e) => setAudience(e.target.value as 'SCHOOL' | 'CLASS')}
                >
                  <FormControlLabel value="SCHOOL" control={<Radio />} label={t('messages.audienceSchool')} />
                  <FormControlLabel value="CLASS" control={<Radio />} label={t('messages.audienceClass')} />
                </RadioGroup>
              )}
              {audience === 'CLASS' && (
                <TextField
                  select
                  label={t('messages.class')}
                  value={classId}
                  onChange={(e) => setClassId(e.target.value)}
                  fullWidth
                  disabled={!classes.data}
                  slotProps={{ select: { native: true }, inputLabel: { shrink: true } }}
                >
                  <option value="" disabled>
                    {t('messages.chooseClass')}
                  </option>
                  {(classes.data ?? []).map((c) => (
                    <option key={c.classId} value={c.classId}>
                      {t('messages.classStudents', { className: c.className, count: c.students })}
                    </option>
                  ))}
                </TextField>
              )}
              {classes.error !== null && <Alert severity="error">{errorMessage(classes.error, t)}</Alert>}
            </>
          )}
          <TextField
            label={t('messages.body')}
            value={body}
            onChange={(e) => setBody(e.target.value)}
            multiline
            minRows={5}
            fullWidth
            helperText={t('messages.characters', { count: body.length, max: MAX_MESSAGE_LENGTH })}
            slotProps={{ htmlInput: { maxLength: MAX_MESSAGE_LENGTH }, formHelperText: { sx: { textAlign: 'right' } } }}
          />
          <Box>
            <FormControlLabel
              control={<Switch checked={sms} onChange={(e) => setSms(e.target.checked)} />}
              label={t('messages.smsSwitch')}
            />
            <Typography variant="body2" sx={{ color: tokens.muted, ml: 6.5 }}>
              {t('messages.smsHint')}
            </Typography>
          </Box>
          {showSms && (preview.smsUnicode || plain) && (
            <Box>
              {preview.smsUnicode && (
                <Alert severity="info" sx={{ mb: 1 }}>
                  {t('messages.unicode')}
                </Alert>
              )}
              <FormControlLabel
                control={<Switch checked={plain} onChange={(e) => setPlain(e.target.checked)} />}
                label={t('messages.plainSwitch')}
              />
              <Typography variant="body2" sx={{ color: tokens.muted, ml: 6.5 }}>
                {t('messages.plainHint')}
              </Typography>
            </Box>
          )}
        </Stack>
      </Paper>

      <Box sx={{ flex: 2, minWidth: 0, width: '100%', position: { md: 'sticky' }, top: { md: 96 } }}>
        <Paper variant="outlined" sx={{ overflow: 'hidden', bgcolor: tokens.raised }}>
          <Box sx={{ height: 3 }}>{key !== null && !fresh && <LinearProgress sx={{ height: 3 }} />}</Box>
          <Stack spacing={2} sx={{ p: { xs: 2, sm: 2.5 } }}>
            <Typography variant="subtitle2" component="h2">
              {t('messages.preview')}
            </Typography>
            {key === null || preview === null ? (
              <Typography sx={{ color: tokens.muted }}>
                {!target ? t('messages.previewChooseClass') : t('messages.previewHint')}
              </Typography>
            ) : (
              <>
                <Box>
                  <Typography variant="caption" sx={{ color: tokens.muted }}>
                    {t('messages.pushLabel')}
                  </Typography>
                  <Paper sx={{ mt: 0.5, p: 1.5, borderRadius: 3, boxShadow: tokens.shadow.raised }}>
                    <Stack direction="row" spacing={1.25} sx={{ alignItems: 'flex-start' }}>
                      <Box
                        aria-hidden
                        sx={{
                          width: 28,
                          height: 28,
                          borderRadius: 1.5,
                          bgcolor: tokens.brand,
                          color: '#fff',
                          display: 'grid',
                          placeItems: 'center',
                          flexShrink: 0,
                        }}
                      >
                        <ChatTextIcon size={16} />
                      </Box>
                      <Box sx={{ minWidth: 0 }}>
                        <Typography variant="body2" sx={{ fontWeight: 700 }}>
                          {announcement ? t('messages.pushTitleAnnouncement') : t('messages.pushTitleDm')}
                        </Typography>
                        <Typography
                          variant="body2"
                          sx={{
                            overflowWrap: 'anywhere',
                            display: '-webkit-box',
                            WebkitLineClamp: 4,
                            WebkitBoxOrient: 'vertical',
                            overflow: 'hidden',
                          }}
                        >
                          {draft?.body}
                        </Typography>
                      </Box>
                    </Stack>
                  </Paper>
                </Box>
                {showSms && preview.smsSample && (
                  <Box>
                    <Typography variant="caption" sx={{ color: tokens.muted }}>
                      {t('messages.smsLabel')}
                    </Typography>
                    <Box
                      sx={{
                        mt: 0.5,
                        p: 1.5,
                        maxWidth: '92%',
                        bgcolor: tokens.brandSoft,
                        color: tokens.text,
                        borderRadius: '16px 16px 16px 4px',
                      }}
                    >
                      <Typography variant="body2" sx={{ overflowWrap: 'anywhere' }}>
                        {preview.smsSample}
                      </Typography>
                    </Box>
                    <Typography variant="caption" sx={{ color: tokens.muted }}>
                      {t('messages.units', { count: preview.smsUnits })}
                    </Typography>
                  </Box>
                )}
              </>
            )}
          </Stack>
        </Paper>

        <Stack spacing={1.5} sx={{ mt: 2 }}>
          {preview !== null && key !== null && (
            <Box>
              <Typography sx={{ fontWeight: 600 }}>{counts(preview)}</Typography>
              {showSms && <Typography sx={{ color: tokens.muted }}>{cost(preview)}</Typography>}
            </Box>
          )}
          {preview?.overCap && key !== null && (
            <Alert severity="error">{t('messages.overCap', { left: number.format(preview.smsLeft) })}</Alert>
          )}
          {preview && key !== null && !reachable && <Alert severity="warning">{t('messages.noRecipient')}</Alert>}
          {previewError !== null && previewError !== undefined && (
            <Alert severity="error">{errorMessage(previewError, t)}</Alert>
          )}
          {sendError !== null && <Alert severity="error">{errorMessage(sendError, t)}</Alert>}
          <Button
            variant="contained"
            size="large"
            startIcon={<PaperPlaneTiltIcon size={18} aria-hidden />}
            disabled={!canSend}
            onClick={() => setConfirming(true)}
          >
            {t('messages.send')}
          </Button>
        </Stack>
      </Box>

      {preview && (
        <ConfirmDialog
          open={confirming}
          title={t('messages.confirmTitle')}
          text={[counts(preview), showSms ? cost(preview) : null, t('messages.confirmBody')].filter(Boolean).join('. ')}
          confirmLabel={t('messages.send')}
          onConfirm={() => void send()}
          onClose={() => setConfirming(false)}
        />
      )}
    </Stack>
  )
}
