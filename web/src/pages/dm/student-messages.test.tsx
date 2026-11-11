import { screen, waitFor } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { StudentHistory } from '../../api/dm'
import type { SentMessage } from '../../api/messages'
import i18n from '../../i18n'
import { fakeFetch, json, newClient, renderApp, tokens } from '../../test/fakeApi'

// D41, UC-D4: the Messages tab of the DM's student page lists the messages written to that
// student's parents. Generated data only (CLAUDE.md).
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
const MESSAGE: SentMessage = {
  id: 'm1',
  kind: 'DM_MESSAGE',
  audience: 'STUDENT',
  studentId: 's1',
  studentName: 'Exemple Awa',
  classId: 'c1',
  className: '6e A',
  body: 'Awa a oublié son cahier.',
  sms: true,
  authorName: 'Marie Surveillante',
  createdAt: '2026-11-09T08:30:00Z',
  delivery: { recipients: 2, pending: 0, sent: 0, delivered: 1, opened: 1, failed: 0 },
}

function signedIn(messages: SentMessage[], language: 'FR' | 'EN' = 'FR') {
  const api = fakeFetch({
    'GET /students/s1/history': json(200, HISTORY),
    'GET /messages?studentId=s1': json(200, messages),
  })
  const client = newClient(api.fetch)
  client.startSession(tokens({ roles: ['DM'], language }))
  return { api, client }
}

describe('the student page Messages tab (D41, UC-D4)', () => {
  it("reads this student's messages", async () => {
    const { api, client } = signedIn([MESSAGE])
    renderApp(client, '/students/s1/messages')

    expect(await screen.findByText('Awa a oublié son cahier.')).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: 'Messages' })).toHaveAttribute('aria-selected', 'true')
    expect(api.calls.some((c) => c.method === 'GET' && c.path === '/messages?studentId=s1')).toBe(true)
  })

  it('links from the other tabs', async () => {
    const { client } = signedIn([MESSAGE])
    renderApp(client, '/students/s1')

    expect(await screen.findByRole('tab', { name: 'Messages' })).toHaveAttribute('href', '/students/s1/messages')
  })

  it('says when none was sent yet', async () => {
    const { client } = signedIn([])
    renderApp(client, '/students/s1/messages')

    expect(await screen.findByText('Aucun message envoyé à ses parents.')).toBeInTheDocument()
    // the empty state's button, beside the page's own
    for (const link of screen.getAllByRole('link', { name: 'Écrire aux parents' })) {
      expect(link).toHaveAttribute('href', '/messages/new?studentId=s1')
    }
    expect(screen.getAllByRole('link', { name: 'Écrire aux parents' })).toHaveLength(2)
  })

  it('reads in English (NFR-12)', async () => {
    await i18n.changeLanguage('en')
    const { client } = signedIn([], 'EN')
    renderApp(client, '/students/s1/messages')

    expect(await screen.findByText('No message sent to the parents.')).toBeInTheDocument()
    await waitFor(() => expect(screen.getByRole('tab', { name: 'Messages' })).toBeInTheDocument())
  })
})
