import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { StaffMember } from '../../api/staff'
import { fakeFetch, json, newClient, renderApp, tokens } from '../../test/fakeApi'

// Generated data only.
const YEAR = { id: 'y1', label: '2026-2027', startDate: '2026-09-07', endDate: '2027-06-30' }
const BLOCK = { id: 'b1', name: 'Bloc A' }
const CLASS_A = { id: 'c1', academicYearId: 'y1', blockId: 'b1', name: '6e A', level: '6e' }
const CLASS_B = { id: 'c2', academicYearId: 'y1', blockId: 'b1', name: '5e B', level: '5e' }
const PERSON: StaffMember = {
  id: 'u1',
  firstName: 'Awa',
  lastName: 'Fictive',
  phone: '+237600000001',
  email: null,
  language: 'FR',
  status: 'ACTIVE',
  passwordChangeRequired: true,
  roles: [],
  teaching: [],
}
const TEACHER: StaffMember = { ...PERSON, roles: [{ id: 'r1', role: 'TEACHER', blockId: null }] }

function signedIn(script: Parameters<typeof fakeFetch>[0], roles: ('ADMIN' | 'DM')[] = ['ADMIN']) {
  const api = fakeFetch(script)
  const client = newClient(api.fetch)
  client.startSession(tokens({ roles }))
  return { api, client }
}

/** What the detail page loads besides the person. */
const detail = {
  'GET /blocks': json(200, [BLOCK]),
  'GET /classes': json(200, [CLASS_A, CLASS_B]),
  'GET /academic-years': json(200, [YEAR]),
  'GET /classes?academicYearId=y1': json(200, [CLASS_A, CLASS_B]),
}

