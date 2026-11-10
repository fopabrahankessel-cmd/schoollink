import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { DayAttendance, DayPeriod } from '../../api/day'
import i18n from '../../i18n'
import { fakeFetch, json, newClient, renderApp, tokens } from '../../test/fakeApi'

// UC-D10, D40: the DM's day of attendance. Generated data only: made-up names (CLAUDE.md).
const absence = (id: string, first: string, state: 'UNJUSTIFIED' | 'JUSTIFIED' | 'CANCELLED' = 'UNJUSTIFIED') => ({
  recordId: id,
  studentId: `s-${id}`,
  firstName: first,
  lastName: 'Fictif',
  matricule: `TW-${id}`,
  state,
})
const period = (number: number, status: DayPeriod['status'], absences: DayPeriod['absences'] = []): DayPeriod => ({
  periodId: `p${number}`,
  number,
  startTime: ['07:30:00', '08:30:00', '10:15:00'][number - 1],
  endTime: ['08:25:00', '09:25:00', '11:10:00'][number - 1],
  subject: 'Maths',
  teacherName: 'Paul Enseignant',
  status,
  absentCount: absences.filter((a) => a.state !== 'CANCELLED').length,
  absences,
})
const day = (periods: DayPeriod[] = [period(1, 'RECORDED', [absence('a1', 'Awa'), absence('a2', 'Bruno')]), period(2, 'NOT_RECORDED'), period(3, 'UPCOMING')]): DayAttendance => ({
  date: '2026-11-10',
  schoolDay: true,
  totals: { notRecorded: 1, absentStudents: 2, lateness: 1 },
  classes: [
    {
      classId: 'c1',
      className: '6e A',
      periods,
      lateness: [
        { recordId: 'l1', studentId: 's-l1', firstName: 'Chantal', lastName: 'Fictif', matricule: 'TW-l1', arrivalTime: '07:52:00', state: 'UNJUSTIFIED' },
      ],
    },
  ],
})

function signedIn(script: Parameters<typeof fakeFetch>[0], roles: ('DM' | 'ADMIN')[] = ['DM'], language: 'FR' | 'EN' = 'FR') {
  const api = fakeFetch(script)
  const client = newClient(api.fetch)
  client.startSession(tokens({ roles, language }))
  return { api, client }
}

const posted = (api: ReturnType<typeof fakeFetch>) => api.calls.filter((c) => c.method === 'POST')
const reads = (api: ReturnType<typeof fakeFetch>) => api.calls.filter((c) => c.method === 'GET' && c.path.startsWith('/attendance/day'))

