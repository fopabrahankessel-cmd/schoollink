import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { SCHOOL_KEY } from '../auth/schoolMemory'
import { LANGUAGE_KEY } from '../i18n'
import { fakeFetch, json, newClient, renderApp, tokens } from '../test/fakeApi'

const SCHOOL_A = { id: '00000000-0000-4000-8000-0000000000aa', name: 'Lycée Démo A (fictif)' }
const SCHOOL_B = { id: '00000000-0000-4000-8000-0000000000bb', name: 'Demo High School B (fictional)' }

async function fillAndSubmit(login = 'admin@lycee-demo-a.test', password = 'schoollink-dev') {
  const user = userEvent.setup()
  await user.type(screen.getByLabelText(/Téléphone ou e-mail/), login)
  await user.type(screen.getByLabelText(/^Mot de passe/), password)
  await user.click(screen.getByRole('button', { name: 'Se connecter' }))
  return user
}

describe('LoginPage', () => {
  it('shows the form in French and switches to English (NFR-12)', async () => {
    renderApp(newClient(fakeFetch({}).fetch), '/login')
    expect(screen.getByRole('heading', { name: 'Connexion' })).toBeInTheDocument()
    expect(screen.getByLabelText(/Téléphone ou e-mail/)).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'English' }))

    expect(screen.getByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
    expect(screen.getByLabelText(/Phone or email/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Sign in' })).toBeInTheDocument()
    expect(localStorage.getItem(LANGUAGE_KEY)).toBe('en')
    expect(document.documentElement.lang).toBe('en')
  })

  it('signs in and opens the home page', async () => {
    const api = fakeFetch({ 'POST /auth/login': json(200, tokens({ roles: ['ADMIN'] })) })
    renderApp(newClient(api.fetch), '/login')

    await fillAndSubmit()

    expect(await screen.findByRole('heading', { name: 'Bienvenue sur SchoolLink' })).toBeInTheDocument()
    expect(screen.getByText('Administrateur')).toBeInTheDocument()
    expect(api.calls[0].body).toEqual({ login: 'admin@lycee-demo-a.test', password: 'schoollink-dev' })
    expect(api.calls[0].headers['Accept-Language']).toBe('fr')
  })

  it('shows the server message when sign-in fails', async () => {
    const api = fakeFetch({
      'POST /auth/login': json(401, { code: 'auth.invalid_credentials', message: 'Identifiant ou mot de passe incorrect.' }),
    })
    renderApp(newClient(api.fetch), '/login')

    await fillAndSubmit('admin@lycee-demo-a.test', 'wrong-password')

    expect(await screen.findByRole('alert')).toHaveTextContent('Identifiant ou mot de passe incorrect.')
    expect(screen.getByRole('heading', { name: 'Connexion' })).toBeInTheDocument()
  })

  it('says when the server cannot be reached', async () => {
    renderApp(newClient(fakeFetch({ 'POST /auth/login': new TypeError('Failed to fetch') }).fetch), '/login')

    await fillAndSubmit()

    expect(await screen.findByRole('alert')).toHaveTextContent('Impossible de joindre le serveur.')
  })

  it('asks for the school when the account exists in several schools, then remembers it (D16)', async () => {
    const api = fakeFetch({
      'POST /auth/login': [
        json(400, { code: 'auth.school_required', message: 'Choisissez votre établissement.', field: 'schoolId' }),
        json(200, tokens()),
      ],
      'GET /auth/schools': json(200, [SCHOOL_A, SCHOOL_B]),
    })
    renderApp(newClient(api.fetch), '/login')

    const user = await fillAndSubmit('enseignant.partage@demo.test')
    const picker = await screen.findByLabelText(/Établissement/)
    expect(screen.getByText(/Ce compte existe dans plusieurs établissements/)).toBeInTheDocument()
    await user.selectOptions(picker, SCHOOL_B.id)
    await user.click(screen.getByRole('button', { name: 'Se connecter' }))

    expect(await screen.findByRole('heading', { name: 'Bienvenue sur SchoolLink' })).toBeInTheDocument()
    const logins = api.calls.filter((c) => c.path === '/auth/login')
    expect(logins[1].body).toEqual({ login: 'enseignant.partage@demo.test', password: 'schoollink-dev', schoolId: SCHOOL_B.id })
    expect(localStorage.getItem(SCHOOL_KEY)).toBe(SCHOOL_B.id)
  })

  it('preselects the school remembered on this device', async () => {
    localStorage.setItem(SCHOOL_KEY, SCHOOL_A.id)
    const api = fakeFetch({ 'GET /auth/schools': json(200, [SCHOOL_A, SCHOOL_B]), 'POST /auth/login': json(200, tokens()) })
    renderApp(newClient(api.fetch), '/login')

    await waitFor(() => expect(screen.getByLabelText(/Établissement/)).toHaveValue(SCHOOL_A.id))
    await fillAndSubmit()

    await screen.findByRole('heading', { name: 'Bienvenue sur SchoolLink' })
    expect(api.calls.find((c) => c.path === '/auth/login')?.body).toMatchObject({ schoolId: SCHOOL_A.id })
  })

  it('sends a user with a temporary password to the password page first (D18)', async () => {
    const api = fakeFetch({
      'POST /auth/login': json(200, tokens({ passwordChangeRequired: true })),
      'POST /me/password': json(200, tokens({ passwordChangeRequired: false }, 2)),
    })
    renderApp(newClient(api.fetch), '/login')

    const user = await fillAndSubmit('prof1@lycee-demo-a.test', 'Temporary-123')
    expect(await screen.findByRole('heading', { name: 'Choisissez votre mot de passe' })).toBeInTheDocument()
    const save = screen.getByRole('button', { name: 'Enregistrer' })

    await user.type(screen.getByLabelText(/Mot de passe actuel/), 'Temporary-123')
    await user.type(screen.getByLabelText(/^Nouveau mot de passe/), 'short')
    expect(screen.getByText('Le mot de passe doit contenir au moins 8 caractères.')).toBeInTheDocument()
    await user.type(screen.getByLabelText(/^Nouveau mot de passe/), '-but-long-now')
    await user.type(screen.getByLabelText(/Confirmez/), 'short-but-long-now')
    expect(save).toBeEnabled()
    await user.click(save)

    expect(await screen.findByRole('heading', { name: 'Bienvenue sur SchoolLink' })).toBeInTheDocument()
    expect(api.calls.find((c) => c.path === '/me/password')).toMatchObject({
      body: { currentPassword: 'Temporary-123', newPassword: 'short-but-long-now' },
      headers: { Authorization: 'Bearer access-1' },
    })
  })

  it('tells roles without web screens to use the mobile app', async () => {
    renderApp(newClient(fakeFetch({ 'POST /auth/login': json(200, tokens({ roles: ['TEACHER'] })) }).fetch), '/login')

    await fillAndSubmit('prof1@lycee-demo-a.test')

    expect(await screen.findByText(/Utilisez l'application mobile SchoolLink/)).toBeInTheDocument()
  })

  it('keeps the session across a reload with the stored refresh token', async () => {
    sessionStorage.setItem('schoollink.refreshToken', 'refresh-1')
    const api = fakeFetch({ 'POST /auth/refresh': json(200, tokens({}, 2)) })
    renderApp(newClient(api.fetch), '/')

    expect(await screen.findByRole('heading', { name: 'Bienvenue sur SchoolLink' })).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Se déconnecter' }))
    expect(await screen.findByRole('heading', { name: 'Connexion' })).toBeInTheDocument()
    expect(sessionStorage.getItem('schoollink.refreshToken')).toBeNull()
  })
})
