import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { fakeFetch, json, newClient, renderApp, tokens } from '../../test/fakeApi'

// Generated data only.
const YEAR = { id: 'y1', label: '2026-2027', startDate: '2026-09-07', endDate: '2027-06-30' }
const TERM = { id: 't1', academicYearId: 'y1', label: 'Trimestre 1', startDate: '2026-09-07', endDate: '2026-12-18' }
const P1 = { id: 'p1', number: 1, startTime: '07:30:00', endTime: '08:25:00' }
const P2 = { id: 'p2', number: 2, startTime: '08:25:00', endTime: '09:20:00' }
const CLASS_A = { id: 'c1', academicYearId: 'y1', blockId: 'b1', name: '6e A', level: '6e' }
const TEACHER = {
  id: 'u1',
  firstName: 'Awa',
  lastName: 'Fictive',
  roles: [{ id: 'r1', role: 'TEACHER', blockId: null }],
  teaching: [
    { id: 'a1', classId: 'c1', subject: 'Mathématiques' },
    { id: 'a2', classId: 'c9', subject: 'Physique' },
  ],
}
const noContent = () => new Response(null, { status: 204 })

function signedIn(script: Parameters<typeof fakeFetch>[0], roles: ('ADMIN' | 'DM')[] = ['ADMIN']) {
  const api = fakeFetch(script)
  const client = newClient(api.fetch)
  client.startSession(tokens({ roles }))
  return { api, client }
}

