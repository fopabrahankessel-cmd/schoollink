import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Letterhead } from '../../api/letterhead'
import { fakeFetch, json, newClient, renderApp, tokens } from '../../test/fakeApi'

// Generated data only: a made-up school and republic.
const HEAD: Letterhead = {
  schoolName: 'Lycée Fictif X',
  configured: true,
  layout: 'BILINGUAL',
  french: [
    { text: 'RÉPUBLIQUE FICTIVE', bold: true, size: 'NORMAL' },
    { text: 'Paix – Travail', bold: false, size: 'SMALL' },
  ],
  english: [{ text: 'FICTIONAL REPUBLIC', bold: true, size: 'NORMAL' }],
  hasLogo: false,
  logoVersion: null,
}
const WITH_LOGO: Letterhead = { ...HEAD, hasLogo: true, logoVersion: '1793635722000' }
const png = () => new Response('png', { headers: { 'Content-Type': 'image/png' } })

function signedIn(script: Parameters<typeof fakeFetch>[0], roles: ('ADMIN' | 'DM')[] = ['ADMIN']) {
  const api = fakeFetch(script)
  const client = newClient(api.fetch)
  client.startSession(tokens({ roles }))
  return { api, client }
}

describe('letterhead editor (UC-A7, D36)', () => {
  beforeEach(() => {
    // jsdom has no object URLs
    URL.createObjectURL = vi.fn(() => 'blob:logo')
    URL.revokeObjectURL = vi.fn()
  })
  afterEach(() => vi.restoreAllMocks())

  it('the admin edits lines, moves one up, makes one bold and saves', async () => {
    const { api, client } = signedIn({
      'GET /school/letterhead': json(200, HEAD),
      'PUT /school/letterhead': json(200, HEAD),
    })
    renderApp(client, '/structure/letterhead')
    const user = userEvent.setup()

    const french = await screen.findByRole('group', { name: 'Colonne française' })
    const second = within(french).getByRole('group', { name: 'Ligne 2' })
    // pasted, not typed: a keystroke at a time is slow and not what this test is about
    await user.clear(within(second).getByLabelText('Texte'))
    await user.paste('  Paix – Travail – Patrie ')
    await user.click(within(second).getByRole('button', { name: 'Monter' }))
    const first = within(french).getByRole('group', { name: 'Ligne 1' })
    expect(within(first).getByLabelText('Texte')).toHaveValue('  Paix – Travail – Patrie ')
    await user.click(within(first).getByRole('button', { name: 'Gras' }))
    await user.selectOptions(within(first).getByLabelText('Taille'), 'LARGE')

    const english = screen.getByRole('group', { name: 'Colonne anglaise' })
    await user.click(within(english).getByRole('button', { name: 'Ajouter une ligne' }))
    await user.click(within(within(english).getByRole('group', { name: 'Ligne 2' })).getByLabelText('Texte'))
    await user.paste('Peace – Work')
    await user.click(screen.getByRole('button', { name: 'Enregistrer' }))

    expect(await screen.findByText("L'en-tête est enregistré.")).toBeInTheDocument()
    expect(api.calls.find((c) => c.method === 'PUT')?.body).toEqual({
      layout: 'BILINGUAL',
      french: [
        { text: 'Paix – Travail – Patrie', bold: true, size: 'LARGE' },
        { text: 'RÉPUBLIQUE FICTIVE', bold: true, size: 'NORMAL' },
      ],
      english: [
        { text: 'FICTIONAL REPUBLIC', bold: true, size: 'NORMAL' },
        { text: 'Peace – Work', bold: false, size: 'NORMAL' },
      ],
    })
    const nav = screen.getByRole('navigation', { name: 'Navigation principale' })
    expect(within(nav).getByRole('link', { name: 'En-tête des documents' })).toHaveAttribute('href', '/structure/letterhead')
  })

  it('does not save an empty line, and stops adding at 12 lines', async () => {
    const twelve = Array.from({ length: 12 }, (_, i) => ({ text: `Ligne fictive ${i + 1}`, bold: false, size: 'NORMAL' as const }))
    const { api, client } = signedIn({ 'GET /school/letterhead': json(200, { ...HEAD, french: twelve }) })
    renderApp(client, '/structure/letterhead')
    const user = userEvent.setup()

    const french = await screen.findByRole('group', { name: 'Colonne française' })
    expect(within(french).getByRole('button', { name: 'Ajouter une ligne' })).toBeDisabled()
    const english = screen.getByRole('group', { name: 'Colonne anglaise' })
    await user.click(within(english).getByRole('button', { name: 'Ajouter une ligne' }))
    await user.click(screen.getByRole('button', { name: 'Enregistrer' }))

    expect(await within(english).findByText('Saisissez le texte de la ligne, ou supprimez-la.')).toBeInTheDocument()
    expect(api.calls.some((c) => c.method === 'PUT')).toBe(false)
  })

  it('the preview shows French left, logo centre, English right, and one centred column for a one-language layout', async () => {
    const { api, client } = signedIn({
      'GET /school/letterhead': json(200, WITH_LOGO),
      'GET /school/letterhead/logo': png,
    })
    renderApp(client, '/structure/letterhead')
    const user = userEvent.setup()

    const preview = await screen.findByRole('figure', { name: "Aperçu de l'en-tête" })
    await waitFor(() => expect(within(preview).getByRole('img', { name: 'Logo' })).toHaveAttribute('src', 'blob:logo'))
    const parts = () => within(preview).getAllByRole('group').map((g) => g.getAttribute('aria-label'))
    expect(parts()).toEqual(['Partie française', 'Logo', 'Partie anglaise'])
    const frenchPart = within(preview).getByRole('group', { name: 'Partie française' })
    expect(within(frenchPart).getByText('RÉPUBLIQUE FICTIVE')).toHaveStyle({ fontWeight: '700' })
    expect(api.calls.find((c) => c.path === '/school/letterhead/logo')?.headers.Authorization).toBe('Bearer access-1')

    await user.click(screen.getByRole('radio', { name: 'Français seul' }))

    expect(parts()).toEqual(['Logo', 'Partie française'])
    expect(within(preview).getByRole('group', { name: 'Partie française' })).toHaveStyle({ textAlign: 'center' })
    expect(within(preview).queryByText('FICTIONAL REPUBLIC')).not.toBeInTheDocument()
  })

  it('the preview of an unset letterhead is the school name alone', async () => {
    const unset: Letterhead = { ...HEAD, configured: false, french: [], english: [] }
    const { client } = signedIn({ 'GET /school/letterhead': json(200, unset) })
    renderApp(client, '/structure/letterhead')

    const preview = await screen.findByRole('figure', { name: "Aperçu de l'en-tête" })
    expect(within(preview).getByText('Lycée Fictif X')).toBeInTheDocument()
    expect(within(preview).queryAllByRole('group')).toHaveLength(0)
  })

  it('uploading a logo sends multipart "file" and shows the server refusal for a wrong type', async () => {
    const { api, client } = signedIn({
      'GET /school/letterhead': json(200, HEAD),
      'PUT /school/letterhead/logo': json(400, {
        code: 'letterhead.logo_type',
        message: 'Le logo doit être une image PNG ou JPEG.',
        field: 'file',
      }),
    })
    renderApp(client, '/structure/letterhead')
    const user = userEvent.setup()

    await user.upload(await screen.findByLabelText('Choisir un logo'), new File(['GIF89a'], 'logo.png', { type: 'image/png' }))

    expect(await screen.findByText('Le logo doit être une image PNG ou JPEG.')).toBeInTheDocument()
    expect(api.calls.find((c) => c.method === 'PUT')?.body).toEqual({ file: 'logo.png' })
  })

  it('refuses a logo over 1 MB before sending it, and shows an accepted logo at once', async () => {
    const { api, client } = signedIn({
      'GET /school/letterhead': json(200, HEAD),
      'PUT /school/letterhead/logo': json(200, WITH_LOGO),
      'GET /school/letterhead/logo': png,
    })
    renderApp(client, '/structure/letterhead')
    const user = userEvent.setup()
    const input = await screen.findByLabelText('Choisir un logo')

    await user.upload(input, new File([new Uint8Array(1_048_577)], 'grand.png', { type: 'image/png' }))
    expect(await screen.findByText('Le logo ne doit pas dépasser 1 Mo.')).toBeInTheDocument()
    expect(api.calls.some((c) => c.method === 'PUT')).toBe(false)

    await user.upload(input, new File(['png'], 'logo.png', { type: 'image/png' }))
    const preview = screen.getByRole('figure', { name: "Aperçu de l'en-tête" })
    expect(await within(preview).findByRole('img', { name: 'Logo' })).toHaveAttribute('src', 'blob:logo')
    expect(screen.getByRole('button', { name: 'Retirer le logo' })).toBeInTheDocument()
  })

  it('the DM has no letterhead page', async () => {
    const { client } = signedIn({}, ['DM'])
    renderApp(client, '/structure/letterhead')

    await waitFor(() => expect(screen.queryByRole('heading', { name: 'En-tête des documents' })).not.toBeInTheDocument())
    const nav = screen.getByRole('navigation', { name: 'Navigation principale' })
    expect(within(nav).queryByRole('link', { name: 'En-tête des documents' })).not.toBeInTheDocument()
  })
})
