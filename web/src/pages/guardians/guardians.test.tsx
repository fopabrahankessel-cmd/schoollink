import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { Guardian } from '../../api/guardians'
import { fakeFetch, json, newClient, renderApp, tokens } from '../../test/fakeApi'

// Generated data only: made-up names and numbers (CLAUDE.md).
const YEAR = { id: 'y1', label: '2026-2027', startDate: '2026-09-07', endDate: '2027-06-30' }
const CLASS_A = { id: 'c1', academicYearId: 'y1', blockId: 'b1', name: '6e A', level: '6e' }
const CLASS_B = { id: 'c2', academicYearId: 'y1', blockId: 'b1', name: '5e B', level: '5e' }
const child = (id: string, firstName: string, classId = 'c1') => ({
  id,
  firstName,
  lastName: 'Exemple',
  classId,
  relation: 'MOTHER' as const,
  primary: true,
})
const NEW: Guardian = {
  id: 'g1',
  name: 'Mama Exemple',
  phone: '+237699000001',
  language: 'FR',
  status: 'NOT_INVITED',
  invitedAt: null,
  expiresAt: null,
  children: [child('s1', 'Awa'), child('s2', 'Junior', 'c2')],
}
const SIGNED_UP: Guardian = { ...NEW, id: 'g2', name: 'Papa Inscrit', phone: '+237699000002', status: 'ACCOUNT', children: [child('s3', 'Bruno')] }
const INVITED: Guardian = {
  ...NEW,
  id: 'g3',
  name: 'Tantine Invitée',
  phone: '+237699000003',
  status: 'INVITED',
  invitedAt: '2026-10-20T08:00:00Z',
  expiresAt: '2026-11-19T08:00:00Z',
  children: [child('s4', 'Chloé')],
}

function signedIn(script: Parameters<typeof fakeFetch>[0]) {
  const api = fakeFetch({
    'GET /academic-years': json(200, [YEAR]),
    'GET /classes?academicYearId=y1': json(200, [CLASS_A, CLASS_B]),
    ...script,
  })
  const client = newClient(api.fetch)
  client.startSession(tokens())
  return { api, client }
}

async function openClass() {
  const user = userEvent.setup()
  const select = await screen.findByLabelText('Classe')
  await waitFor(() => expect(within(select).getByRole('option', { name: '6e A' })).toBeInTheDocument())
  await user.selectOptions(select, 'c1')
  return user
}

