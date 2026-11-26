import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it } from 'vitest'
import type { StudentHistory } from '../../api/dm'
import type { MessagePreview, SentMessage } from '../../api/messages'
import i18n from '../../i18n'
import { fakeFetch, json, newClient, renderApp, tokens } from '../../test/fakeApi'

// UC-D4, D38: the DM writes to the parents of one student or one class of their blocks. A preview
// asked 400 ms after the last change gives who gets it and what the SMS cost; sending asks for
// confirmation and sends once. Generated data only (CLAUDE.md).
const SENT: SentMessage = {
  id: 'm1',
  kind: 'DM_MESSAGE',
  audience: 'CLASS',
  studentId: null,
  studentName: null,
  classId: 'c1',
  className: '6e A',
  body: 'Réunion des parents vendredi à 15 h.',
  sms: false,
  authorName: 'Marie Surveillante',
  createdAt: '2026-11-09T08:30:00Z',
  delivery: { recipients: 4, pending: 0, sent: 1, delivered: 2, opened: 1, failed: 0 },
}
const CLASSES = [
  { classId: 'c1', className: '6e A', students: 30 },
  { classId: 'c2', className: '5e B', students: 28 },
]
const HISTORY: StudentHistory = {
  studentId: 's1',
  matricule: 'FAKE001',
  firstName: 'Awa',
  lastName: 'Exemple',
  classId: 'c1',
  className: '6e A',
  from: '2026-08-12',
  to: '2026-11-10',
  totals: { absentPeriods: 0, unjustifiedAbsentPeriods: 0, lateness: 0, unjustifiedLateness: 0 },
  days: [],
}
function preview(sms: boolean, extra: Partial<MessagePreview> = {}): MessagePreview {
  return {
    recipients: 4,
    byApp: 2,
    bySms: sms ? 2 : 0,
    unreached: sms ? 0 : 2,
    smsUnits: sms ? 1 : 0,
    smsUnicode: false,
    smsLeft: 1957,
    estimatedCost: sms ? 362 : 0,
    overCap: false,
    smsSample: sms ? 'SchoolLink - SG Lycée Démo : Réunion vendredi.' : null,
    ...extra,
  }
}
function signedIn(script: Parameters<typeof fakeFetch>[0], language: 'FR' | 'EN' = 'FR') {
  const api = fakeFetch(script)
  const client = newClient(api.fetch)
  client.startSession(tokens({ roles: ['DM'], language }))
  return { api, client }
}

/** A fake fetch whose preview answer depends on the posted draft. */
function withPreview(
  script: Parameters<typeof fakeFetch>[0],
  answer: (draft: { sms: boolean; plainSms: boolean }) => MessagePreview,
) {
  const api = fakeFetch(script)
  const fetchFn = async (input: RequestInfo | URL, init?: RequestInit) => {
    if (String(input).endsWith('/messages/preview')) {
      const draft = JSON.parse(String(init?.body))
      api.calls.push({ method: 'POST', path: '/messages/preview', headers: {}, body: draft })
      return new Response(JSON.stringify(answer(draft)), { status: 200, headers: { 'Content-Type': 'application/json' } })
    }
    return api.fetch(input, init)
  }
  const client = newClient(fetchFn as typeof fetch)
  client.startSession(tokens({ roles: ['DM'] }))
  return { api, client }
}

const BASE = {
  'GET /messages': json(200, [SENT]),
  'GET /messages/classes': json(200, CLASSES),
  'POST /messages': json(201, { id: 'x' }),
}

const posted = (api: ReturnType<typeof fakeFetch>, path: string) =>
  api.calls.filter((c) => c.method === 'POST' && c.path === path).map((c) => c.body as Record<string, unknown>)

const withoutId = (body: Record<string, unknown>) => {
  const { id: _id, ...rest } = body
  return rest
}

/** Pastes the text: typing it key by key makes the test slow, and flaky on a busy machine. */
async function write(user: ReturnType<typeof userEvent.setup>, text: string) {
  await user.click(screen.getByLabelText('Votre message'))
  await user.paste(text)
}

async function writeToClass(user: ReturnType<typeof userEvent.setup>, text: string) {
  await user.selectOptions(await screen.findByLabelText('Classe'), 'c1')
  await write(user, text)
}

const sendButton = () => screen.getByRole('button', { name: 'Envoyer' })

afterEach(async () => {
  await i18n.changeLanguage('fr')
})

