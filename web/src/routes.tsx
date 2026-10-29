import type { ReactNode } from 'react'
import { Navigate, type RouteObject } from 'react-router'
import { CHANGE_PASSWORD_PATH, RequireAuth } from './auth/RequireAuth'
import { RequireRole } from './auth/RequireRole'
import { AppLayout } from './components/AppLayout'
import { ChangePasswordPage } from './pages/ChangePasswordPage'
import { HomePage } from './pages/HomePage'
import { LoginPage } from './pages/LoginPage'
import { CalendarPage } from './pages/structure/CalendarPage'
import { ClassesPage } from './pages/structure/ClassesPage'
import { PeriodsPage } from './pages/structure/PeriodsPage'
import { TimetablePage } from './pages/structure/TimetablePage'

const admin = (page: ReactNode) => <RequireRole role="ADMIN">{page}</RequireRole>

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
      // UC-A1: school structure, admin only
      { path: '/structure/calendar', element: admin(<CalendarPage />) },
      { path: '/structure/periods', element: admin(<PeriodsPage />) },
      { path: '/structure/classes', element: admin(<ClassesPage />) },
      { path: '/structure/timetable', element: admin(<TimetablePage />) },
    ],
  },
  { path: '*', element: <Navigate to="/" replace /> },
]
