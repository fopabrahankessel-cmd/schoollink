import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { StudentImport } from '../../api/imports'
import { fakeFetch, json, newClient, renderApp, tokens } from '../../test/fakeApi'

// Generated data only: made-up names, no real school's file (CLAUDE.md).
const YEAR = { id: 'y1', label: '2026-2027', startDate: '2026-09-07', endDate: '2027-06-30' }
const WITH_ERRORS: StudentImport = {
  id: 'i1',
  academicYearId: 'y1',
  fileName: 'eleves.csv',
  status: 'PREVIEW',
  rowCount: 4,
  validCount: 2,
  errorCount: 2,
  errors: [
    { row: 3, column: 'nom', code: 'validation.not_blank', message: 'Ce champ est obligatoire.' },
    { row: 3, column: 'sexe', code: 'import.sex_invalid', message: 'Écrivez M ou F.' },
    { row: 5, column: 'classe', code: 'import.class_unknown', message: 'La classe « 6e Z » n’existe pas pour cette année scolaire.' },
  ],
}
const CLEAN: StudentImport = { ...WITH_ERRORS, id: 'i2', validCount: 4, errorCount: 0, errors: [] }

function signedIn(script: Parameters<typeof fakeFetch>[0]) {
  const api = fakeFetch({ 'GET /academic-years': json(200, [YEAR]), ...script })
  const client = newClient(api.fetch)
  client.startSession(tokens())
  return { api, client }
}

const csv = (name = 'eleves.csv') => new File(['matricule;nom\nEX-0001;Exemple\n'], name, { type: 'text/csv' })

describe('import wizard (UC-A3)', () => {
  it('uploads the file for the year, then lists the errors to correct', async () => {
    const { api, client } = signedIn({
      'POST /students/import': json(201, WITH_ERRORS),
      'GET /students/import/i1': json(200, WITH_ERRORS),
    })
    const router = renderApp(client, '/students/import')
    const user = userEvent.setup()

    const check = await screen.findByRole('button', { name: 'Vérifier le fichier' })
    expect(check).toBeDisabled()
    await user.upload(screen.getByLabelText('Fichier des élèves'), csv())
    expect(screen.getByText('eleves.csv')).toBeInTheDocument()
    await user.click(check)

    const errors = await screen.findByRole('region', { name: 'Erreurs à corriger' })
    expect(api.calls.find((c) => c.method === 'POST')?.body).toEqual({ academicYearId: 'y1', file: 'eleves.csv' })
    expect(router.state.location.pathname).toBe('/students/import/i1')
    expect(screen.getByText('Fichier eleves.csv, année 2026-2027')).toBeInTheDocument()
    expect(within(errors).getByText('3 problèmes')).toBeInTheDocument()
    const rows = within(errors).getAllByRole('row').slice(1)
    expect(rows.map((r) => within(r).getAllByRole('cell').map((c) => c.textContent))).toEqual([
      ['3', 'nom', 'Ce champ est obligatoire.'],
      ['3', 'sexe', 'Écrivez M ou F.'],
      ['5', 'classe', 'La classe « 6e Z » n’existe pas pour cette année scolaire.'],
    ])
    // D19: a file with errors never commits; the admin uploads a corrected one for the same year
    expect(screen.queryByRole('button', { name: /^Importer/ })).not.toBeInTheDocument()
    expect(within(errors).getByRole('link', { name: 'Envoyer un fichier corrigé' })).toHaveAttribute(
      'href',
      '/students/import?year=y1',
    )
  })

  it('refuses a file that is not CSV or Excel before sending it', async () => {
    const { api, client } = signedIn({})
    renderApp(client, '/students/import')
    const user = userEvent.setup({ applyAccept: false })

    await user.upload(await screen.findByLabelText('Fichier des élèves'), csv('eleves.pdf'))

    expect(screen.getByText('Choisissez un fichier CSV (.csv) ou Excel (.xlsx).')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Vérifier le fichier' })).toBeDisabled()
    expect(api.calls.some((c) => c.method === 'POST')).toBe(false)
  })

  it("shows the server's message when the whole file is refused", async () => {
    const { client } = signedIn({
      'POST /students/import': json(400, {
        code: 'import.column_missing',
        message: "Colonnes manquantes : classe. Utilisez le modèle d'import.",
        field: 'file',
      }),
    })
    const router = renderApp(client, '/students/import')
    const user = userEvent.setup()

    await user.upload(await screen.findByLabelText('Fichier des élèves'), csv())
    await user.click(screen.getByRole('button', { name: 'Vérifier le fichier' }))

    expect(await screen.findByText("Colonnes manquantes : classe. Utilisez le modèle d'import.")).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/students/import')
  })

  it('commits a clean file after confirmation and shows what was created', async () => {
    const { api, client } = signedIn({
      'GET /students/import/i2': json(200, CLEAN),
      'POST /students/import/i2/commit': json(200, {
        id: 'i2',
        status: 'COMMITTED',
        studentsCreated: 4,
        guardiansCreated: 3,
        guardiansLinked: 1,
      }),
    })
    renderApp(client, '/students/import/i2')
    const user = userEvent.setup()

    expect(await screen.findByText('Les 4 lignes sont valides')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Importer 4 élèves' }))
    const confirm = await screen.findByRole('dialog', { name: 'Importer ces élèves ?' })
    expect(within(confirm).getByText("4 élèves seront ajoutés à l'année 2026-2027, avec leurs tuteurs.")).toBeInTheDocument()
    await user.click(within(confirm).getByRole('button', { name: 'Importer 4 élèves' }))

    const done = await screen.findByRole('region', { name: 'Import terminé' })
    expect(api.calls.filter((c) => c.path === '/students/import/i2/commit')).toHaveLength(1)
    expect(within(done).getByText('Élèves créés').nextSibling).toHaveTextContent('4')
    expect(within(done).getByText('Tuteurs créés').nextSibling).toHaveTextContent('3')
    expect(within(done).getByText('Tuteurs déjà connus, reliés').nextSibling).toHaveTextContent('1')
  })

  it('explains a refused commit (D19: the school changed) and offers a new upload', async () => {
    const { client } = signedIn({
      'GET /students/import/i2': json(200, CLEAN),
      'POST /students/import/i2/commit': json(409, {
        code: 'import.stale',
        message: "L'établissement a changé depuis l'aperçu. Envoyez de nouveau le fichier.",
      }),
    })
    renderApp(client, '/students/import/i2')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Importer 4 élèves' }))
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Importer 4 élèves' }))

    expect(await screen.findByText("L'établissement a changé depuis l'aperçu. Envoyez de nouveau le fichier.")).toBeInTheDocument()
    // findBy: the page is hidden from assistive technology until the dialog has closed
    expect(await screen.findByRole('link', { name: 'Envoyer de nouveau le fichier' })).toHaveAttribute(
      'href',
      '/students/import?year=y1',
    )
  })

  it('says so when the import was already committed or has expired', async () => {
    const { client } = signedIn({
      'GET /students/import/i2': json(200, { ...CLEAN, status: 'COMMITTED' }),
      'GET /students/import/i3': json(200, { ...CLEAN, id: 'i3', status: 'EXPIRED' }),
    })
    const router = renderApp(client, '/students/import/i2')

    expect(await screen.findByText('Ce fichier a déjà été importé.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^Importer/ })).not.toBeInTheDocument()

    await router.navigate('/students/import/i3')
    expect(await screen.findByText(/Cet aperçu a expiré/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Envoyer de nouveau le fichier' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^Importer/ })).not.toBeInTheDocument()
  })
})