describe('Messages to parents (UC-D4, D38)', () => {
  it('lists sent messages with delivery and an empty state', async () => {
    const { client } = signedIn(BASE)
    renderApp(client, '/messages')

    const row = await screen.findByTestId('sent-m1')
    expect(within(row).getByText('6e A')).toBeInTheDocument()
    expect(within(row).getByText('Réunion des parents vendredi à 15 h.')).toBeInTheDocument()
    expect(within(row).getByText('4 destinataires')).toBeInTheDocument()
    expect(within(row).getByText('1 ouvert')).toBeInTheDocument()
    expect(within(row).getByText('2 reçus')).toBeInTheDocument()
  })

  it('says what to do when nothing was sent', async () => {
    const { client } = signedIn({ ...BASE, 'GET /messages': json(200, []) })
    renderApp(client, '/messages')

    expect(await screen.findByText('Aucun message envoyé.')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Nouveau message' })).toHaveAttribute('href', '/messages/new')
  })

  it('sends a class message: SMS off by default, counts and cost, confirm, then back to the list', async () => {
    const { api, client } = withPreview(BASE, (draft) => preview(draft.sms))
    const router = renderApp(client, '/messages/new')
    const user = userEvent.setup()

    await writeToClass(user, '  Réunion des parents vendredi à 15 h. ')
    expect(screen.getByRole('switch', { name: "Aussi par SMS, à tous les parents" })).not.toBeChecked()
    expect(await screen.findByText("4 parents : 2 par l'app, 2 sans l'app")).toBeInTheDocument()

    await user.click(sendButton())
    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).getByText(/4 parents : 2 par l'app, 2 sans l'app/)).toBeInTheDocument()
    await user.click(within(dialog).getByRole('button', { name: 'Envoyer' }))

    await waitFor(() => expect(router.state.location.pathname).toBe('/messages'))
    const [body] = posted(api, '/messages')
    expect(body.id).toMatch(/^[0-9a-f-]{36}$/)
    expect(withoutId(body)).toEqual({ classId: 'c1', body: 'Réunion des parents vendredi à 15 h.', sms: false, plainSms: false })
    expect(await screen.findByText('Message envoyé.')).toBeInTheDocument()
  })

  it('opens from the student page with that student and SMS on', async () => {
    const { api, client } = withPreview(
      {
        ...BASE,
        'GET /students/s1/history': json(200, HISTORY),
        'GET /students/s1/disciplinary-records': json(200, []),
      },
      (draft) => preview(draft.sms),
    )
    const router = renderApp(client, '/students/s1')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('link', { name: 'Écrire aux parents' }))
    expect(router.state.location.pathname + router.state.location.search).toBe('/messages/new?studentId=s1')
    expect(await screen.findByText('Exemple Awa', { selector: '.MuiChip-label' })).toBeInTheDocument()
    expect(screen.getByRole('switch', { name: "Aussi par SMS, à tous les parents" })).toBeChecked()

    await write(user, 'Merci de passer au bureau.')
    expect(await screen.findByText("4 parents : 2 par l'app, 2 par SMS")).toBeInTheDocument()
    expect(screen.getByText(/≈ 362 FCFA/)).toBeInTheDocument()
    expect(screen.getByText('SchoolLink - SG Lycée Démo : Réunion vendredi.')).toBeInTheDocument()
    await user.click(sendButton())
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Envoyer' }))

    await waitFor(() => expect(posted(api, '/messages')).toHaveLength(1))
    expect(withoutId(posted(api, '/messages')[0])).toEqual({
      studentId: 's1',
      body: 'Merci de passer au bureau.',
      sms: true,
      plainSms: false,
    })
  })

  it('blocks sending over the SMS left and says why', async () => {
    const { client } = withPreview(BASE, (draft) => preview(draft.sms, { overCap: draft.sms, smsLeft: 1 }))
    renderApp(client, '/messages/new')
    const user = userEvent.setup()

    await writeToClass(user, 'Réunion vendredi.')
    await user.click(screen.getByRole('switch', { name: "Aussi par SMS, à tous les parents" }))

    expect(await screen.findByText(/Plus assez de SMS ce mois-ci/)).toBeInTheDocument()
    expect(sendButton()).toBeDisabled()
  })

  it('offers to replace special letters when the SMS is unicode', async () => {
    const { api, client } = withPreview(BASE, (draft) => preview(draft.sms, { smsUnicode: draft.sms && !draft.plainSms }))
    renderApp(client, '/messages/new')
    const user = userEvent.setup()

    await writeToClass(user, 'Fête de fin d’année')
    await user.click(screen.getByRole('switch', { name: "Aussi par SMS, à tous les parents" }))
    await user.click(await screen.findByRole('switch', { name: 'Remplacer les lettres spéciales' }))

    await waitFor(() => expect(posted(api, '/messages/preview').at(-1)?.plainSms).toBe(true))
  })

  it('keeps Send disabled for a blank text and sends once on a double click', async () => {
    const { api, client } = withPreview(BASE, (draft) => preview(draft.sms))
    renderApp(client, '/messages/new')
    const user = userEvent.setup()

    await writeToClass(user, '   ')
    expect(sendButton()).toBeDisabled()
    expect(posted(api, '/messages/preview')).toHaveLength(0)

    await write(user, 'Réunion vendredi.')
    await waitFor(() => expect(sendButton()).toBeEnabled())
    await user.click(sendButton())
    await user.dblClick(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Envoyer' }))

    await screen.findByText('Message envoyé.')
    expect(posted(api, '/messages')).toHaveLength(1)
  })

  it('reads in English (NFR-12)', async () => {
    await i18n.changeLanguage('en')
    const { client } = signedIn(BASE, 'EN')
    renderApp(client, '/messages')

    expect(await screen.findByText('4 recipients')).toBeInTheDocument()
    expect(screen.getByText('1 opened')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'New message' })).toBeInTheDocument()
  })
})
