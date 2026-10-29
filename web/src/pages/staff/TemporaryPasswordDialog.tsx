import { Alert, Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, Typography } from '@mui/material'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'

/**
 * D18: the temporary password is shown once, here; the server keeps only its hash. The person
 * must replace it at their first sign-in.
 */
export function TemporaryPasswordDialog({
  password,
  name,
  onClose,
}: {
  password: string | null
  name: string
  onClose: () => void
}) {
  const { t } = useTranslation()
  const [copied, setCopied] = useState(false)

  async function copy() {
    if (!password) return
    try {
      await navigator.clipboard.writeText(password)
      setCopied(true)
    } catch {
      // clipboard blocked: the password stays on screen to copy by hand
    }
  }

  return (
    <Dialog
      open={password !== null}
      onClose={(_, reason) => reason !== 'backdropClick' && onClose()}
      fullWidth
      maxWidth="xs"
    >
      <DialogTitle>{t('staff.passwordTitle')}</DialogTitle>
      <DialogContent>
        <Typography gutterBottom>{t('staff.passwordFor', { name })}</Typography>
        <Box
          aria-label={t('staff.passwordLabel')}
          sx={{
            fontFamily: 'monospace',
            fontSize: 28,
            letterSpacing: 2,
            textAlign: 'center',
            py: 2,
            my: 1,
            bgcolor: 'grey.100',
            borderRadius: 1,
            userSelect: 'all',
          }}
        >
          {password}
        </Box>
        <Alert severity="warning">{t('staff.passwordOnce')}</Alert>
      </DialogContent>
      <DialogActions>
        <Button onClick={copy}>{copied ? t('staff.copied') : t('staff.copy')}</Button>
        <Button
          variant="contained"
          onClick={() => {
            setCopied(false)
            onClose()
          }}
        >
          {t('staff.passwordDone')}
        </Button>
      </DialogActions>
    </Dialog>
  )
}
