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
  status: 'NO_ACCOUNT',
  children: [child('s1', 'Awa'), child('s2', 'Junior', 'c2')],
}
const SIGNED_UP: Guardian = { ...NEW, id: 'g2', name: 'Papa Inscrit', phone: '+237699000002', status: 'ACCOUNT', children: [child('s3', 'Bruno')] }
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
    const { client } = signedIn({ 'GET /guardians?classId=c1': json(200, [NEW, SIGNED_UP]) })
    renderApp(client, '/guardians')
    await openClass()

    const table = await screen.findByRole('table')
    const row = (name: string) => within(table).getByText(name).closest('tr') as HTMLElement
    expect(within(row('Mama Exemple')).getByText('+237699000001, SMS : Français')).toBeInTheDocument()
    expect(within(row('Mama Exemple')).getByText('Junior Exemple (mère)')).toBeInTheDocument()
    expect(within(row('Mama Exemple')).getByText('en 5e B')).toBeInTheDocument() // a sibling in another class
    expect(within(row('Mama Exemple')).getByText('Pas encore connecté')).toBeInTheDocument()
    expect(within(row('Papa Inscrit')).getByText('Connecté')).toBeInTheDocument() // D7: gets push

    const summary = screen.getByRole('list', { name: 'Tuteurs par statut' })
    expect(within(summary).getByText('1 pas encore connecté')).toBeInTheDocument()
    expect(within(summary).getByText('1 connecté')).toBeInTheDocument()
  })

  it('sends nothing from this screen: a parent signs in with their number (UC-P1)', async () => {
    const { api, client } = signedIn({ 'GET /guardians?classId=c1': json(200, [NEW]) })
    renderApp(client, '/guardians')
    await openClass()

    await screen.findByRole('table')
    expect(screen.queryByRole('button', { name: /inviter|code/i })).not.toBeInTheDocument()
    expect(api.calls.some((c) => c.method === 'POST')).toBe(false)
  })
})
