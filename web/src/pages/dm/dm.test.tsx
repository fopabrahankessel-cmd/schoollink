import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { isoDate, type RiskAlert, type ScopeDashboard, type Settings, type StudentHistory } from '../../api/dm'
import { fakeFetch, json, newClient, renderApp, tokens } from '../../test/fakeApi'

// Generated data only: made-up names and matricules (CLAUDE.md).
const ALERT: RiskAlert = {
  id: 'a1',
  studentId: 's1',
  firstName: 'Awa',
  lastName: 'Exemple',
  matricule: 'FAKE001',
  classId: 'c1',
  className: '6e A',
  ruleType: 'FULL_DAY_ABSENCE',
  threshold: 1,
  asOf: '2026-10-28',
  evidence: { dates: ['2026-10-28'] },
  status: 'OPEN',
  raisedAt: '2026-10-28T16:00:00Z',
  decision: null,
  decisionNote: null,
  decidedAt: null,
}
const CLOSED: RiskAlert = {
  ...ALERT,
  id: 'a2',
  firstName: 'Bruno',
  ruleType: 'UNJUSTIFIED_ABSENCES',
  threshold: 6,
  evidence: { periods: 6, from: '2026-09-07', to: '2026-10-27' },
  status: 'CLOSED',
  decision: 'DISMISS',
  decisionNote: 'Certificat apporté',
  decidedAt: '2026-10-29T09:00:00Z',
}
const HISTORY: StudentHistory = {
  studentId: 's1',
  matricule: 'FAKE001',
  firstName: 'Awa',
  lastName: 'Exemple',
  classId: 'c1',
  className: '6e A',
  from: '2026-10-03',
  to: '2026-11-01',
  totals: { absentPeriods: 2, unjustifiedAbsentPeriods: 1, lateness: 1, unjustifiedLateness: 1 },
  days: [
    {
      date: '2026-10-28',
      absences: [
        { recordId: 'r1', periodNumber: 1, startTime: '07:30:00', justified: false },
        { recordId: 'r2', periodNumber: 2, startTime: '08:30:00', justified: true },
      ],
      lateness: { recordId: 'l1', arrivalTime: '07:52:00', justified: false },
    },
  ],
}
const SETTINGS: Settings = {
  startTime: '07:30:00',
  digestTimes: ['17:00:00'],
  rules: [
    { type: 'FULL_DAY_ABSENCE', count: 1, active: true, min: 1, max: 5 },
    { type: 'CONSECUTIVE_LATENESS', count: 3, active: true, min: 2, max: 20 },
    { type: 'UNJUSTIFIED_ABSENCES', count: 6, active: true, min: 1, max: 200 },
  ],
}

function signedIn(script: Parameters<typeof fakeFetch>[0], roles: ('DM' | 'ADMIN')[] = ['DM']) {
  const api = fakeFetch(script)
  const client = newClient(api.fetch)
  client.startSession(tokens({ roles }))
  return { api, client }
}

const refusal = (message: string) => json(409, { code: 'conflict', message })

