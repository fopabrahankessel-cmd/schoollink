import { CssBaseline, ThemeProvider } from '@mui/material'
import { useMemo } from 'react'
import { createBrowserRouter, RouterProvider } from 'react-router'
import { ApiClient } from './api/client'
import { AuthProvider } from './auth/AuthContext'
import i18n from './i18n'
import { routes } from './routes'
import { theme } from './theme'

export default function App() {
  const client = useMemo(() => new ApiClient({ language: () => i18n.resolvedLanguage ?? 'fr' }), [])
  const router = useMemo(() => createBrowserRouter(routes), [])
  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <AuthProvider client={client}>
        <RouterProvider router={router} />
      </AuthProvider>
    </ThemeProvider>
  )
}
