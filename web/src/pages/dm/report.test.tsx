import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { Letterhead } from '../../api/letterhead'
import type { FullDayReport } from '../../api/report'
import i18n from '../../i18n'
import { fakeFetch, json, newClient, renderApp, tokens } from '../../test/fakeApi'

// UC-D9, D41: the full-day report on the web, under the school letterhead, to print or save as PDF
// through the browser. Generated data only (CLAUDE.md).
const LETTERHEAD: Letterhead = {
  schoolName: 'Lycée de Démo',
  configured: false,
  layout: 'BILINGUAL',
  french: [],
  english: [],
  hasLogo: false,
  logoVersion: null,
}
const REPORT: FullDayReport = {
  date: '2026-11-09',
  generatedBy: 'Marie Surveillante',
  studentCount: 3,
  classCount: 2,
  blocks: [
    {
      blockName: 'Bloc A',
      classes: [
        {
          className: '6e A',
          students: [
            { lastName: 'Fictif', firstName: 'Awa', matricule: 'TW-001' },
            { lastName: 'Exemple', firstName: 'Bruno', matricule: 'TW-002' },
          ],
        },
        { className: '5e B', students: [{ lastName: 'Modèle', firstName: 'Chantal', matricule: 'TW-003' }] },
      ],
    },
  ],
  unrecorded: [{ className: '4e C', periods: [2, 3] }],
}

function signedIn(report: () => Response = json(200, REPORT), roles: ('DM' | 'ADMIN')[] = ['DM'], language: 'FR' | 'EN' = 'FR') {
  const api = fakeFetch({
    'GET /reports/full-day-absences/default-date': json(200, { date: '2026-11-09' }),
    'GET /reports/full-day-absences?date=2026-11-09': report,
    'GET /reports/full-day-absences?date=2026-11-06': json(200, { ...REPORT, date: '2026-11-06' }),
    'GET /school/letterhead': json(200, LETTERHEAD),
  })
  const client = newClient(api.fetch)
  client.startSession(tokens({ roles, language }))
  return { api, client }
}

const printButton = () => screen.queryByRole('button', { name: 'Imprimer ou enregistrer en PDF' })