describe('DM alerts (UC-D2, D30)', () => {
  it('lists open alerts with what the rule saw, and a summons with the default meeting sends the decision alone', async () => {
    const { api, client } = signedIn({
      'GET /alerts?status=open': [json(200, [ALERT]), json(200, [])],
      'POST /alerts/a1/decision': json(200, { ...ALERT, status: 'CLOSED', decision: 'SUMMON' }),
    })
    renderApp(client, '/alerts')
    const user = userEvent.setup()

    const row = await screen.findByTestId('alert-a1')
    expect(within(row).getByRole('link', { name: 'Exemple Awa' })).toHaveAttribute('href', '/students/s1')
    expect(within(row).getByText('Absent toute la journée')).toBeInTheDocument()
    expect(within(row).getByText(/^Absent à tous les cours le/)).toBeInTheDocument()

    await user.click(within(row).getByRole('button', { name: 'Convoquer les parents' }))
    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).getByText('Convoquer les parents de Awa')).toBeInTheDocument()
    await user.click(within(dialog).getByRole('button', { name: 'Convoquer les parents' }))

    expect(await screen.findByText('Les parents de Exemple Awa sont convoqués.')).toBeInTheDocument()
    expect(api.calls.find((c) => c.method === 'POST')?.body).toEqual({ decision: 'SUMMON' })
    expect(await screen.findByText('Aucune alerte à traiter.')).toBeInTheDocument()
  })

  it('needs a reason to dismiss, and shows the server refusal inside the dialog', async () => {
    const { api, client } = signedIn({
      'GET /alerts?status=open': json(200, [ALERT]),
      'POST /alerts/a1/decision': refusal('Cette alerte est déjà traitée.'),
    })
    renderApp(client, '/alerts')
    const user = userEvent.setup()

    const row = await screen.findByTestId('alert-a1')
    await user.click(within(row).getByRole('button', { name: 'Autre décision' }))
    await user.click(await screen.findByRole('menuitem', { name: 'Classer sans suite' }))
    const dialog = await screen.findByRole('dialog')
    const submit = within(dialog).getByRole('button', { name: 'Classer sans suite' })
    expect(submit).toBeDisabled()

    await user.type(within(dialog).getByLabelText(/Motif du classement/), 'Erreur de saisie')
    await user.click(submit)
    expect(await within(dialog).findByText('Cette alerte est déjà traitée.')).toBeInTheDocument()
    expect(api.calls.find((c) => c.method === 'POST')?.body).toEqual({ decision: 'DISMISS', note: 'Erreur de saisie' })
  })

  it('a summons on a chosen day sends that day and time', async () => {
    const { api, client } = signedIn({
      'GET /alerts?status=open': json(200, [ALERT]),
      'POST /alerts/a1/decision': json(200, {}),
    })
    renderApp(client, '/alerts')
    const user = userEvent.setup()

    await user.click(within(await screen.findByTestId('alert-a1')).getByRole('button', { name: 'Convoquer les parents' }))
    const dialog = await screen.findByRole('dialog')
    await user.click(within(dialog).getByLabelText('Choisir le jour et l\'heure'))
    const submit = within(dialog).getByRole('button', { name: 'Convoquer les parents' })
    expect(submit).toBeDisabled() // no day yet
    await user.type(within(dialog).getByLabelText(/^Jour/), '2026-11-04')
    await user.click(submit)

    await waitFor(() =>
      expect(api.calls.find((c) => c.method === 'POST')?.body).toEqual({
        decision: 'SUMMON',
        meetingDate: '2026-11-04',
        meetingTime: '08:00',
      }),
    )
  })

  it('shows handled alerts with the decision and its note', async () => {
    const { client } = signedIn({
      'GET /alerts?status=open': json(200, []),
      'GET /alerts?status=closed': json(200, [CLOSED]),
    })
    renderApp(client, '/alerts')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('tab', { name: 'Traitées' }))
    const row = await screen.findByTestId('alert-a2')
    expect(within(row).getByText('Classer sans suite')).toBeInTheDocument()
    expect(within(row).getByText('Certificat apporté')).toBeInTheDocument()
    expect(within(row).getByText(/^6 cours manqués sans justification du/)).toBeInTheDocument()
    expect(within(row).queryByRole('button')).not.toBeInTheDocument()
  })
})

