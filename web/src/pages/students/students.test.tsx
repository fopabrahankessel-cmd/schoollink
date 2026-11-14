import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { Student, StudentGuardian, StudentRow } from '../../api/students'
import { fakeFetch, json, newClient, renderApp, tokens } from '../../test/fakeApi'

// Generated data only.
const YEAR = { id: 'y1', label: '2026-2027', startDate: '2026-09-07', endDate: '2027-06-30' }
const CLASS_A = { id: 'c1', academicYearId: 'y1', blockId: 'b1', name: '6e A', level: '6e' }
const CLASS_B = { id: 'c2', academicYearId: 'y1', blockId: 'b1', name: '5e B', level: '5e' }
const AWA: StudentRow = { id: 's1', matricule: 'TW-0001', firstName: 'Awa', lastName: 'Fictif', classId: 'c1', className: '6e A', leftOn: null, guardians: 1 }
const BEN: StudentRow = { id: 's2', matricule: 'TW-0002', firstName: 'Ben', lastName: 'Inventé', classId: 'c1', className: '6e A', leftOn: null, guardians: 2 }
const GONE: StudentRow = { id: 's3', matricule: 'TW-0003', firstName: 'Carl', lastName: 'Imaginaire', classId: 'c1', className: '6e A', leftOn: '2026-11-02', guardians: 1 }
const MOTHER: StudentGuardian = { id: 'g1', name: 'Parent Fictif', phone: '+237690000001', language: 'FR', relation: 'MOTHER', primary: true, hasAccount: true, children: 2 }
const FATHER: StudentGuardian = { id: 'g2', name: 'Père Fictif', phone: '+237690000002', language: 'FR', relation: 'FATHER', primary: false, hasAccount: false, children: 1 }
const UNCLE: StudentGuardian = { id: 'g3', name: 'Oncle Fictif', phone: '+237690000003', language: 'EN', relation: 'OTHER', primary: false, hasAccount: false, children: 1 }
const STUDENT: Student = { id: 's1', matricule: 'TW-0001', firstName: 'Awa', lastName: 'Fictif', sex: 'F', birthYear: 2014, classId: 'c1', className: '6e A', leftOn: null, guardians: [MOTHER, FATHER] }

function signedIn(script: Parameters<typeof fakeFetch>[0], roles: ('ADMIN' | 'DM')[] = ['ADMIN']) {
  const api = fakeFetch(script)
  const client = newClient(api.fetch)
  client.startSession(tokens({ roles }))
  return { api, client }
}

const structure = {
  'GET /academic-years': json(200, [YEAR]),
  'GET /classes?academicYearId=y1': json(200, [CLASS_A, CLASS_B]),
}

async function pickClass(user: ReturnType<typeof userEvent.setup>, name: string) {
  await screen.findByRole('option', { name })
  await user.selectOptions(screen.getByLabelText('Classe'), name)
}