describe('staff screens (UC-A2)', () => {
  it('creates an account, shows the temporary password once, then opens the new account', async () => {
    const { api, client } = signedIn({
      ...detail,
      'GET /staff': [json(200, []), json(200, [PERSON])],
      'POST /staff': json(201, { staff: PERSON, temporaryPassword: 'kT7-mQ2x-Pw9' }),
      'GET /staff/u1': json(200, PERSON),
    })
    const router = renderApp(client, '/staff')

    expect(await screen.findByText('Aucun membre du personnel.')).toBeInTheDocument()
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: 'Ajouter un membre du personnel' }))
    const form = await screen.findByRole('dialog', { name: 'Nouveau membre du personnel' })
    await user.type(within(form).getByLabelText(/Prénom/), 'Awa')
    await user.type(within(form).getByLabelText(/^Nom/), 'Fictive')
    await user.type(within(form).getByLabelText(/Téléphone/), '+237600000001')
    await user.click(within(form).getByRole('button', { name: 'Créer le compte' }))

    const shown = await screen.findByRole('dialog', { name: 'Mot de passe temporaire' })
    expect(within(shown).getByLabelText('Mot de passe temporaire')).toHaveTextContent('kT7-mQ2x-Pw9')
    expect(api.calls.find((c) => c.method === 'POST')?.body).toEqual({
      firstName: 'Awa',
      lastName: 'Fictive',
      phone: '+237600000001',
      email: null,
      language: null,
    })
    await user.click(within(shown).getByRole('button', { name: "C'est noté" }))

    expect(await screen.findByRole('heading', { name: 'Awa Fictive' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/staff/u1') // the id, never the phone (NFR-9)
    expect(screen.queryByText('kT7-mQ2x-Pw9')).not.toBeInTheDocument()
  })

  it("shows the server's message on the field it concerns", async () => {
    const { client } = signedIn({
      'GET /staff': json(200, []),
      'GET /blocks': json(200, []),
      'POST /staff': json(409, {
        code: 'staff.phone_taken',
        message: 'Ce téléphone est déjà utilisé.',
        details: [{ code: 'staff.phone_taken', message: 'Ce téléphone est déjà utilisé.', field: 'phone' }],
      }),
    })
    renderApp(client, '/staff')

    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Ajouter un membre du personnel' }))
    const form = await screen.findByRole('dialog')
    await user.type(within(form).getByLabelText(/Prénom/), 'Awa')
    await user.type(within(form).getByLabelText(/^Nom/), 'Fictive')
    await user.type(within(form).getByLabelText(/Téléphone/), '+237600000001')
    await user.click(within(form).getByRole('button', { name: 'Créer le compte' }))

    expect(await within(form).findByText('Ce téléphone est déjà utilisé.')).toBeInTheDocument()
    expect(within(form).getByLabelText(/Téléphone/)).toHaveAttribute('aria-invalid', 'true')
    expect(within(form).queryByRole('alert')).not.toBeInTheDocument()
  })

  it('gives the DM role with its block (D5)', async () => {
    const asDm: StaffMember = { ...PERSON, roles: [{ id: 'r2', role: 'DM', blockId: 'b1' }] }
    const { api, client } = signedIn({
      ...detail,
      'GET /staff/u1': [json(200, PERSON), json(200, asDm)],
      'POST /staff/u1/roles': json(201, asDm.roles[0]),
    })
    renderApp(client, '/staff/u1')

    expect(await screen.findByText("Aucun rôle pour l'instant.")).toBeInTheDocument()
    const user = userEvent.setup()
    const add = screen.getByRole('button', { name: 'Ajouter le rôle' })
    await user.selectOptions(screen.getByLabelText('Rôle à ajouter'), 'DM')
    expect(add).toBeDisabled() // the block is still missing
    await user.selectOptions(await screen.findByLabelText('Bloc'), 'b1')
    await user.click(add)

    expect(await screen.findByRole('cell', { name: 'Surveillant Général · Bloc A' })).toBeInTheDocument()
    expect(api.calls.find((c) => c.method === 'POST')?.body).toEqual({ role: 'DM', blockId: 'b1' })
  })

  it('assigns two classes to a teacher (BR-1)', async () => {
    const one = { ...TEACHER, teaching: [{ id: 'a1', classId: 'c1', subject: 'Mathématiques' }] }
    const two = { ...one, teaching: [...one.teaching, { id: 'a2', classId: 'c2', subject: 'Mathématiques' }] }
    const { api, client } = signedIn({
      ...detail,
      'GET /staff/u1': [json(200, TEACHER), json(200, one), json(200, two)],
      'POST /staff/u1/teaching': [json(201, one.teaching[0]), json(201, two.teaching[1])],
    })
    renderApp(client, '/staff/u1')

    expect(await screen.findByText('Aucune classe affectée.')).toBeInTheDocument()
    const user = userEvent.setup()
    const teaching = screen.getByRole('region', { name: 'Classes enseignées' })
    await waitFor(() => expect(within(teaching).getByLabelText('Année scolaire')).toHaveValue('y1'))
    await user.selectOptions(await within(teaching).findByRole('combobox', { name: 'Classe' }), 'c1')
    await user.type(within(teaching).getByLabelText('Matière'), 'Mathématiques')
    await user.click(within(teaching).getByRole('button', { name: 'Affecter la classe' }))
    expect(await within(teaching).findByRole('cell', { name: '6e A' })).toBeInTheDocument()

    // the subject stays for the next class
    await user.selectOptions(within(teaching).getByRole('combobox', { name: 'Classe' }), 'c2')
    await user.click(within(teaching).getByRole('button', { name: 'Affecter la classe' }))
    expect(await within(teaching).findByRole('cell', { name: '5e B' })).toBeInTheDocument()

    expect(api.calls.filter((c) => c.method === 'POST').map((c) => c.body)).toEqual([
      { classId: 'c1', subject: 'Mathématiques' },
      { classId: 'c2', subject: 'Mathématiques' },
    ])
  })

  it('asks before removing a role, and says why the server refused', async () => {
    const assigned = { ...TEACHER, teaching: [{ id: 'a1', classId: 'c1', subject: 'Mathématiques' }] }
    const { api, client } = signedIn({
      ...detail,
      'GET /staff/u1': json(200, assigned),
      'DELETE /staff/u1/roles/r1': json(409, {
        code: 'staff.teaching_assigned',
        message: "Retirez d'abord les classes de cet enseignant.",
      }),
    })
    renderApp(client, '/staff/u1')

    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Retirer le rôle Enseignant' }))
    const confirm = await screen.findByRole('dialog', { name: 'Retirer ce rôle ?' })
    expect(api.calls.some((c) => c.method === 'DELETE')).toBe(false)
    await user.click(within(confirm).getByRole('button', { name: 'Retirer' }))

    expect(await screen.findByText("Retirez d'abord les classes de cet enseignant.")).toBeInTheDocument()
    expect(await screen.findByRole('cell', { name: 'Enseignant' })).toBeInTheDocument() // still given
  })

  it('disables an account after confirmation, and resets a password', async () => {
    const disabled = { ...PERSON, status: 'DISABLED' as const }
    const { api, client } = signedIn({
      ...detail,
      'GET /staff/u1': [json(200, PERSON), json(200, disabled)],
      'PUT /staff/u1/status': json(200, disabled),
      'POST /staff/u1/password-reset': json(200, { temporaryPassword: 'nW4-zR8k-Lq3' }),
    })
    renderApp(client, '/staff/u1')

    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Désactiver le compte' }))
    await user.click(
      within(await screen.findByRole('dialog', { name: 'Désactiver ce compte ?' })).getByRole('button', {
        name: 'Désactiver le compte',
      }),
    )
    expect(await screen.findByRole('button', { name: 'Réactiver le compte' })).toBeInTheDocument()
    expect(api.calls.find((c) => c.method === 'PUT')?.body).toEqual({ status: 'DISABLED' })

    await user.click(screen.getByRole('button', { name: 'Réinitialiser le mot de passe' }))
    await user.click(
      within(await screen.findByRole('dialog', { name: 'Réinitialiser le mot de passe ?' })).getByRole('button', {
        name: 'Réinitialiser le mot de passe',
      }),
    )
    const shown = await screen.findByRole('dialog', { name: 'Mot de passe temporaire' })
    expect(within(shown).getByLabelText('Mot de passe temporaire')).toHaveTextContent('nW4-zR8k-Lq3')
  })

  it('keeps the staff screens to the admin', async () => {
    const { client } = signedIn({}, ['DM'])
    renderApp(client, '/staff/u1')

    expect(await screen.findByRole('heading', { name: 'Bienvenue sur SchoolLink' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Personnel' })).not.toBeInTheDocument()
  })

  it('shows the admin the staff screen in the navigation, current on a detail page', async () => {
    const { client } = signedIn({ ...detail, 'GET /staff/u1': json(200, PERSON) })
    renderApp(client, '/staff/u1')

    const nav = await screen.findByRole('navigation', { name: 'Navigation principale' })
    const link = within(nav).getByRole('link', { name: 'Personnel' })
    expect(link).toHaveAttribute('href', '/staff')
    expect(link).toHaveAttribute('aria-current', 'page')
  })
})