describe('DM student history (UC-D1, UC-D6, UC-D8)', () => {
  it('justifies the ticked absence and lateness together; an illness sends no note (NFR-10)', async () => {
    const { api, client } = signedIn({
      'GET /students/s1/history': json(200, HISTORY),
      'POST /justifications': json(201, {}),
    })
    renderApp(client, '/students/s1')
    const user = userEvent.setup()

    expect(await screen.findByRole('heading', { name: 'Exemple Awa' })).toBeInTheDocument()
    const table = screen.getByRole('table')
    // a justified record cannot be ticked again
    expect(within(table).getAllByRole('checkbox')).toHaveLength(3) // select all + 2 unjustified
    await user.click(within(table).getByRole('checkbox', { name: /Choisir tous/ }))
    expect(screen.getByText('2 enregistrements choisis')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Justifier' }))
    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).getByText('Justifier 2 enregistrements')).toBeInTheDocument()
    await user.selectOptions(within(dialog).getByLabelText(/^Motif/), 'ILLNESS')
    expect(within(dialog).queryByLabelText('Note (facultative)')).not.toBeInTheDocument()
    await user.click(within(dialog).getByRole('button', { name: 'Justifier' }))

    expect(await screen.findByText('2 enregistrements justifiés.')).toBeInTheDocument()
    expect(api.calls.find((c) => c.method === 'POST')?.body).toEqual({
      reason: 'ILLNESS',
      absenceIds: ['r1'],
      latenessIds: ['l1'],
    })
  })

  it('cancels a record with a mandatory reason (BR-13)', async () => {
    const { api, client } = signedIn({
      'GET /students/s1/history': json(200, HISTORY),
      'POST /lateness/l1/cancel': json(200, {}),
    })
    renderApp(client, '/students/s1')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: /^Annuler cet enregistrement : .*En retard, arrivé à 07:52$/ }))
    const dialog = await screen.findByRole('dialog')
    const submit = within(dialog).getByRole('button', { name: "Annuler l'enregistrement" })
    expect(submit).toBeDisabled()
    await user.type(within(dialog).getByLabelText(/Motif de l'annulation/), 'Mauvais élève')
    await user.click(submit)

    expect(await screen.findByText('Enregistrement annulé.')).toBeInTheDocument()
    expect(api.calls.find((c) => c.method === 'POST')).toMatchObject({ path: '/lateness/l1/cancel', body: { reason: 'Mauvais élève' } })
  })

  it('summons the parents on the DM\'s own initiative, with a reason (UC-D3)', async () => {
    const { api, client } = signedIn({
      'GET /students/s1/history': json(200, HISTORY),
      'POST /summons': json(201, {}),
    })
    renderApp(client, '/students/s1')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Convoquer les parents' }))
    const dialog = await screen.findByRole('dialog')
    await user.type(within(dialog).getByLabelText(/^Motif/), 'Bavardages répétés')
    await user.click(within(dialog).getByRole('button', { name: 'Convoquer' }))

    expect(await screen.findByText('Les parents sont convoqués.')).toBeInTheDocument()
    expect(api.calls.find((c) => c.method === 'POST')?.body).toEqual({ studentId: 's1', reason: 'Bavardages répétés' })
  })

  it('finds a student of the DM\'s classes by name (D35)', async () => {
    const { client } = signedIn({
      'GET /lateness/students?q=aw': json(200, [
        { id: 's1', matricule: 'FAKE001', firstName: 'Awa', lastName: 'Exemple', classId: 'c1', className: '6e A' },
      ]),
    })
    renderApp(client, '/students')
    const user = userEvent.setup()

    expect(screen.getByText('Tapez au moins deux lettres.')).toBeInTheDocument()
    await user.type(screen.getByLabelText('Nom ou matricule'), 'aw')
    expect(await screen.findByRole('link', { name: 'Exemple Awa' })).toHaveAttribute('href', '/students/s1')
  })
})