describe('school structure screens (UC-A1)', () => {
  it('lists periods and adds one, with the number sent as a number', async () => {
    const { api, client } = signedIn({
      'GET /periods': [json(200, [P1]), json(200, [P1, P2])],
      'POST /periods': json(201, P2),
    })
    renderApp(client, '/structure/periods')

    expect(await screen.findByRole('cell', { name: '07:30' })).toBeInTheDocument()
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: 'Ajouter' }))
    const dialog = await screen.findByRole('dialog', { name: 'Ajouter : Période de cours' })
    await user.type(within(dialog).getByLabelText(/Numéro/), '2')
    await user.type(within(dialog).getByLabelText(/Heure de début/), '08:25')
    await user.type(within(dialog).getByLabelText(/Heure de fin/), '09:20')
    await user.click(within(dialog).getByRole('button', { name: 'Enregistrer' }))

    expect(await screen.findByRole('cell', { name: '09:20' })).toBeInTheDocument()
    expect(api.calls.find((c) => c.method === 'POST')?.body).toEqual({ number: 2, startTime: '08:25', endTime: '09:20' })
  })

  it("shows the server's message on the field it concerns, and keeps the form open", async () => {
    const { client } = signedIn({
      'GET /periods': json(200, []),
      'POST /periods': json(400, {
        code: 'validation.failed',
        message: 'Données invalides.',
        details: [{ code: 'validation.date_order', message: 'La fin doit venir après le début.', field: 'endTime' }],
      }),
    })
    renderApp(client, '/structure/periods')

    expect(await screen.findByText("Aucune période de cours pour l'instant.")).toBeInTheDocument()
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: 'Ajouter' }))
    const dialog = await screen.findByRole('dialog')
    await user.type(within(dialog).getByLabelText(/Numéro/), '1')
    await user.type(within(dialog).getByLabelText(/Heure de début/), '09:00')
    await user.type(within(dialog).getByLabelText(/Heure de fin/), '08:00')
    await user.click(within(dialog).getByRole('button', { name: 'Enregistrer' }))

    expect(await within(dialog).findByText('La fin doit venir après le début.')).toBeInTheDocument()
    expect(within(dialog).getByLabelText(/Heure de fin/)).toHaveAttribute('aria-invalid', 'true')
  })

  it('asks before deleting, and says when the item is still in use', async () => {
    const { api, client } = signedIn({
      'GET /periods': json(200, [P1]),
      'DELETE /periods/p1': json(409, { code: 'in_use', message: 'Cet élément est encore utilisé.' }),
    })
    renderApp(client, '/structure/periods')

    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Supprimer' }))
    const confirm = await screen.findByRole('dialog', { name: 'Supprimer cet élément ?' })
    expect(api.calls.some((c) => c.method === 'DELETE')).toBe(false)
    await user.click(within(confirm).getByRole('button', { name: 'Supprimer' }))

    expect(await screen.findByText('Cet élément est encore utilisé.')).toBeInTheDocument()
    expect(await screen.findByRole('cell', { name: '07:30' })).toBeInTheDocument() // still listed
  })

  it('shows the terms of the selected year and adds a term to it', async () => {
    const { api, client } = signedIn({
      'GET /academic-years': json(200, [YEAR]),
      'GET /terms?academicYearId=y1': [json(200, []), json(200, [TERM])],
      'POST /terms': json(201, TERM),
    })
    renderApp(client, '/structure/calendar')

    expect(screen.getByText('Sélectionnez une année scolaire pour voir ses trimestres.')).toBeInTheDocument()
    const user = userEvent.setup()
    await user.click(await screen.findByRole('cell', { name: '2026-2027' }))
    const terms = await screen.findByRole('region', { name: /Trimestres · 2026-2027/ })
    expect(screen.getAllByRole('region', { name: /Trimestres/ })).toHaveLength(1) // no stale section
    expect(await within(terms).findByText('Aucun trimestre dans cette année.')).toBeInTheDocument()

    await user.click(within(terms).getByRole('button', { name: 'Ajouter' }))
    const dialog = await screen.findByRole('dialog', { name: 'Ajouter : Trimestre' })
    await user.type(within(dialog).getByLabelText(/Libellé/), 'Trimestre 1')
    await user.type(within(dialog).getByLabelText(/Début/), '2026-09-07')
    await user.type(within(dialog).getByLabelText(/Fin/), '2026-12-18')
    await user.click(within(dialog).getByRole('button', { name: 'Enregistrer' }))

    expect(await within(terms).findByRole('cell', { name: 'Trimestre 1' })).toBeInTheDocument()
    expect(api.calls.find((c) => c.path === '/terms' && c.method === 'POST')?.body).toEqual({
      academicYearId: 'y1',
      label: 'Trimestre 1',
      startDate: '2026-09-07',
      endDate: '2026-12-18',
    })
  })

  it("fills a timetable cell with one of the class's assigned teachers", async () => {
    const { api, client } = signedIn({
      'GET /academic-years': json(200, [YEAR]),
      'GET /periods': json(200, [P1, P2]),
      'GET /staff': json(200, [TEACHER]),
      'GET /classes?academicYearId=y1': json(200, [CLASS_A]),
      'GET /timetable-slots?classId=c1': [
        json(200, [{ id: 's1', classId: 'c1', weekday: 1, periodId: 'p1', teacherId: 'u1', subject: 'Mathématiques' }]),
        json(200, [
          { id: 's1', classId: 'c1', weekday: 1, periodId: 'p1', teacherId: 'u1', subject: 'Mathématiques' },
          { id: 's2', classId: 'c1', weekday: 2, periodId: 'p2', teacherId: 'u1', subject: 'Mathématiques' },
        ]),
      ],
      'POST /timetable-slots': json(201, {}),
    })
    renderApp(client, '/structure/timetable')

    const user = userEvent.setup()
    await waitFor(() => expect(screen.getByLabelText(/Année scolaire/)).toHaveValue('y1'))
    await user.selectOptions(await screen.findByLabelText(/Classe/), 'c1')
    expect(await screen.findByRole('button', { name: 'Lundi, période 1 : Mathématiques, Awa Fictive' })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Mardi, période 2 : Libre' }))
    const dialog = await screen.findByRole('dialog', { name: 'Mardi, période 2' })
    const lesson = within(dialog).getByLabelText(/Cours/)
    // only this class's assignments
    expect(within(lesson).getAllByRole('option').map((o) => o.textContent)).toEqual([
      'Choisir…',
      'Mathématiques · Awa Fictive',
    ])
    await user.selectOptions(lesson, 'u1|Mathématiques')
    await user.click(within(dialog).getByRole('button', { name: 'Enregistrer' }))

    expect(await screen.findByRole('button', { name: 'Mardi, période 2 : Mathématiques, Awa Fictive' })).toBeInTheDocument()
    expect(api.calls.find((c) => c.method === 'POST')?.body).toEqual({
      classId: 'c1',
      weekday: 2,
      periodId: 'p2',
      teacherId: 'u1',
      subject: 'Mathématiques',
    })
  })

  it('shows why the server refused a lesson, and clears a slot', async () => {
    const slot = { id: 's1', classId: 'c1', weekday: 1, periodId: 'p1', teacherId: 'u1', subject: 'Mathématiques' }
    const { api, client } = signedIn({
      'GET /academic-years': json(200, [YEAR]),
      'GET /periods': json(200, [P1]),
      'GET /staff': json(200, [TEACHER]),
      'GET /classes?academicYearId=y1': json(200, [CLASS_A]),
      'GET /timetable-slots?classId=c1': [json(200, [slot]), json(200, [])],
      'PUT /timetable-slots/s1': json(409, {
        code: 'timetable.teacher_busy',
        message: 'Cet enseignant a déjà cours dans une autre classe à cette période.',
      }),
      'DELETE /timetable-slots/s1': noContent,
    })
    renderApp(client, '/structure/timetable')

    const user = userEvent.setup()
    await waitFor(() => expect(screen.getByLabelText(/Année scolaire/)).toHaveValue('y1'))
    await user.selectOptions(await screen.findByLabelText(/Classe/), 'c1')
    await user.click(await screen.findByRole('button', { name: /Lundi, période 1 : Mathématiques/ }))
    const dialog = await screen.findByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: 'Enregistrer' }))
    expect(await within(dialog).findByText(/Cet enseignant a déjà cours/)).toBeInTheDocument()

    await user.click(within(dialog).getByRole('button', { name: 'Libérer le créneau' }))
    expect(await screen.findByRole('button', { name: 'Lundi, période 1 : Libre' })).toBeInTheDocument()
    expect(api.calls.some((c) => c.method === 'DELETE' && c.path === '/timetable-slots/s1')).toBe(true)
  })

  it('keeps the structure screens to the admin', async () => {
    const { client } = signedIn({}, ['DM'])
    renderApp(client, '/structure/periods')

    expect(await screen.findByRole('heading', { name: 'Bienvenue sur SchoolLink' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Périodes' })).not.toBeInTheDocument()
  })

  it('shows the admin the structure screens in the navigation', async () => {
    const { client } = signedIn({})
    renderApp(client, '/')

    const nav = await screen.findByRole('navigation', { name: 'Navigation principale' })
    expect(within(nav).getByRole('link', { name: 'Emploi du temps' })).toHaveAttribute('href', '/structure/timetable')
  })
})
