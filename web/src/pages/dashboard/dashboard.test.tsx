import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { SchoolDashboard } from '../../api/dashboard'
import { isoDate } from '../../api/dm'
import { fakeFetch, json, newClient, renderApp, tokens } from '../../test/fakeApi'

// Generated figures only (CLAUDE.md): two school days, two classes, made-up class names.
const today = new Date()
const start = new Date(today)
start.setDate(start.getDate() - 29)
const FROM = isoDate(start)
const TO = isoDate(today)
const PATH = `GET /dashboard/school?from=${FROM}&to=${TO}`

const counts = (studentPeriods: number, absentPeriods: number, unjustified: number, lateness: number) => ({
  sessions: studentPeriods / 25,
  studentPeriods,
  absentPeriods,
  unjustifiedAbsentPeriods: unjustified,
  lateness,
  unjustifiedLateness: lateness,
})

const FIXTURE: SchoolDashboard = {
  from: FROM,
  to: TO,
  totals: counts(400, 20, 8, 7),
  days: [
    { date: '2026-10-29', counts: counts(200, 16, 6, 4) },
    { date: '2026-10-30', counts: counts(200, 4, 2, 3) },
  ],
  classes: [
    { classId: 'c1', name: '6e A', counts: counts(200, 15, 6, 5) },
    { classId: 'c2', name: '6e B', counts: counts(200, 5, 2, 2) },
  ],
  alerts: [
    { rule: 'FULL_DAY_ABSENCE', open: 2, raised: 3, closed: 1 },
    { rule: 'CONSECUTIVE_LATENESS', open: 1, raised: 1, closed: 0 },
    { rule: 'UNJUSTIFIED_ABSENCES', open: 0, raised: 0, closed: 0 },
  ],
  delivery: {
    push: { pending: 1, sent: 2, delivered: 3, opened: 14, failed: 1, deliveryRate: 0.95 },
    sms: { pending: 0, sent: 5, delivered: 30, opened: 0, failed: 5, deliveryRate: 0.875 },
  },
  sms: { sentThisMonth: 120, monthlyCap: 500, capReached: false },
}

function signedIn(script: Parameters<typeof fakeFetch>[0], roles: ('ADMIN' | 'DM')[] = ['ADMIN']) {
  const api = fakeFetch(script)
  const client = newClient(api.fetch)
  client.startSession(tokens({ roles }))
  return { api, client }
}

const figure = (label: string) => screen.getByText(label, { selector: 'dt' }).nextElementSibling

describe('school dashboard (UC-A6, BR-24, BR-25)', () => {
  it('shows the figures of the last 30 days, the trend data, delivery per channel and SMS against the cap', async () => {
    const { api, client } = signedIn({ [PATH]: json(200, FIXTURE) })
    renderApp(client, '/dashboard')

    expect(await screen.findByRole('heading', { name: "Tableau de bord de l'école" })).toBeInTheDocument()
    await screen.findByText("Taux d'absence", { selector: 'dt' })
    expect(api.calls.some((c) => c.path === PATH.slice(4))).toBe(true)
    expect(figure("Taux d'absence")).toHaveTextContent('5 %')
    expect(figure('Absences non justifiées')).toHaveTextContent('8')
    expect(figure('Retards')).toHaveTextContent('7')
    expect(figure('Alertes ouvertes')).toHaveTextContent('3')

    // the numbers behind both charts, one row a day (BR-8, BR-9: absences and lateness apart)
    const days = await screen.findByRole('table', { name: 'Chiffres jour par jour' }, { timeout: 10_000 }) // the chart code loads on demand
    const rows = within(days).getAllByRole('row')
    expect(rows).toHaveLength(3)
    expect(rows[1]).toHaveTextContent(/29 oct\. 2026\s*8 %\s*16\s*4/)
    expect(rows[2]).toHaveTextContent(/30 oct\. 2026\s*2 %\s*4\s*3/)

    const push = screen.getByRole('region', { name: "Notification de l'application" })
    expect(push).toHaveTextContent('95 %')
    expect(within(push).getByText('Ouvertes').nextElementSibling).toHaveTextContent('14')
    expect(within(push).getByText('En échec').nextElementSibling).toHaveTextContent('1')
    const sms = screen.getByRole('region', { name: 'SMS' })
    expect(sms).toHaveTextContent('87,5 %')
    expect(within(sms).queryByText('Ouvertes')).not.toBeInTheDocument() // an SMS is never "opened"
    expect(within(sms).getByText('Remis').nextElementSibling).toHaveTextContent('30')

    const usage = screen.getByRole('region', { name: 'SMS ce mois-ci' })
    expect(within(usage).getByRole('progressbar', { name: '120 SMS utilisés sur 500 ce mois-ci' })).toHaveAttribute('aria-valuenow', '24')
    expect(usage).toHaveTextContent('Encore 380 SMS avant le plafond.')

    const byClass = screen.getByRole('table', { name: 'Par classe' })
    expect(within(byClass).getAllByRole('row')[1]).toHaveTextContent(/6e A\s*7,5 %\s*15\s*6\s*5/)
    const byRule = screen.getByRole('table', { name: 'Alertes par règle' })
    expect(within(byRule).getAllByRole('row')[1]).toHaveTextContent(/Absent toute la journée\s*3\s*1\s*2/)
  })

  it('says so when the monthly SMS cap is reached: only summons still go by SMS (BR-25, D12)', async () => {
    const { client } = signedIn({ [PATH]: json(200, { ...FIXTURE, sms: { sentThisMonth: 512, monthlyCap: 500, capReached: true } }) })
    renderApp(client, '/dashboard')

    const usage = await screen.findByRole('region', { name: 'SMS ce mois-ci' })
    expect(within(usage).getByRole('progressbar')).toHaveAttribute('aria-valuenow', '100')
    expect(usage).toHaveTextContent(/Plafond atteint : jusqu'à la fin du mois, seules les convocations partent par SMS/)
  })

  it('shows the server refusal when the range is too long', async () => {
    const { client } = signedIn({ [PATH]: json(400, { code: 'history.range_invalid', message: 'La période ne peut pas dépasser 366 jours.' }) })
    renderApp(client, '/dashboard')
    expect(await screen.findByText('La période ne peut pas dépasser 366 jours.')).toBeInTheDocument()
  })

  it('is in the admin navigation, and kept from the DM (NFR-8)', async () => {
    const { client } = signedIn({ [`GET /dashboard/scope?from=${FROM}&to=${TO}`]: json(200, { ...FIXTURE, delivery: undefined, sms: undefined }) }, ['DM'])
    renderApp(client, '/dashboard')

    expect(await screen.findByRole('heading', { name: 'Vos classes' })).toBeInTheDocument()
    const nav = screen.getByRole('navigation', { name: 'Navigation principale' })
    expect(within(nav).queryByRole('link', { name: 'Tableau de bord' })).toHaveAttribute('href', '/') // the DM's own
    expect(within(nav).queryByRole('link', { name: /école/ })).not.toBeInTheDocument()
  })
})