describe('DM day of attendance (UC-D10, D40)', () => {
  it('lists each class with its periods and their status', async () => {
    const { api, client } = signedIn({ 'GET /attendance/day': json(200, day()) })
    renderApp(client, '/day')

    expect(await screen.findByRole('heading', { name: '6e A' })).toBeInTheDocument()
    expect(screen.getByText('Saisie')).toBeInTheDocument()
    expect(screen.getByText('Non saisie')).toBeInTheDocument()
    expect(screen.getByText('À venir')).toBeInTheDocument()
    expect(screen.getByText('période non saisie')).toBeInTheDocument()
    expect(screen.getByText('élèves absents')).toBeInTheDocument()
    expect(screen.getByText('retard')).toBeInTheDocument()
    expect(reads(api)[0].path).toBe('/attendance/day')
  })

  it('justifies several absences of a period', async () => {
    const { api, client } = signedIn({ 'GET /attendance/day': json(200, day()), 'POST /justifications': json(201, {}) })
    renderApp(client, '/day')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Absents en P1' }))
    expect(screen.getByRole('button', { name: 'Absents en P1' })).toHaveAttribute('aria-expanded', 'true')
    await user.click(screen.getByRole('checkbox', { name: 'Awa Fictif' }))
    await user.click(screen.getByRole('checkbox', { name: 'Bruno Fictif' }))
    await user.click(screen.getByRole('button', { name: 'Justifier la sélection (2)' }))
    const dialog = await screen.findByRole('dialog')
    await user.selectOptions(within(dialog).getByLabelText(/^Motif/), 'ILLNESS')
    await user.click(within(dialog).getByRole('button', { name: 'Justifier' }))

    expect(await screen.findByText('2 enregistrements justifiés.')).toBeInTheDocument()
    expect(posted(api)[0]).toMatchObject({ path: '/justifications', body: { reason: 'ILLNESS', absenceIds: ['a1', 'a2'], latenessIds: [] } })
    await waitFor(() => expect(reads(api)).toHaveLength(2))
  })

  it('cancels an absence with a reason', async () => {
    const { api, client } = signedIn({ 'GET /attendance/day': json(200, day()), 'POST /records/a1/cancel': json(200, {}) })
    renderApp(client, '/day')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Absents en P1' }))
    await user.click(screen.getByRole('button', { name: /^Annuler cet enregistrement : Absence, Awa Fictif/ }))
    const dialog = await screen.findByRole('dialog')
    await user.type(within(dialog).getByLabelText(/Motif de l'annulation/), 'Erreur de saisie')
    await user.click(within(dialog).getByRole('button', { name: "Annuler l'enregistrement" }))

    expect(await screen.findByText('Enregistrement annulé.')).toBeInTheDocument()
    expect(posted(api)[0]).toMatchObject({ path: '/records/a1/cancel', body: { reason: 'Erreur de saisie' } })
    await waitFor(() => expect(reads(api)).toHaveLength(2))
  })

  it('shows a cancelled absence struck through, without actions', async () => {
    const { client } = signedIn({
      'GET /attendance/day': json(200, day([period(1, 'RECORDED', [absence('a1', 'Awa'), absence('a2', 'Bruno', 'CANCELLED')])])),
    })
    renderApp(client, '/day')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Absents en P1' }))
    expect(screen.getByText('Annulée')).toBeInTheDocument()
    expect(screen.queryByRole('checkbox', { name: 'Bruno Fictif' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Bruno Fictif/ })).not.toBeInTheDocument()
    expect(screen.getByText('Bruno Fictif')).toHaveStyle({ textDecoration: 'line-through' })
  })

  it('justifies the day\'s lateness from the class', async () => {
    const { api, client } = signedIn({ 'GET /attendance/day': json(200, day()), 'POST /justifications': json(201, {}) })
    renderApp(client, '/day')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('checkbox', { name: 'Chantal Fictif' }))
    await user.click(screen.getByRole('button', { name: 'Justifier la sélection (1)' }))
    const dialog = await screen.findByRole('dialog')
    await user.selectOptions(within(dialog).getByLabelText(/^Motif/), 'ILLNESS')
    await user.click(within(dialog).getByRole('button', { name: 'Justifier' }))

    await waitFor(() => expect(posted(api)).toHaveLength(1))
    expect(posted(api)[0].body).toEqual({ reason: 'ILLNESS', absenceIds: [], latenessIds: ['l1'] })
  })

  it('reads another day from the URL', async () => {
    const { api, client } = signedIn({ 'GET /attendance/day?date=2026-11-02': json(200, { ...day(), date: '2026-11-02' }) })
    renderApp(client, '/day?date=2026-11-02')

    expect(await screen.findByRole('heading', { name: '6e A' })).toBeInTheDocument()
    expect(reads(api)[0].path).toBe('/attendance/day?date=2026-11-02')
    expect(screen.getByLabelText('Jour')).toHaveValue('2026-11-02')
  })

  it('goes to the previous school day, skipping the weekend', async () => {
    const { api, client } = signedIn({
      'GET /attendance/day?date=2026-11-02': json(200, { ...day(), date: '2026-11-02' }),
      'GET /attendance/day?date=2026-10-30': json(200, { ...day(), date: '2026-10-30' }),
    })
    const router = renderApp(client, '/day?date=2026-11-02')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Jour précédent' }))

    await waitFor(() => expect(router.state.location.search).toBe('?date=2026-10-30'))
    await waitFor(() => expect(reads(api).at(-1)?.path).toBe('/attendance/day?date=2026-10-30'))
  })

  it('says when there are no lessons that day', async () => {
    const { client } = signedIn({
      'GET /attendance/day': json(200, { date: '2026-11-07', schoolDay: false, totals: { notRecorded: 0, absentStudents: 0, lateness: 0 }, classes: [] }),
    })
    renderApp(client, '/day')

    expect(await screen.findByText('Pas de cours ce jour-là.')).toBeInTheDocument()
  })

  it('is in the DM menu only', async () => {
    const { client } = signedIn({ 'GET /attendance/day': json(200, day()) })
    renderApp(client, '/day')
    expect(await screen.findByRole('link', { name: 'Présences du jour' })).toHaveAttribute('href', '/day')

    const admin = signedIn({}, ['ADMIN'])
    const router = renderApp(admin.client, '/day')
    await waitFor(() => expect(router.state.location.pathname).toBe('/'))
  })

  it('reads in English (NFR-12)', async () => {
    await i18n.changeLanguage('en')
    const { client } = signedIn({ 'GET /attendance/day': json(200, day()) }, ['DM'], 'EN')
    renderApp(client, '/day')

    expect(await screen.findByText('Not recorded')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: "Today's attendance" })).toBeInTheDocument()
  })
})
