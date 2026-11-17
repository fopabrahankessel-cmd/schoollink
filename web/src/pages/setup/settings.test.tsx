import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { fakeFetch, json, newClient, renderApp, tokens } from '../../test/fakeApi'

// Generated data only. The year spans every test run date: 2000-2100.
const YEAR = { id: 'y1', label: '2026-2027', startDate: '2000-01-01', endDate: '2100-12-31' }
const TERM = { id: 't1', academicYearId: 'y1', label: 'Trimestre 1', startDate: '2000-01-01', endDate: '2100-12-31' }
const SEQ1 = { id: 'q1', termId: 't1', number: 1, label: 'Séquence 1', startDate: '2000-01-01', endDate: '2100-06-30' }
const SEQ2 = { id: 'q2', termId: 't1', number: 2, label: 'Séquence 2', startDate: '2100-07-01', endDate: '2100-12-31' }
const P1 = { id: 'p1', number: 1, startTime: '07:30:00', endTime: '08:25:00' }
const CLASS_A = { id: 'c1', academicYearId: 'y1', blockId: 'b1', name: '6e A', level: '6e' }

function signedIn(script: Parameters<typeof fakeFetch>[0], roles: ('ADMIN' | 'DM')[] = ['ADMIN']) {
  const api = fakeFetch(script)
  const client = newClient(api.fetch)
  client.startSession(tokens({ roles }))
  return { api, client }
}

const school = (sequences: unknown[]) => ({
  'GET /auth/schools': json(200, []),
  'GET /academic-years': json(200, [YEAR]),
  'GET /terms?academicYearId=y1': json(200, [TERM]),
  'GET /sequences': json(200, sequences),
  'GET /periods': json(200, [P1]),
  'GET /blocks': json(200, [{ id: 'b1', name: 'Bloc A' }]),
  'GET /classes?academicYearId=y1': json(200, [CLASS_A]),
  'GET /timetable-slots': json(200, []),
  'GET /staff': json(200, []),
})

describe('school settings (D44)', () => {
  it('shows in one place what defines the school, what is done and what is missing', async () => {
    const { client } = signedIn(school([SEQ1]))
    renderApp(client, '/setup')

    expect(await screen.findByRole('heading', { name: "Réglages de l'école" })).toBeInTheDocument()
    const defined = screen.getByRole('region', { name: "Ce qui définit l'école" })
    const calendar = (await within(defined).findByRole('link', { name: 'Calendrier' })).closest('li')!
    expect(within(defined).getByRole('link', { name: 'Calendrier' })).toHaveAttribute('href', '/structure/calendar')
    expect(await within(calendar).findByText('Année 2026-2027, 1 trimestre, 1 séquence')).toBeInTheDocument()
    expect(within(calendar).getByText("1 trimestre n'a pas ses deux séquences.")).toBeInTheDocument()
    expect(within(calendar).getByText('À compléter')).toBeInTheDocument()

    expect(within(defined).getByRole('link', { name: 'Périodes' })).toHaveAttribute('href', '/structure/periods')
    expect(within(defined).getByRole('link', { name: 'Classes' })).toHaveAttribute('href', '/structure/classes')
    expect(within(defined).getByRole('link', { name: 'Emploi du temps' })).toHaveAttribute('href', '/structure/timetable')
    expect(within(defined).getByRole('link', { name: 'En-tête des documents' })).toHaveAttribute('href', '/structure/letterhead')

    // staff and students are kept up to date all year: linked from here, and still in the menu
    const people = screen.getByRole('region', { name: "Les personnes, tenues à jour toute l'année" })
    expect(within(people).getByRole('link', { name: 'Personnel' })).toHaveAttribute('href', '/staff')
    expect(within(people).getByRole('link', { name: 'Élèves' })).toHaveAttribute('href', '/school/students')
  })

  it('counts the calendar as ready once each term has its two sequences', async () => {
    const { client } = signedIn(school([SEQ1, SEQ2]))
    renderApp(client, '/setup')

    const defined = await screen.findByRole('region', { name: "Ce qui définit l'école" })
    const calendar = (await within(defined).findByRole('link', { name: 'Calendrier' })).closest('li')!
    expect(await within(calendar).findByText('Année 2026-2027, 1 trimestre, 2 séquences')).toBeInTheDocument()
    expect(within(calendar).getByText('Prêt')).toBeInTheDocument()
  })

  it('is one menu entry, current on every screen it leads to, each with a way back', async () => {
    const { client } = signedIn({ 'GET /periods': json(200, [P1]) })
    renderApp(client, '/structure/periods')

    const nav = await screen.findByRole('navigation', { name: 'Navigation principale' })
    const entry = within(nav).getByRole('link', { name: "Réglages de l'école" })
    expect(entry).toHaveAttribute('href', '/setup')
    expect(entry).toHaveAttribute('aria-current', 'page')
    expect(within(nav).queryByRole('link', { name: 'Périodes' })).not.toBeInTheDocument()
    expect(within(nav).getByRole('link', { name: 'Personnel' })).toBeInTheDocument()

    const user = userEvent.setup()
    await user.click(screen.getByRole('link', { name: "Réglages de l'école", current: false }))
    expect(await screen.findByRole('heading', { name: "Réglages de l'école" })).toBeInTheDocument()
  })

  it('keeps the page to the admin', async () => {
    const { client } = signedIn({}, ['DM'])
    renderApp(client, '/setup')

    expect(await screen.findByRole('heading', { name: 'Vos classes' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: "Réglages de l'école" })).not.toBeInTheDocument()
  })
})
