import { Alert, Box, Button, FormControlLabel, Paper, Stack, Switch, TextField, Typography } from '@mui/material'
import { useState, type FormEvent, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { DM_PATHS, hm, type RuleSetting, type Settings, type SettingsRequest } from '../../api/dm'
import { errorMessage } from '../../api/errors'
import { useAuth } from '../../auth/useAuth'
import { PageHeader } from '../../components/PageHeader'
import { useResource } from '../../hooks/useResource'
import { SlidersIcon } from '../../icons'
import { tokens } from '../../theme'

/**
 * UC-D7, D28: the school's alert thresholds, its start time (BR-7) and the times of the SMS digest
 * (BR-22, D6). The form is shown once the current settings are loaded.
 */
export function SettingsPage() {
  const { t } = useTranslation()
  const settings = useResource<Settings>(DM_PATHS.settings)
  return (
    <Stack>
      <PageHeader icon={SlidersIcon} title={t('dm.settingsTitle')} description={t('dm.settingsIntro')} />
      {settings.error !== null && !settings.data && <Alert severity="error">{errorMessage(settings.error, t)}</Alert>}
      {settings.data && <SettingsForm initial={settings.data} onSaved={settings.reload} />}
    </Stack>
  )
}

/** The bounds the server holds too (D28): a time outside them is a typing error. */
const START = { min: '05:00', max: '12:00' }
const DIGEST = { min: '06:00', max: '20:00' }

function SettingsForm({ initial, onSaved }: { initial: Settings; onSaved: () => Promise<void> }) {
  const { t } = useTranslation()
  const { client } = useAuth()
  const [startTime, setStartTime] = useState(hm(initial.startTime))
  const [digests, setDigests] = useState(initial.digestTimes.map(hm))
  const [rules, setRules] = useState<RuleSetting[]>(initial.rules)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const [saved, setSaved] = useState(false)

  const startError = !within(startTime, START) ? t('dm.startTimeRange', START) : ''
  const digestErrors = digests.map((time, i) =>
    !within(time, DIGEST) ? t('dm.digestRange', DIGEST) : i === 1 && time <= digests[0] ? t('dm.digestOrder') : '',
  )
  const ruleErrors = rules.map((r) =>
    Number.isInteger(r.count) && r.count >= r.min && r.count <= r.max ? '' : t('dm.countRange', { min: r.min, max: r.max }),
  )
  const valid = !startError && digestErrors.every((e) => !e) && ruleErrors.every((e) => !e)

  const setRule = (index: number, change: Partial<RuleSetting>) =>
    setRules((current) => current.map((r, i) => (i === index ? { ...r, ...change } : r)))

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (!valid) return
    setSaving(true)
    setError(null)
    setSaved(false)
    const body: SettingsRequest = {
      startTime,
      digestTimes: digests,
      rules: rules.map(({ type, count, active }) => ({ type, count, active })),
    }
    try {
      await client.request(DM_PATHS.settings, { method: 'PUT', body })
      setSaved(true)
      await onSaved()
    } catch (e) {
      setError(e)
    } finally {
      setSaving(false)
    }
  }

  return (
    <Stack component="form" onSubmit={submit} noValidate spacing={3} sx={{ maxWidth: 720 }}>
      {error !== null && <Alert severity="error">{errorMessage(error, t)}</Alert>}
      {saved && (
        <Alert severity="success" onClose={() => setSaved(false)}>
          {t('dm.settingsSaved')}
        </Alert>
      )}

      <Section title={t('dm.rulesTitle')} intro={t('dm.rulesIntro')}>
        <Stack spacing={2}>
          {rules.map((rule, i) => (
            <Paper key={rule.type} variant="outlined" sx={{ p: 2 }}>
              <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ alignItems: { sm: 'flex-start' } }}>
                <Box sx={{ flex: 1, minWidth: 0 }}>
                  <Typography variant="subtitle2" component="h3">
                    {t(`dm.rules.${rule.type}`)}
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    {t(`dm.ruleHelp.${rule.type}`, { count: rule.count })}
                  </Typography>
                  <FormControlLabel
                    sx={{ mt: 0.5 }}
                    control={<Switch checked={rule.active} onChange={(e) => setRule(i, { active: e.target.checked })} />}
                    label={rule.active ? t('dm.ruleOn') : t('dm.ruleOff')}
                  />
                </Box>
                <TextField
                  type="number"
                  label={t(`dm.ruleCount.${rule.type}`)}
                  value={Number.isNaN(rule.count) ? '' : rule.count}
                  onChange={(e) => setRule(i, { count: e.target.value === '' ? NaN : Number(e.target.value) })}
                  disabled={!rule.active}
                  error={ruleErrors[i] !== ''}
                  helperText={ruleErrors[i] || t('dm.countRange', { min: rule.min, max: rule.max })}
                  sx={{ width: { sm: 200 } }}
                  slotProps={{ htmlInput: { min: rule.min, max: rule.max, step: 1 } }}
                />
              </Stack>
            </Paper>
          ))}
        </Stack>
      </Section>

      <Section title={t('dm.timesTitle')} intro={t('dm.timesIntro')}>
        <Stack spacing={2}>
          <TextField
            type="time"
            label={t('dm.startTime')}
            value={startTime}
            onChange={(e) => setStartTime(e.target.value)}
            error={startError !== ''}
            helperText={startError || t('dm.startTimeHelp')}
            sx={{ maxWidth: 240 }}
            slotProps={{ inputLabel: { shrink: true }, htmlInput: START }}
          />
          {digests.map((time, i) => (
            <Stack key={i} direction="row" spacing={1} sx={{ alignItems: 'flex-start' }}>
              <TextField
                type="time"
                label={t('dm.digestTime', { n: i + 1 })}
                value={time}
                onChange={(e) => setDigests((current) => current.map((d, j) => (j === i ? e.target.value : d)))}
                error={digestErrors[i] !== ''}
                helperText={digestErrors[i] || undefined}
                sx={{ maxWidth: 240 }}
                slotProps={{ inputLabel: { shrink: true }, htmlInput: DIGEST }}
              />
              {i === 1 && (
                <Button onClick={() => setDigests((current) => current.slice(0, 1))} sx={{ mt: 1 }}>
                  {t('dm.removeDigest')}
                </Button>
              )}
            </Stack>
          ))}
          {digests.length === 1 && (
            <Box>
              <Button variant="outlined" onClick={() => setDigests((current) => [...current, '18:00'])}>
                {t('dm.addDigest')}
              </Button>
            </Box>
          )}
          <Typography variant="body2" sx={{ color: tokens.muted }}>
            {t('dm.digestHelp')}
          </Typography>
        </Stack>
      </Section>

      <Box>
        <Button type="submit" variant="contained" disabled={!valid} loading={saving}>
          {t('crud.save')}
        </Button>
      </Box>
    </Stack>
  )
}

function Section({ title, intro, children }: { title: string; intro: string; children: ReactNode }) {
  return (
    <Box component="section">
      <Typography variant="h6" component="h2">
        {title}
      </Typography>
      <Typography color="text.secondary" sx={{ mb: 1.5 }}>
        {intro}
      </Typography>
      {children}
    </Box>
  )
}

const within = (time: string, range: { min: string; max: string }) => time !== '' && time >= range.min && time <= range.max