describe('DM settings (UC-D7, D28)', () => {
  it('saves the thresholds, the start time and a second digest', async () => {
    const { api, client } = signedIn({
      'GET /settings/thresholds': json(200, SETTINGS),
      'PUT /settings/thresholds': json(200, SETTINGS),
    })
    renderApp(client, '/settings')
    const user = userEvent.setup()

    const lateness = await screen.findByLabelText('Jours de classe de suite')
    await user.clear(lateness)
    await user.type(lateness, '4')
    await user.click(screen.getAllByRole('switch')[2])
    await user.click(screen.getByRole('button', { name: 'Ajouter un second récapitulatif' }))
    await user.click(screen.getByRole('button', { name: 'Enregistrer' }))

    expect(await screen.findByText('Réglages enregistrés.')).toBeInTheDocument()
    expect(api.calls.find((c) => c.method === 'PUT')?.body).toEqual({
      startTime: '07:30',
      digestTimes: ['17:00', '18:00'],
      rules: [
        { type: 'FULL_DAY_ABSENCE', count: 1, active: true },
        { type: 'CONSECUTIVE_LATENESS', count: 4, active: true },
        { type: 'UNJUSTIFIED_ABSENCES', count: 6, active: false },
      ],
    })
  })

  it('will not save a count outside the bounds or a second digest before the first', async () => {
    const { client } = signedIn({ 'GET /settings/thresholds': json(200, { ...SETTINGS, digestTimes: ['17:00:00', '16:00:00'] }) })
    renderApp(client, '/settings')
    const user = userEvent.setup()

    const days = await screen.findByLabelText('Jours de suite')
    await user.clear(days)
    await user.type(days, '9')
    expect(screen.getAllByText('Entre 1 et 5').length).toBeGreaterThan(0)
    expect(screen.getByText('Après le premier récapitulatif')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Enregistrer' })).toBeDisabled()
  })
})

describe('DM home and access (UC-D8, D34, NFR-8)', () => {
  const today = new Date()
  const from = new Date(today)
  from.setDate(from.getDate() - 29)
  const path = `/dashboard/scope?from=${isoDate(from)}&to=${isoDate(today)}`
  const counts = { sessions: 4, studentPeriods: 200, absentPeriods: 10, unjustifiedAbsentPeriods: 4, lateness: 3, unjustifiedLateness: 2 }
  const DASHBOARD: ScopeDashboard = {
    from: isoDate(from),
    to: isoDate(today),
    totals: counts,
    days: [{ date: isoDate(today), counts }],
    classes: [{ classId: 'c1', name: '6e A', counts }],
    alerts: [
      { rule: 'FULL_DAY_ABSENCE', open: 2, raised: 3, closed: 1 },
      { rule: 'CONSECUTIVE_LATENESS', open: 1, raised: 1, closed: 0 },
      { rule: 'UNJUSTIFIED_ABSENCES', open: 0, raised: 0, closed: 0 },
    ],
  }

  it("opens on the DM's classes over the last 30 days", async () => {
    const { client } = signedIn({ [`GET ${path}`]: json(200, DASHBOARD) })
    renderApp(client, '/')

    expect(await screen.findByRole('heading', { name: 'Vos classes' })).toBeInTheDocument()
    const figures = await screen.findByText("Taux d'absence", { selector: 'dt' })
    expect(figures.nextElementSibling).toHaveTextContent('5 %')
    expect(screen.getByText('Alertes à traiter', { selector: 'dt' }).nextElementSibling).toHaveTextContent('3')
    const byClass = screen.getByRole('table', { name: 'Par classe' })
    expect(within(byClass).getByText('6e A')).toBeInTheDocument()
    expect(screen.getByRole('navigation', { name: 'Navigation principale' })).toHaveTextContent('Alertes')
  })

  it('keeps an administrator without the DM role away from the DM screens', async () => {
    const { client } = signedIn({ 'GET /academic-years': json(200, []) }, ['ADMIN'])
    renderApp(client, '/alerts')
    await waitFor(() => expect(screen.queryByRole('heading', { name: 'Alertes' })).not.toBeInTheDocument())
    expect(screen.getByRole('navigation', { name: 'Navigation principale' })).not.toHaveTextContent('Alertes')
  })
})
