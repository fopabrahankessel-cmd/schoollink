import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { isoDate, type DisciplinaryRecord, type RiskAlert, type StudentHistory } from '../../api/dm'
import i18n from '../../i18n'
import { fakeFetch, json, newClient, renderApp, tokens } from '../../test/fakeApi'

// UC-D5, D37: the Discipline tab of the DM's student page. Generated data only (CLAUDE.md).
const HISTORY: StudentHistory = {
  studentId: 's1',
  matricule: 'FAKE001',
  firstName: 'Awa',
  lastName: 'Exemple',
  classId: 'c1',
  className: '6e A',
  from: '2026-10-03',
  to: '2026-11-01',
  totals: { absentPeriods: 0, unjustifiedAbsentPeriods: 0, lateness: 0, unjustifiedLateness: 0 },
  days: [],
}
const FIGHT: DisciplinaryRecord = {
  id: 'd1',
  studentId: 's1',
  alertId: null,
  category: 'FIGHT',
  measure: 'DETENTION',
  occurredOn: '2026-10-30',
  description: 'Bagarre à la récréation',
  status: 'OPEN',
  closeNote: null,
  recordedByName: 'Marie Surveillante',
  recordedAt: '2026-10-30T11:00:00Z',
  closedAt: null,
}
const PHONE: DisciplinaryRecord = {
  ...FIGHT,
  id: 'd0',
  category: 'PHONE',
  measure: 'NONE',
  occurredOn: '2026-10-20',
  description: 'Téléphone en classe',
  status: 'CLOSED',
  closeNote: 'Téléphone rendu aux parents',
}
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

function signedIn(script: Parameters<typeof fakeFetch>[0]) {
  const api = fakeFetch(script)
  const client = newClient(api.fetch)
  client.startSession(tokens({ roles: ['DM'] }))
  return { api, client }
}

const tab = (name: string) => screen.findByRole('tab', { name })