describe('guardians screen (UC-A4)', () => {
  it("lists a class's guardians with their status, siblings and SMS language", async () => {
    const { client } = signedIn({ 'GET /guardians?classId=c1': json(200, [NEW, SIGNED_UP, INVITED]) })
    renderApp(client, '/guardians')
    await openClass()

    const table = await screen.findByRole('table')
    const row = (name: string) => within(table).getByText(name).closest('tr') as HTMLElement
    expect(within(row('Mama Exemple')).getByText('+237699000001, SMS : Français')).toBeInTheDocument()
    expect(within(row('Mama Exemple')).getByText('Junior Exemple (mère)')).toBeInTheDocument()
    expect(within(row('Mama Exemple')).getByText('en 5e B')).toBeInTheDocument() // a sibling in another class
    expect(within(row('Mama Exemple')).getByRole('button', { name: 'Inviter' })).toBeInTheDocument()
    // D7: a guardian with an account gets push, so no code is offered
    expect(within(row('Papa Inscrit')).getByText('Compte actif')).toBeInTheDocument()
    expect(within(row('Papa Inscrit')).queryByRole('button')).not.toBeInTheDocument()
    expect(within(row('Tantine Invitée')).getByText(/^Valable jusqu'au/)).toBeInTheDocument()

    const summary = screen.getByRole('list', { name: 'Tuteurs par statut' })
    expect(within(summary).getByText('1 pas encore invité')).toBeInTheDocument()
    expect(within(summary).getByText('1 avec compte')).toBeInTheDocument()
  })

  it('invites one guardian and never shows the code (D13)', async () => {
    const { api, client } = signedIn({
      'GET /guardians?classId=c1': [json(200, [NEW]), json(200, [{ ...NEW, status: 'INVITED', expiresAt: '2026-11-28T10:00:00Z' }])],
      'POST /guardians/g1/invitations': json(201, {
        invitationId: 'i1',
        guardianId: 'g1',
        guardianName: 'Mama Exemple',
        code: 'K7QM-3XPA',
        expiresAt: '2026-11-28T10:00:00Z',
      }),
    })
    renderApp(client, '/guardians')
    const user = await openClass()

    await user.click(await screen.findByRole('button', { name: 'Inviter' }))

    expect(await screen.findByText('Code envoyé par SMS à Mama Exemple.')).toBeInTheDocument()
    expect(api.calls.filter((c) => c.method === 'POST').map((c) => c.path)).toEqual(['/guardians/g1/invitations'])
    expect(await screen.findByText('Invité')).toBeInTheDocument()
    expect(screen.queryByText(/K7QM/)).not.toBeInTheDocument()
  })

  it('asks before replacing a live code', async () => {
    const { api, client } = signedIn({
      'GET /guardians?classId=c1': json(200, [INVITED]),
      'POST /guardians/g3/invitations': json(201, { invitationId: 'i2', guardianId: 'g3', guardianName: 'Tantine Invitée', code: 'AAAA-BBBB', expiresAt: '2026-11-28T10:00:00Z' }),
    })
    renderApp(client, '/guardians')
    const user = await openClass()

    await user.click(await screen.findByRole('button', { name: 'Envoyer un nouveau code' }))
    const dialog = await screen.findByRole('dialog', { name: 'Envoyer un nouveau code ?' })
    await user.click(within(dialog).getByRole('button', { name: 'Annuler' }))
    expect(api.calls.some((c) => c.method === 'POST')).toBe(false)

    // findBy: the page stays hidden from assistive technology until the dialog has closed
    await user.click(await screen.findByRole('button', { name: 'Envoyer un nouveau code' }))
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Envoyer un nouveau code' }))
    expect(await screen.findByText('Code envoyé par SMS à Tantine Invitée.')).toBeInTheDocument()
  })

  it('invites the whole class after confirmation, skipping guardians with an account', async () => {
    const { api, client } = signedIn({
      'GET /guardians?classId=c1': json(200, [NEW, SIGNED_UP, INVITED]),
      'POST /classes/c1/invitations': json(201, {
        issued: [
          { invitationId: 'i1', guardianId: 'g1', guardianName: 'Mama Exemple', code: 'AAAA-AAAA', expiresAt: '2026-11-28T10:00:00Z' },
          { invitationId: 'i3', guardianId: 'g3', guardianName: 'Tantine Invitée', code: 'BBBB-BBBB', expiresAt: '2026-11-28T10:00:00Z' },
        ],
        skippedWithAccount: 1,
      }),
    })
    renderApp(client, '/guardians')
    const user = await openClass()
    await screen.findByRole('table')

    await user.click(screen.getByRole('button', { name: 'Inviter la classe' }))
    const dialog = await screen.findByRole('dialog', { name: 'Inviter les tuteurs de 6e A ?' })
    expect(within(dialog).getByText(/chacun des 2 tuteurs qui n'ont pas encore de compte/)).toBeInTheDocument()
    await user.click(within(dialog).getByRole('button', { name: 'Envoyer les codes' }))

    expect(await screen.findByText('2 codes envoyés par SMS. Tuteurs déjà inscrits, sans code : 1.')).toBeInTheDocument()
    expect(api.calls.filter((c) => c.method === 'POST').map((c) => c.path)).toEqual(['/classes/c1/invitations'])
    expect(screen.queryByText(/AAAA/)).not.toBeInTheDocument()
  })

  it('cannot invite a class whose guardians all have an account', async () => {
    const { client } = signedIn({ 'GET /guardians?classId=c1': json(200, [SIGNED_UP]) })
    renderApp(client, '/guardians')
    await openClass()

    await screen.findByRole('table')
    expect(screen.getByRole('button', { name: 'Inviter la classe' })).toBeDisabled()
  })
})
