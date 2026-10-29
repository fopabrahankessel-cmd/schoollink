import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { fakeFetch, json, newClient, renderApp, tokens } from '../test/fakeApi'

// Generated data only. The year spans every test run date: 2000-2100.
const YEAR = { id: 'y1', label: '2026-2027', startDate: '2000-01-01', endDate: '2100-12-31' }
const TERM = { id: 't1', academicYearId: 'y1', label: 'Trimestre 1', startDate: '2000-01-01', endDate: '2100-12-31' }
const P1 = { id: 'p1', number: 1, startTime: '07:30:00', endTime: '08:25:00' }
const P2 = { id: 'p2', number: 2, startTime: '08:25:00', endTime: '09:20:00' }
const CLASS_A = { id: 'c1', academicYearId: 'y1', blockId: 'b1', name: '6e A', level: '6e' }
const CLASS_B = { id: 'c2', academicYearId: 'y1', blockId: 'b1', name: '5e B', level: '5e' }
const SLOT = { id: 's1', classId: 'c1', weekday: 1, periodId: 'p1', teacherId: 'u1', subject: 'Mathématiques' }
const teacher = (id: string, teaching: unknown[]) => ({
  id,
  firstName: 'Awa',
  lastName: `Fictive ${id}`,
  phone: null,
  email: `${id}@demo.test`,
  language: 'FR',
  status: 'ACTIVE',
  passwordChangeRequired: false,
  roles: [{ id: `r-${id}`, role: 'TEACHER', blockId: null }],
  teaching,
})

describe('year setup overview (UC-A1, UC-A2)', () => {
  it('shows what is in place and sends the admin to the first step left', async () => {
    const api = fakeFetch({
      'GET /auth/schools': json(200, [{ id: tokens().user.schoolId, name: 'Lycée Démo A (fictif)' }]),
      'GET /academic-years': json(200, [YEAR]),
      'GET /terms?academicYearId=y1': json(200, [TERM]),
      'GET /periods': json(200, [P1, P2]),
      'GET /blocks': json(200, [{ id: 'b1', name: 'Bloc A' }]),
      'GET /classes?academicYearId=y1': json(200, [CLASS_A, CLASS_B]),
      'GET /timetable-slots': json(200, [SLOT, { ...SLOT, id: 's9', classId: 'c-other-year' }]),
      'GET /staff': json(200, [teacher('u1', [{ id: 'a1', classId: 'c1', subject: 'Mathématiques' }]), teacher('u2', [])]),
    })
    const client = newClient(api.fetch)
    client.startSession(tokens())
    renderApp(client, '/')

    expect(await screen.findByRole('heading', { name: "Préparation de l'année" })).toBeInTheDocument()
    const steps = screen.getByRole('region', { name: 'Étapes de la mise en place' })
    expect(await within(steps).findByText('Année 2026-2027, 1 trimestre')).toBeInTheDocument()
    expect(within(steps).getByText('2 périodes de cours, de 07:30 à 09:20')).toBeInTheDocument()
    expect(within(steps).getByText('2 classes cette année dans 1 bloc')).toBeInTheDocument()
    // only this year's classes count: 1 of 2 classes x 2 periods x 5 days
    expect(within(steps).getByText('1 cours placé sur 20 créneaux de la semaine')).toBeInTheDocument()
    expect(within(steps).getByText("1 classe n'a encore aucun cours.")).toBeInTheDocument()
    expect(within(steps).getByText("1 enseignant n'a encore aucune classe.")).toBeInTheDocument()

    expect(screen.getByText("Encore 2 étapes avant que les enseignants puissent faire l'appel.")).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Continuer : Emploi du temps' })).toHaveAttribute('href', '/structure/timetable')
    expect(await screen.findByText('Lycée Démo A (fictif)')).toBeInTheDocument() // the scope, in the sidebar
  })

  it('starts a new school with the calendar', async () => {
    const api = fakeFetch({
      'GET /auth/schools': json(200, []),
      'GET /academic-years': json(200, []),
      'GET /periods': json(200, []),
      'GET /blocks': json(200, []),
      'GET /timetable-slots': json(200, []),
      'GET /staff': json(200, []),
    })
    const client = newClient(api.fetch)
    client.startSession(tokens())
    renderApp(client, '/')

    expect(await screen.findByText("Aucune année scolaire n'est encore créée.")).toBeInTheDocument()
    expect(screen.getByText('Créez l\'année scolaire dans le calendrier.')).toBeInTheDocument()
    expect(await screen.findByRole('link', { name: 'Continuer : Calendrier' })).toHaveAttribute('href', '/structure/calendar')
  })
})
