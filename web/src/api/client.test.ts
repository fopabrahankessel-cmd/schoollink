import { describe, expect, it } from 'vitest'
import { fakeFetch, json, newClient, tokens } from '../test/fakeApi'
import { ApiError, NETWORK_ERROR, REFRESH_TOKEN_KEY, SERVER_ERROR, type UserInfo } from './client'

const expired = json(401, { code: 'auth.unauthorized', message: 'Veuillez vous connecter.' })

describe('ApiClient', () => {
  it('refreshes once for concurrent 401s and retries with the new token', async () => {
    const api = fakeFetch({
      'GET /me': [expired, expired, json(200, { ok: 1 }), json(200, { ok: 2 })],
      'POST /auth/refresh': json(200, tokens({}, 2)),
    })
    const client = newClient(api.fetch)
    client.startSession(tokens({}, 1))

    const results = await Promise.all([client.request('/me'), client.request('/me')])

    expect(results).toEqual([{ ok: 1 }, { ok: 2 }])
    const refreshes = api.calls.filter((c) => c.path === '/auth/refresh')
    expect(refreshes).toHaveLength(1)
    expect(refreshes[0].body).toEqual({ refreshToken: 'refresh-1' })
    expect(api.calls.slice(-2).map((c) => c.headers.Authorization)).toEqual(['Bearer access-2', 'Bearer access-2'])
    expect(sessionStorage.getItem(REFRESH_TOKEN_KEY)).toBe('refresh-2')
  })

  it('ends the session when the refresh token is refused', async () => {
    const api = fakeFetch({
      'GET /me': expired,
      'POST /auth/refresh': json(401, { code: 'auth.invalid_refresh_token', message: 'Session expirée.' }),
    })
    const client = newClient(api.fetch)
    const sessions: (UserInfo | null)[] = []
    client.onSession((u) => sessions.push(u))
    client.startSession(tokens())

    await expect(client.request('/me')).rejects.toMatchObject({ status: 401, code: 'auth.unauthorized' })

    expect(sessions.at(-1)).toBeNull()
    expect(client.hasSession()).toBe(false)
    expect(sessionStorage.getItem(REFRESH_TOKEN_KEY)).toBeNull()
  })

  it('signs out after a refresh under way, revoking the newest token', async () => {
    let finishRefresh: (r: Response) => void = () => undefined
    const api = fakeFetch({
      'GET /me': expired,
      'POST /auth/refresh': () => json(200, tokens({}, 2))(),
      'POST /auth/logout': () => new Response(null, { status: 204 }),
    })
    const slowFetch: typeof fetch = (input, init) =>
      String(input).endsWith('/auth/refresh')
        ? new Promise<Response>((resolve) => {
            finishRefresh = resolve
          }).then(() => api.fetch(input, init))
        : api.fetch(input, init)
    const client = newClient(slowFetch)
    client.startSession(tokens({}, 1))

    const request = client.request('/me').catch(() => undefined)
    await new Promise((r) => setTimeout(r, 0)) // the 401 arrives and the refresh starts
    const signOut = client.signOut()
    finishRefresh(new Response())
    await Promise.all([request, signOut])

    expect(client.hasSession()).toBe(false)
    expect(api.calls.find((c) => c.path === '/auth/logout')?.body).toEqual({ refreshToken: 'refresh-2' })
  })

  it('treats a 401 from sign-in as an answer, without refreshing', async () => {
    const api = fakeFetch({
      'POST /auth/login': json(401, { code: 'auth.invalid_credentials', message: 'Identifiant incorrect.' }),
    })
    const client = newClient(api.fetch)

    await expect(
      client.request('/auth/login', { method: 'POST', body: { login: 'x', password: 'y' }, auth: false }),
    ).rejects.toMatchObject({ code: 'auth.invalid_credentials', message: 'Identifiant incorrect.' })
    expect(api.calls).toHaveLength(1)
    expect(api.calls[0].headers.Authorization).toBeUndefined()
  })

  it('sends the interface language for the server messages', async () => {
    const api = fakeFetch({ 'GET /auth/schools': json(200, []) })
    await newClient(api.fetch).request('/auth/schools', { auth: false })
    expect(api.calls[0].headers['Accept-Language']).toBe('fr')
  })

  it('sends a FormData as multipart, leaving the Content-Type to fetch', async () => {
    const api = fakeFetch({ 'POST /upload': json(201, { ok: true }) })
    const client = newClient(api.fetch)
    client.startSession(tokens())
    const form = new FormData()
    form.append('file', new File(['matricule;nom'], 'eleves.csv', { type: 'text/csv' }))

    await client.request('/upload', { method: 'POST', body: form })

    expect(api.calls[0].headers['Content-Type']).toBeUndefined()
    expect(api.calls[0].headers.Authorization).toBe('Bearer access-1')
    expect(api.calls[0].body).toEqual({ file: 'eleves.csv' })
  })

  it('reports network failures and non-API answers with their own codes', async () => {
    const offline = newClient(fakeFetch({ 'GET /auth/schools': new TypeError('Failed to fetch') }).fetch)
    await expect(offline.request('/auth/schools', { auth: false })).rejects.toMatchObject({ code: NETWORK_ERROR })

    const proxy = newClient(
      fakeFetch({ 'GET /auth/schools': () => new Response('<html>Bad Gateway</html>', { status: 502 }) }).fetch,
    )
    const error = await proxy.request('/auth/schools', { auth: false }).catch((e: unknown) => e)
    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({ status: 502, code: SERVER_ERROR })
  })

  it('finds field messages in the error or its details', () => {
    const error = new ApiError(400, {
      code: 'validation.failed',
      message: 'Invalid',
      details: [{ code: 'validation.not_blank', message: 'Required', field: 'login' }],
    })
    expect(error.messageFor('login')).toBe('Required')
    expect(error.messageFor('password')).toBeUndefined()
  })
})