describe('admin student screens (D43)', () => {
  it('lists the students of a class, and those who left only when asked', async () => {
    const { client } = signedIn({
      ...structure,
      'GET /students?classId=c1': json(200, [AWA, BEN]),
      'GET /students?classId=c1&left=true': json(200, [AWA, GONE, BEN]),
    })
    renderApp(client, '/school/students')

    expect(await screen.findByText(/Choisissez une classe/)).toBeInTheDocument()
    const user = userEvent.setup()
    await pickClass(user, '6e A')

    expect(await screen.findByRole('link', { name: 'FICTIF Awa' })).toHaveAttribute('href', '/school/students/s1')
    expect(screen.getByText('2 élèves')).toBeInTheDocument()
    expect(screen.queryByText('IMAGINAIRE Carl')).not.toBeInTheDocument()

    await user.click(screen.getByLabelText('Afficher les élèves partis'))
    expect(await screen.findByRole('link', { name: 'IMAGINAIRE Carl' })).toBeInTheDocument()
    expect(screen.getByText('Parti le 2 nov. 2026')).toBeInTheDocument()
  })

  it('finds a student of any class by name or matricule', async () => {
    const { client } = signedIn({
      ...structure,
      'GET /students?q=TW-0002': json(200, [BEN]),
    })
    renderApp(client, '/school/students')

    const user = userEvent.setup()
    await user.type(await screen.findByLabelText('Chercher par nom ou matricule'), 'TW-0002')
    expect(await screen.findByRole('link', { name: 'INVENTÉ Ben' })).toBeInTheDocument()
  })

  it('adds a student to the chosen class with a primary guardian, then opens the student', async () => {
    const { api, client } = signedIn({
      ...structure,
      'GET /students?classId=c1': json(200, []),
      'POST /students': json(201, { ...STUDENT, guardians: [MOTHER] }),
      'GET /students/s1': json(200, { ...STUDENT, guardians: [MOTHER] }),
    })
    const router = renderApp(client, '/school/students')

    const user = userEvent.setup()
    await pickClass(user, '6e A')
    expect(await screen.findByText('Aucun élève dans cette classe.')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Ajouter un élève' }))
    const form = await screen.findByRole('dialog', { name: 'Nouvel élève' })
    await user.type(within(form).getByLabelText(/^Matricule/), 'TW-0001')
    await user.type(within(form).getByLabelText(/^Nom\s\*$/), 'Fictif')
    await user.type(within(form).getByLabelText(/^Prénom/), 'Awa')
    await user.type(within(form).getByLabelText(/^Année de naissance/), '2014')
    await user.type(within(form).getByLabelText('Tuteur principal : Téléphone'), '690 000 001')
    await user.type(within(form).getByLabelText('Tuteur principal : Nom du tuteur'), 'Parent Fictif')
    await user.click(within(form).getByRole('button', { name: "Ajouter l'élève" }))

    expect(await screen.findByRole('heading', { name: 'FICTIF Awa' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/school/students/s1')
    expect(api.calls.find((c) => c.method === 'POST')?.body).toEqual({
      matricule: 'TW-0001',
      lastName: 'Fictif',
      firstName: 'Awa',
      sex: 'F',
      birthYear: 2014,
      classId: 'c1',
      guardians: [{ phone: '690 000 001', name: 'Parent Fictif', relation: 'MOTHER', language: null, primary: true }],
    })
  })

  it('shows a refused matricule on its field', async () => {
    const { client } = signedIn({
      ...structure,
      'GET /students?classId=c1': json(200, []),
      'POST /students': json(409, {
        code: 'students.matricule_exists',
        message: 'Ce matricule existe déjà.',
        details: [{ code: 'students.matricule_exists', message: 'Ce matricule existe déjà.', field: 'matricule' }],
      }),
    })
    renderApp(client, '/school/students')

    const user = userEvent.setup()
    await pickClass(user, '6e A')
    await user.click(await screen.findByRole('button', { name: 'Ajouter un élève' }))
    const form = await screen.findByRole('dialog', { name: 'Nouvel élève' })
    await user.type(within(form).getByLabelText(/^Matricule/), 'TW-0001')
    await user.click(within(form).getByRole('button', { name: "Ajouter l'élève" }))

    expect(await within(form).findByText('Ce matricule existe déjà.')).toBeInTheDocument()
    expect(within(form).getByLabelText(/^Matricule/)).toBeInvalid()
  })

  it('moves a class to its next class, leaving those who repeat the year', async () => {
    const { api, client } = signedIn({
      ...structure,
      'GET /students?classId=c1': [json(200, [AWA, BEN]), json(200, [BEN])],
      'POST /students/move': json(200, { moved: 1 }),
    })
    renderApp(client, '/school/students')

    const user = userEvent.setup()
    await pickClass(user, '6e A')
    await user.click(await screen.findByRole('button', { name: 'Faire passer la classe' }))
    const form = await screen.findByRole('dialog', { name: 'Faire passer la classe 6e A' })
    await within(form).findByRole('option', { name: '5e B' })
    expect(within(form).queryByRole('option', { name: '6e A' })).not.toBeInTheDocument()
    await user.selectOptions(within(form).getByLabelText(/^Vers la classe/), '5e B')
    await user.click(within(form).getByRole('checkbox', { name: /INVENTÉ Ben/ }))
    expect(within(form).getByText('1 élève reste en 6e A.')).toBeInTheDocument()
    await user.click(within(form).getByRole('button', { name: 'Déplacer 1 élève' }))

    expect(await screen.findByText('1 élève est passé en 5e B.')).toBeInTheDocument()
    expect(api.calls.find((c) => c.path === '/students/move')?.body).toEqual({ studentIds: ['s1'], classId: 'c2' })
  })

  it('shows a student with the guardians, the primary first', async () => {
    const { client } = signedIn({ 'GET /students/s1': json(200, STUDENT) })
    renderApp(client, '/school/students/s1')

    expect(await screen.findByRole('heading', { name: 'FICTIF Awa' })).toBeInTheDocument()
    expect(screen.getByText('TW-0001')).toBeInTheDocument()
    const guardians = screen.getByRole('region', { name: 'Tuteurs' })
    const rows = within(guardians).getAllByRole('listitem')
    expect(within(rows[0]).getByText('Parent Fictif')).toBeInTheDocument()
    expect(within(rows[0]).getByText('Tuteur principal')).toBeInTheDocument()
    expect(within(rows[0]).getByText("Utilise l'application")).toBeInTheDocument()
    expect(within(rows[1]).getByText('Prévenu par SMS')).toBeInTheDocument()
  })

  it('warns that a new phone signs the guardian out, then saves it in a body', async () => {
    const { api, client } = signedIn({
      'GET /students/s1': json(200, STUDENT),
      'PUT /guardians/g1': json(200, {}),
    })
    renderApp(client, '/school/students/s1')

    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Modifier Parent Fictif' }))
    const form = await screen.findByRole('dialog', { name: 'Modifier le tuteur' })
    expect(within(form).queryByText(/il est déconnecté/)).not.toBeInTheDocument()
    expect(within(form).getByText(/2 enfants dans l'école/)).toBeInTheDocument()
    const phone = within(form).getByLabelText('Parent Fictif : Téléphone')
    await user.clear(phone)
    await user.type(phone, '+237690000009')
    expect(within(form).getByText(/il est déconnecté/)).toBeInTheDocument()
    await user.click(within(form).getByRole('button', { name: 'Enregistrer' }))

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    const puts = api.calls.filter((c) => c.method === 'PUT')
    expect(puts.map((c) => c.path)).toEqual(['/guardians/g1']) // the link did not change
    expect(puts[0].body).toEqual({ name: 'Parent Fictif', phone: '+237690000009', language: 'FR' })
    expect(api.calls.every((c) => !c.path.includes('690000009'))).toBe(true) // NFR-9
  })

  it('hands the primary guardian over without touching the guardian', async () => {
    const { api, client } = signedIn({
      'GET /students/s1': json(200, STUDENT),
      'PUT /students/s1/guardians/g2': json(200, STUDENT),
    })
    renderApp(client, '/school/students/s1')

    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Modifier Père Fictif' }))
    const form = await screen.findByRole('dialog', { name: 'Modifier le tuteur' })
    await user.click(within(form).getByLabelText("Tuteur principal de l'élève"))
    await user.click(within(form).getByRole('button', { name: 'Enregistrer' }))

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    const puts = api.calls.filter((c) => c.method === 'PUT')
    expect(puts.map((c) => c.path)).toEqual(['/students/s1/guardians/g2'])
    expect(puts[0].body).toEqual({ relation: 'FATHER', primary: true })
  })

  it('asks who becomes the primary when the primary guardian is removed', async () => {
    const three = { ...STUDENT, guardians: [MOTHER, FATHER, UNCLE] }
    const { api, client } = signedIn({
      'GET /students/s1': json(200, three),
      'DELETE /students/s1/guardians/g1?primary=g3': json(200, three),
    })
    renderApp(client, '/school/students/s1')

    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Retirer Parent Fictif' }))
    const form = await screen.findByRole('dialog', { name: 'Retirer Parent Fictif ?' })
    expect(within(form).getByRole('button', { name: 'Retirer' })).toBeDisabled()
    await user.click(within(form).getByRole('radio', { name: 'Oncle Fictif' }))
    await user.click(within(form).getByRole('button', { name: 'Retirer' }))

    await waitFor(() => expect(api.calls.some((c) => c.method === 'DELETE')).toBe(true))
  })

  it('does not offer to remove the only guardian', async () => {
    const { client } = signedIn({ 'GET /students/s1': json(200, { ...STUDENT, guardians: [MOTHER] }) })
    renderApp(client, '/school/students/s1')

    expect(await screen.findByRole('button', { name: 'Modifier Parent Fictif' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Retirer Parent Fictif' })).not.toBeInTheDocument()
  })

  it('adds a guardian to the student', async () => {
    const { api, client } = signedIn({
      'GET /students/s1': json(200, STUDENT),
      'POST /students/s1/guardians': json(201, STUDENT),
    })
    renderApp(client, '/school/students/s1')

    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Ajouter un tuteur' }))
    const form = await screen.findByRole('dialog', { name: 'Ajouter un tuteur' })
    await user.type(within(form).getByLabelText('Nouveau tuteur : Téléphone'), '+237690000003')
    await user.type(within(form).getByLabelText('Nouveau tuteur : Nom du tuteur'), 'Oncle Fictif')
    await user.selectOptions(within(form).getByLabelText(/^Lien/), 'Autre')
    await user.click(within(form).getByRole('button', { name: 'Ajouter un tuteur' }))

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(api.calls.find((c) => c.method === 'POST')?.body).toEqual({
      phone: '+237690000003',
      name: 'Oncle Fictif',
      relation: 'OTHER',
      language: null,
      primary: false,
    })
  })

  it('marks a student as having left, after saying what it changes', async () => {
    const { api, client } = signedIn({
      'GET /students/s1': [json(200, STUDENT), json(200, { ...STUDENT, leftOn: '2026-11-02' })],
      'POST /students/s1/leave': json(200, STUDENT),
    })
    renderApp(client, '/school/students/s1')

    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Marquer comme parti' }))
    const form = await screen.findByRole('dialog', { name: "Awa a quitté l'école ?" })
    expect(within(form).getByText(/n'est plus dans aucun appel/)).toBeInTheDocument()
    const date = within(form).getByLabelText(/^Date de départ/)
    await user.clear(date)
    await user.type(date, '2026-11-02')
    await user.click(within(form).getByRole('button', { name: 'Marquer comme parti' }))

    expect(await screen.findByText('Parti le 2 nov. 2026')).toBeInTheDocument()
    expect(api.calls.find((c) => c.method === 'POST')?.body).toEqual({ date: '2026-11-02' })
    expect(await screen.findByRole('button', { name: 'Réinscrire' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Changer de classe' })).not.toBeInTheDocument()
  })

  it('brings a student back into a class', async () => {
    const { api, client } = signedIn({
      ...structure,
      'GET /students/s1': [json(200, { ...STUDENT, leftOn: '2026-11-02' }), json(200, { ...STUDENT, classId: 'c2', className: '5e B' })],
      'POST /students/s1/return': json(200, STUDENT),
    })
    renderApp(client, '/school/students/s1')

    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Réinscrire' }))
    const form = await screen.findByRole('dialog', { name: 'Réinscrire Awa' })
    await within(form).findByRole('option', { name: '5e B' })
    await user.selectOptions(within(form).getByLabelText(/^Nouvelle classe/), '5e B')
    await user.click(within(form).getByRole('button', { name: 'Réinscrire' }))

    expect(await screen.findByRole('button', { name: 'Marquer comme parti' })).toBeInTheDocument()
    expect(api.calls.find((c) => c.method === 'POST')?.body).toEqual({ classId: 'c2' })
  })

  it('changes the class of one student', async () => {
    const { api, client } = signedIn({
      ...structure,
      'GET /students/s1': [json(200, STUDENT), json(200, { ...STUDENT, classId: 'c2', className: '5e B' })],
      'PUT /students/s1/class': json(200, STUDENT),
    })
    renderApp(client, '/school/students/s1')

    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Changer de classe' }))
    const form = await screen.findByRole('dialog', { name: 'Changer Awa de classe' })
    await within(form).findByRole('option', { name: '5e B' })
    expect(within(form).queryByRole('option', { name: '6e A' })).not.toBeInTheDocument()
    await user.selectOptions(within(form).getByLabelText(/^Nouvelle classe/), '5e B')
    await user.click(within(form).getByRole('button', { name: 'Changer de classe' }))

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(api.calls.find((c) => c.method === 'PUT')?.body).toEqual({ classId: 'c2' })
    expect(await screen.findByText(/5e B/)).toBeInTheDocument()
  })

  it('corrects the details of a student', async () => {
    const { api, client } = signedIn({
      'GET /students/s1': [json(200, STUDENT), json(200, { ...STUDENT, firstName: 'Awah' })],
      'PUT /students/s1': json(200, STUDENT),
    })
    renderApp(client, '/school/students/s1')

    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Modifier la fiche' }))
    const form = await screen.findByRole('dialog', { name: 'Modifier la fiche' })
    await user.type(within(form).getByLabelText(/^Prénom/), 'h')
    await user.click(within(form).getByRole('button', { name: 'Enregistrer' }))

    expect(await screen.findByRole('heading', { name: 'FICTIF Awah' })).toBeInTheDocument()
    expect(api.calls.find((c) => c.method === 'PUT')?.body).toEqual({ matricule: 'TW-0001', lastName: 'Fictif', firstName: 'Awah', sex: 'F', birthYear: 2014 })
  })

  it('keeps the screens to the admin, and puts them in the navigation', async () => {
    const dm = signedIn({}, ['DM']) // sent back to the DM's home (UC-D8)
    const first = renderApp(dm.client, '/school/students/s1')
    expect(await screen.findByRole('heading', { name: 'Vos classes' })).toBeInTheDocument()
    expect(first.state.location.pathname).toBe('/')
  })

  it('shows the admin the screen in the navigation, current on a student', async () => {
    const { client } = signedIn({ 'GET /students/s1': json(200, STUDENT) })
    renderApp(client, '/school/students/s1')

    const nav = await screen.findByRole('navigation', { name: 'Navigation principale' })
    expect(within(nav).getByRole('link', { name: 'Élèves' })).toHaveAttribute('aria-current', 'page')
  })
})
