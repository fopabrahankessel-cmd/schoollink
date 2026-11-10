import { cleanup, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it } from 'vitest'
import type { MessagePreview, SentMessage } from '../../api/messages'
import i18n from '../../i18n'
import { fakeFetch, json, newClient, renderApp, tokens } from '../../test/fakeApi'

// UC-A5, D38: the administrator announces to every parent of the school or of one class, by push
// and, when ticked, by SMS, seeing the count and the cost first. Generated data only (CLAUDE.md).
const SENT: SentMessage = {
  id: 'an1',
  kind: 'ANNOUNCEMENT',
  audience: 'SCHOOL',
  studentId: null,
  studentName: null,
  classId: null,
  className: null,
  body: 'Pas de cours lundi : journée pédagogique.',
  sms: true,
  authorName: 'Paul Directeur',
  createdAt: '2026-11-09T07:00:00Z',
  delivery: { recipients: 120, pending: 0, sent: 10, delivered: 70, opened: 38, failed: 2 },
}
const CLASSES = [
  { classId: 'c1', className: '6e A', students: 30 },
  { classId: 'c2', className: '5e B', students: 28 },
]

function preview(sms: boolean): MessagePreview {
  return {
    recipients: 120,
    byApp: 80,
    bySms: sms ? 40 : 0,
    unreached: sms ? 0 : 40,
    smsUnits: sms ? 1 : 0,
    smsUnicode: false,
    smsLeft: 1957,
    estimatedCost: sms ? 7240 : 0,
    overCap: false,
    smsSample: sms ? 'SchoolLink - Lycée Démo : Pas de cours lundi.' : null,
  }
}

/** Signed in as [roles]; the preview follows the SMS switch of the posted draft. */
function signedIn(roles: string[], language: 'FR' | 'EN' = 'FR') {
  const api = fakeFetch({
    'GET /announcements': json(200, [SENT]),
    'GET /messages/classes': json(200, CLASSES),
    'POST /announcements': json(201, { id: 'x' }),
  })
  const fetchFn = async (input: RequestInfo | URL, init?: RequestInit) => {
    if (String(input).endsWith('/announcements/preview')) {
      const draft = JSON.parse(String(init?.body))
      api.calls.push({ method: 'POST', path: '/announcements/preview', headers: {}, body: draft })
      return new Response(JSON.stringify(preview(draft.sms)), { status: 200, headers: { 'Content-Type': 'application/json' } })
    }
    return api.fetch(input, init)
  }
  const client = newClient(fetchFn as typeof fetch)
  client.startSession(tokens({ roles: roles as never, language }))
  return { api, client }
}

const posted = (api: ReturnType<typeof fakeFetch>) =>
  api.calls.filter((c) => c.method === 'POST' && c.path === '/announcements').map((c) => c.body as Record<string, unknown>)

async function write(user: ReturnType<typeof userEvent.setup>, text: string) {
  await user.click(screen.getByLabelText('Votre message'))
  await user.paste(text)
}

afterEach(async () => {
  await i18n.changeLanguage('fr')
})

describe('Announcements (UC-A5, D38)', () => {
  it('an announcement to the whole school shows the parent count and SMS cost before sending', async () => {
    const { api, client } = signedIn(['ADMIN'])
    const router = renderApp(client, '/announcements/new')
    const user = userEvent.setup()

    expect(await screen.findByRole('radio', { name: "Tous les parents de l'école" })).toBeChecked()
    expect(screen.getByRole('switch', { name: "Aussi par SMS pour les parents sans l'application" })).not.toBeChecked()
    await write(user, 'Pas de cours lundi.')
    expect(await screen.findByText("120 parents : 80 par l'app, 40 sans l'app")).toBeInTheDocument()

    await user.click(screen.getByRole('switch', { name: "Aussi par SMS pour les parents sans l'application" }))
    expect(await screen.findByText("120 parents : 80 par l'app, 40 par SMS")).toBeInTheDocument()
    expect(screen.getByText(/≈ 7.240 FCFA/)).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Envoyer' }))
    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).getByText(/120 parents : 80 par l'app, 40 par SMS/)).toBeInTheDocument()
    expect(within(dialog).getByText(/≈ 7.240 FCFA/)).toBeInTheDocument()
    await user.click(within(dialog).getByRole('button', { name: 'Envoyer' }))

    await waitFor(() => expect(router.state.location.pathname).toBe('/announcements'))
    const { id, ...rest } = posted(api)[0]
    expect(id).toMatch(/^[0-9a-f-]{36}$/)
    expect(rest).toEqual({ body: 'Pas de cours lundi.', sms: true, plainSms: false })
    expect(await screen.findByText('Annonce envoyée.')).toBeInTheDocument()
  })

  it('an announcement to one class sends its classId', async () => {
    const { api, client } = signedIn(['ADMIN'])
    renderApp(client, '/announcements/new')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('radio', { name: "Les parents d'une classe" }))
    await user.selectOptions(await screen.findByLabelText('Classe'), 'c2')
    await write(user, 'Sortie au musée jeudi.')
    await waitFor(() => expect(screen.getByRole('button', { name: 'Envoyer' })).toBeEnabled())
    await user.click(screen.getByRole('button', { name: 'Envoyer' }))
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Envoyer' }))

    await waitFor(() => expect(posted(api)).toHaveLength(1))
    const { id: _id, ...rest } = posted(api)[0]
    expect(rest).toEqual({ classId: 'c2', body: 'Sortie au musée jeudi.', sms: false, plainSms: false })
  })

  it('lists sent announcements with their author and delivery', async () => {
    const { client } = signedIn(['ADMIN'])
    renderApp(client, '/announcements')

    const row = await screen.findByTestId('sent-an1')
    expect(within(row).getByText("Toute l'école")).toBeInTheDocument()
    expect(within(row).getByText('Paul Directeur')).toBeInTheDocument()
    expect(within(row).getByText('120 destinataires')).toBeInTheDocument()
    expect(within(row).getByText('38 ouverts')).toBeInTheDocument()
    expect(within(row).getByText('2 échecs')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Nouvelle annonce' })).toHaveAttribute('href', '/announcements/new')
  })

  it('is reachable from the admin menu only', async () => {
    const admin = signedIn(['ADMIN'])
    renderApp(admin.client, '/announcements')
    expect(await screen.findByRole('link', { name: 'Annonces' })).toHaveAttribute('href', '/announcements')
    cleanup()

    const dm = signedIn(['DM'])
    const router = renderApp(dm.client, '/announcements')
    await waitFor(() => expect(router.state.location.pathname).toBe('/'))
    expect(screen.queryByRole('link', { name: 'Annonces' })).not.toBeInTheDocument()
  })

  it('reads in English (NFR-12)', async () => {
    await i18n.changeLanguage('en')
    const { client } = signedIn(['ADMIN'], 'EN')
    renderApp(client, '/announcements')

    expect(await screen.findByText('120 recipients')).toBeInTheDocument()
    expect(screen.getByText('Whole school')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'New announcement' })).toBeInTheDocument()
  })
})
