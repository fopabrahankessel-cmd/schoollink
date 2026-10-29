import { Navigate, type RouteObject } from 'react-router'
import { CHANGE_PASSWORD_PATH, RequireAuth } from './auth/RequireAuth'
import { AppLayout } from './components/AppLayout'
import { ChangePasswordPage } from './pages/ChangePasswordPage'
import { HomePage } from './pages/HomePage'
import { LoginPage } from './pages/LoginPage'

export const routes: RouteObject[] = [
  { path: '/login', element: <LoginPage /> },
  {
    element: (
      <RequireAuth>
        <AppLayout />
      </RequireAuth>
    ),
    children: [
      { index: true, element: <HomePage /> },
      { path: CHANGE_PASSWORD_PATH, element: <ChangePasswordPage /> },
    ],
  },
  { path: '*', element: <Navigate to="/" replace /> },
]