describe('the day report (UC-D9, D41)', () => {
  it('opens on the default day', async () => {
    const { api, client } = signedIn()
    const router = renderApp(client, '/report')

    expect(await screen.findByRole('heading', { name: 'Élèves absents toute la journée' })).toBeInTheDocument()
    expect(screen.getByLabelText('Jour')).toHaveValue('2026-11-09')
    await waitFor(() => expect(router.state.location.search).toBe('?date=2026-11-09'))
    expect(api.calls.map((c) => c.path)).toContain('/reports/full-day-absences/default-date')
  })

  it('the sheet', async () => {
    const { client } = signedIn()
    renderApp(client, '/report?date=2026-11-09')

    expect(await screen.findByText('Lundi 9 novembre 2026')).toBeInTheDocument()
    expect(screen.getByText('Bloc A – Surveillant Général : Marie Surveillante – 3 élèves dans 2 classes')).toBeInTheDocument()
    expect(screen.getByText('Lycée de Démo')).toBeInTheDocument()
    const sixA = screen.getByRole('table', { name: '6e A : 2 élèves' })
    const rows = within(sixA).getAllByRole('row')
    expect(rows).toHaveLength(3)
    expect(within(rows[1]).getByText('1')).toBeInTheDocument()
    expect(within(rows[1]).getByText('FICTIF Awa')).toBeInTheDocument()
    expect(within(rows[1]).getByText('TW-001')).toBeInTheDocument()
    expect(screen.getByRole('table', { name: '5e B : 1 élève' })).toBeInTheDocument()
    expect(screen.getByText(/Heures non saisies.*4e C \(H2, H3\)/)).toBeInTheDocument()
  })

  it("prints the school's header, not the settings page's preview of it", async () => {
    const { client } = signedIn()
    renderApp(client, '/report?date=2026-11-09')

    expect(await screen.findByText('Lycée de Démo')).toBeInTheDocument()
    // the preview draws a sketched page (a desk, a frame, grey bars) around the header
    expect(screen.queryByRole('figure', { name: "Aperçu de l'en-tête" })).not.toBeInTheDocument()
  })

  it('prints', async () => {
    const print = vi.spyOn(window, 'print').mockImplementation(() => undefined)
    const { client } = signedIn()
    renderApp(client, '/report?date=2026-11-09')

    await userEvent.setup().click(await screen.findByRole('button', { name: 'Imprimer ou enregistrer en PDF' }))
    expect(print).toHaveBeenCalledTimes(1)
    print.mockRestore()
  })

  it('no print button before the sheet can show', async () => {
    const { api } = signedIn()
    // the letterhead never answers: the sheet waits for it, so printing would give a blank page
    const fetchFn = ((input: RequestInfo | URL, init?: RequestInit) =>
      String(input).endsWith('/school/letterhead') ? new Promise<Response>(() => {}) : api.fetch(input, init)) as typeof fetch
    const client = newClient(fetchFn)
    client.startSession(tokens({ roles: ['DM'] }))
    renderApp(client, '/report?date=2026-11-09')

    await waitFor(() => expect(api.calls.map((c) => c.path)).toContain('/reports/full-day-absences?date=2026-11-09'))
    await new Promise((resolve) => setTimeout(resolve, 50))
    expect(printButton()).not.toBeInTheDocument()
  })

  it("the footer gives when the day shown was loaded, not when the page was opened", async () => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-11-10T08:00:00'))
    const { api, client } = signedIn()
    renderApp(client, '/report?date=2026-11-09')
    expect(await screen.findByText(/08:00/)).toBeInTheDocument()

    vi.setSystemTime(new Date('2026-11-10T09:30:00'))
    api.calls.length = 0
    fireEvent.change(screen.getByLabelText('Jour'), { target: { value: '2026-11-06' } })
    expect(await screen.findByText(/09:30/)).toBeInTheDocument()
    vi.useRealTimers()
  })

  it('nobody absent: no sheet, no print', async () => {
    const { client } = signedIn(json(200, { ...REPORT, studentCount: 0, classCount: 0, blocks: [], unrecorded: [] }))
    renderApp(client, '/report?date=2026-11-09')

    expect(await screen.findByText("Aucun élève n'a été absent à toutes les heures de cours le lundi 9 novembre 2026.")).toBeInTheDocument()
    expect(printButton()).not.toBeInTheDocument()
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
  })

  it('nobody absent still names the lessons not recorded', async () => {
    const { client } = signedIn(json(200, { ...REPORT, studentCount: 0, classCount: 0, blocks: [] }))
    renderApp(client, '/report?date=2026-11-09')

    expect(await screen.findByText(/Aucun élève n'a été absent/)).toBeInTheDocument()
    expect(screen.getByText(/Heures non saisies.*4e C \(H2, H3\)/)).toBeInTheDocument()
  })

  it("a refused day shows the server's message", async () => {
    const message = "La journée n'est pas terminée : le rapport du jour est disponible à partir de 17 h."
    const { client } = signedIn(json(400, { code: 'report.day_not_finished', message, field: 'date' }))
    renderApp(client, '/report?date=2026-11-09')

    expect(await screen.findByText(message)).toBeInTheDocument()
    expect(printButton()).not.toBeInTheDocument()
  })

  it('is in the DM menu only', async () => {
    const { client } = signedIn()
    renderApp(client, '/report?date=2026-11-09')
    expect(await screen.findByRole('link', { name: 'Rapport de la journée' })).toHaveAttribute('href', '/report')

    const admin = signedIn(undefined, ['ADMIN'])
    const router = renderApp(admin.client, '/report')
    await waitFor(() => expect(router.state.location.pathname).toBe('/'))
  })

  it('reads in English (NFR-12)', async () => {
    await i18n.changeLanguage('en')
    const { client } = signedIn(undefined, ['DM'], 'EN')
    renderApp(client, '/report?date=2026-11-09')

    expect(await screen.findByRole('heading', { name: 'Students absent the whole day' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Print or save as PDF' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Day report' })).toBeInTheDocument()
  })
})
