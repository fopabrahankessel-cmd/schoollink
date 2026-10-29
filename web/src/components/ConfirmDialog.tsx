import { Button, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle } from '@mui/material'
import { useTranslation } from 'react-i18next'

export function ConfirmDialog({
  open,
  title,
  text,
  confirmLabel,
  danger = false,
  onConfirm,
  onClose,
}: {
  open: boolean
  title: string
  text: string
  confirmLabel: string
  danger?: boolean
  onConfirm: () => void
  onClose: () => void
}) {
  const { t } = useTranslation()
  return (
    <Dialog open={open} onClose={onClose}>
      <DialogTitle>{title}</DialogTitle>
      <DialogContent>
        <DialogContentText>{text}</DialogContentText>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>{t('crud.cancel')}</Button>
        <Button
          variant="contained"
          color={danger ? 'error' : 'primary'}
          onClick={() => {
            onClose()
            onConfirm()
          }}
        >
          {confirmLabel}
        </Button>
      </DialogActions>
    </Dialog>
  )
}
