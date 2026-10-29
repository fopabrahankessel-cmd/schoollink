import { createTheme } from '@mui/material/styles'

export const theme = createTheme({
  palette: {
    primary: { main: '#1d5d9b' },
    secondary: { main: '#e07a1f' },
    background: { default: '#f4f6f9' },
  },
  shape: { borderRadius: 8 },
  components: {
    MuiButton: { defaultProps: { disableElevation: true } },
    MuiTextField: { defaultProps: { fullWidth: true, margin: 'normal' } },
  },
})
