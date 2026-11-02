import type { ReactNode } from 'react'
import { Navigate, type RouteObject } from 'react-router'
import { CHANGE_PASSWORD_PATH, RequireAuth } from './auth/RequireAuth'
import { RequireRole } from './auth/RequireRole'
import { AppLayout } from './components/AppLayout'
import { ChangePasswordPage } from './pages/ChangePasswordPage'
import { GuardiansPage } from './pages/guardians/GuardiansPage'
import { ImportPreviewPage } from './pages/import/ImportPreviewPage'
import { ImportUploadPage } from './pages/import/ImportUploadPage'
import { HomePage } from './pages/HomePage'
import { SchoolDashboardPage } from './pages/dashboard/SchoolDashboardPage'
import { LoginPage } from './pages/LoginPage'
import { AlertsPage } from './pages/dm/AlertsPage'
import { SettingsPage } from './pages/dm/SettingsPage'
import { StudentHistoryPage } from './pages/dm/StudentHistoryPage'
import { StudentsPage } from './pages/dm/StudentsPage'
import { CalendarPage } from './pages/structure/CalendarPage'
import { ClassesPage } from './pages/structure/ClassesPage'
import { LetterheadPage } from './pages/structure/LetterheadPage'
import { PeriodsPage } from './pages/structure/PeriodsPage'
import { TimetablePage } from './pages/structure/TimetablePage'
import { StaffDetailPage } from './pages/staff/StaffDetailPage'
import { StaffListPage } from './pages/staff/StaffListPage'

const admin = (page: ReactNode) => <RequireRole role="ADMIN">{page}</RequireRole>
const dm = (page: ReactNode) => <RequireRole role="DM">{page}</RequireRole>

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
      // UC-A6: the school dashboard, admin only
      { path: '/dashboard', element: admin(<SchoolDashboardPage />) },
      // UC-A1: school structure, admin only
      { path: '/structure/calendar', element: admin(<CalendarPage />) },
      { path: '/structure/periods', element: admin(<PeriodsPage />) },
      { path: '/structure/classes', element: admin(<ClassesPage />) },
      { path: '/structure/timetable', element: admin(<TimetablePage />) },
      // UC-A7: the letterhead of the DM's reports, admin only
      { path: '/structure/letterhead', element: admin(<LetterheadPage />) },
      // UC-A2: staff accounts and assignments, admin only
      { path: '/staff', element: admin(<StaffListPage />) },
      { path: '/staff/:id', element: admin(<StaffDetailPage />) },
      // UC-A3: student and guardian import, admin only
      { path: '/students/import', element: admin(<ImportUploadPage />) },
      { path: '/students/import/:id', element: admin(<ImportPreviewPage />) },
      // UC-A4: guardians and invitation codes, admin only
      { path: '/guardians', element: admin(<GuardiansPage />) },
      // UC-D1, UC-D2, UC-D6, UC-D7, UC-D8: the DM's alerts, students and settings; the home is the scope dashboard
      { path: '/alerts', element: dm(<AlertsPage />) },
      { path: '/students', element: dm(<StudentsPage />) },
      { path: '/students/:id', element: dm(<StudentHistoryPage />) },
      { path: '/settings', element: dm(<SettingsPage />) },
    ],
  },
  { path: '*', element: <Navigate to="/" replace /> },
]