describe('the Discipline tab (UC-D5, D37)', () => {
  it('shows History and Discipline tabs, each with its own URL', async () => {
    const { client } = signedIn({
      'GET /students/s1/history': json(200, HISTORY),
      'GET /students/s1/disciplinary-records': json(200, []),
    })
    const router = renderApp(client, '/students/s1')
    const user = userEvent.setup()

    expect(await tab('Historique')).toHaveAttribute('aria-selected', 'true')
    await user.click(await tab('Discipline'))
    await waitFor(() => expect(router.state.location.pathname).toBe('/students/s1/discipline'))
    expect(await tab('Discipline')).toHaveAttribute('aria-selected', 'true')
    expect(await screen.findByText('Aucun incident enregistré.')).toBeInTheDocument()
  })

  it('opens on the Discipline tab from its URL', async () => {
    const { client } = signedIn({
      'GET /students/s1/history': json(200, HISTORY),
      'GET /students/s1/disciplinary-records': json(200, []),
    })
    renderApp(client, '/students/s1/discipline')
    expect(await tab('Discipline')).toHaveAttribute('aria-selected', 'true')
  })

  it('lists incidents newest first with category, date, measure and status', async () => {
    const { client } = signedIn({
      'GET /students/s1/history': json(200, HISTORY),
      'GET /students/s1/disciplinary-records': json(200, [FIGHT, PHONE]),
    })
    renderApp(client, '/students/s1/discipline')

    const first = await screen.findByTestId('incident-d1')
    expect(within(first).getByText('Bagarre')).toBeInTheDocument()
    expect(within(first).getByText('Retenue')).toBeInTheDocument()
    expect(within(first).getByText('Bagarre à la récréation')).toBeInTheDocument()
    expect(within(first).getByText('Marie Surveillante')).toBeInTheDocument()
    expect(within(first).getByText('En cours')).toBeInTheDocument()
    expect(within(first).getByText(/30 oct/)).toBeInTheDocument()

    const second = screen.getByTestId('incident-d0')
    expect(within(second).getByText('Clôturé')).toBeInTheDocument()
    expect(within(second).getByText('Téléphone rendu aux parents')).toBeInTheDocument()
    const rows = screen.getAllByTestId(/^incident-/)
    expect(rows.map((r) => r.dataset.testid)).toEqual(['incident-d1', 'incident-d0'])
  })

  it('records an incident and shows it', async () => {
    const { api, client } = signedIn({
      'GET /students/s1/history': json(200, HISTORY),
      'GET /students/s1/disciplinary-records': [json(200, []), json(200, [FIGHT])],
      'POST /students/s1/disciplinary-records': json(201, FIGHT),
    })
    renderApp(client, '/students/s1/discipline')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Signaler un incident' }))
    const dialog = await screen.findByRole('dialog')
    const today = new Date()
    const earliest = new Date(today)
    earliest.setDate(earliest.getDate() - 30)
    const date = within(dialog).getByLabelText(/^Date de l'incident/)
    expect(date).toHaveValue(isoDate(today))
    expect(date).toHaveAttribute('min', isoDate(earliest))
    expect(date).toHaveAttribute('max', isoDate(today))

    await user.selectOptions(within(dialog).getByLabelText(/^Catégorie/), 'FIGHT')
    await user.selectOptions(within(dialog).getByLabelText(/^Mesure/), 'DETENTION')
    await user.clear(date)
    await user.type(date, '2026-10-30')
    await user.type(within(dialog).getByLabelText(/^Ce qui s'est passé/), '  Bagarre à la récréation ')
    await user.click(within(dialog).getByRole('button', { name: 'Signaler un incident' }))

    expect(await screen.findByText('Incident enregistré.')).toBeInTheDocument()
    expect(api.calls.find((c) => c.method === 'POST')?.body).toEqual({
      category: 'FIGHT',
      measure: 'DETENTION',
      occurredOn: '2026-10-30',
      description: 'Bagarre à la récréation',
    })
    expect(await screen.findByTestId('incident-d1')).toBeInTheDocument()
  })

  it('keeps the button disabled for a blank description or no category', async () => {
    const { client } = signedIn({
      'GET /students/s1/history': json(200, HISTORY),
      'GET /students/s1/disciplinary-records': json(200, []),
    })
    renderApp(client, '/students/s1/discipline')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Signaler un incident' }))
    const dialog = await screen.findByRole('dialog')
    const submit = within(dialog).getByRole('button', { name: 'Signaler un incident' })
    expect(submit).toBeDisabled()

    await user.type(within(dialog).getByLabelText(/^Ce qui s'est passé/), 'Insolence en classe')
    expect(submit).toBeDisabled() // no category yet

    await user.selectOptions(within(dialog).getByLabelText(/^Catégorie/), 'INSOLENCE')
    expect(submit).toBeEnabled()
    await user.clear(within(dialog).getByLabelText(/^Ce qui s'est passé/))
    await user.type(within(dialog).getByLabelText(/^Ce qui s'est passé/), '    ')
    expect(submit).toBeDisabled() // spaces only
  })

  it('closes with a note and cancels with a reason', async () => {
    const { api, client } = signedIn({
      'GET /students/s1/history': json(200, HISTORY),
      'GET /students/s1/disciplinary-records': json(200, [FIGHT]),
      'POST /disciplinary-records/d1/close': json(200, { ...FIGHT, status: 'CLOSED' }),
      'POST /disciplinary-records/d1/cancel': () => new Response(null, { status: 204 }),
    })
    renderApp(client, '/students/s1/discipline')
    const user = userEvent.setup()

    const row = await screen.findByTestId('incident-d1')
    await user.click(within(row).getByRole('button', { name: /^Clôturer l'incident/ }))
    let dialog = await screen.findByRole('dialog')
    await user.type(within(dialog).getByLabelText(/^Note de clôture/), 'Parents reçus')
    await user.click(within(dialog).getByRole('button', { name: 'Clôturer' }))
    expect(await screen.findByText('Incident clôturé.')).toBeInTheDocument()
    expect(api.calls.find((c) => c.path === '/disciplinary-records/d1/close')?.body).toEqual({ note: 'Parents reçus' })
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull())

    await user.click(within(screen.getByTestId('incident-d1')).getByRole('button', { name: /^Annuler l'incident/ }))
    dialog = await screen.findByRole('dialog')
    const confirm = within(dialog).getByRole('button', { name: "Annuler l'enregistrement" })
    expect(confirm).toBeDisabled()
    await user.type(within(dialog).getByLabelText(/^Motif de l'annulation/), "Erreur d'élève")
    await user.click(confirm)
    expect(await screen.findByText('Incident annulé.')).toBeInTheDocument()
    expect(api.calls.find((c) => c.path === '/disciplinary-records/d1/cancel')?.body).toEqual({ reason: "Erreur d'élève" })
  })

  it('a closed incident can be cancelled but not closed again', async () => {
    const { client } = signedIn({
      'GET /students/s1/history': json(200, HISTORY),
      'GET /students/s1/disciplinary-records': json(200, [PHONE]),
    })
    renderApp(client, '/students/s1/discipline')

    const row = await screen.findByTestId('incident-d0')
    expect(within(row).queryByRole('button', { name: /^Clôturer l'incident/ })).toBeNull()
    expect(within(row).getByRole('button', { name: /^Annuler l'incident/ })).toBeInTheDocument()
  })

  it('asks for category and measure on a disciplinary alert decision', async () => {
    const { api, client } = signedIn({
      'GET /alerts?status=open': json(200, [ALERT]),
      'POST /alerts/a1/decision': json(200, {}),
    })
    renderApp(client, '/alerts')
    const user = userEvent.setup()

    await user.click(within(await screen.findByTestId('alert-a1')).getByRole('button', { name: 'Autre décision' }))
    await user.click(await screen.findByRole('menuitem', { name: 'Ouvrir un dossier disciplinaire' }))
    const dialog = await screen.findByRole('dialog')
    const submit = within(dialog).getByRole('button', { name: 'Ouvrir un dossier disciplinaire' })
    await user.type(within(dialog).getByRole('textbox'), 'Absences répétées')
    expect(submit).toBeDisabled() // no category yet

    await user.selectOptions(within(dialog).getByLabelText(/^Catégorie/), 'ABSENTEEISM')
    await user.selectOptions(within(dialog).getByLabelText(/^Mesure/), 'WARNING')
    await user.click(submit)

    await waitFor(() =>
      expect(api.calls.find((c) => c.method === 'POST')?.body).toEqual({
        decision: 'DISCIPLINARY',
        note: 'Absences répétées',
        category: 'ABSENTEEISM',
        measure: 'WARNING',
      }),
    )
  })

  it('reads in English (NFR-12)', async () => {
    await i18n.changeLanguage('en')
    const { client } = signedIn({
      'GET /students/s1/history': json(200, HISTORY),
      'GET /students/s1/disciplinary-records': json(200, [FIGHT]),
    })
    renderApp(client, '/students/s1/discipline')

    const row = await screen.findByTestId('incident-d1')
    expect(within(row).getByText('Fight')).toBeInTheDocument()
    expect(within(row).getByText('Detention')).toBeInTheDocument()
    expect(within(row).getByText('Open')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Record an incident' })).toBeInTheDocument()
    expect(await tab('History')).toBeInTheDocument()
  })
})
