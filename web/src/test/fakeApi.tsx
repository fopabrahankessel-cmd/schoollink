import { CssBaseline, ThemeProvider } from '@mui/material'
import { render } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { ApiClient, type TokenResponse, type UserInfo } from '../api/client'
import { AuthProvider } from '../auth/AuthContext'
import i18n from '../i18n'
import { routes } from '../routes'
import { theme } from '../theme'

export interface Call {
  method: string
  path: string
  headers: Record<string, string>
  body: unknown
}

type Reply = Response | (() => Response) | Error

/**
 * A fetch that answers from a script: "POST /auth/login" -> replies, used in order (the last one
 * repeats). Unknown routes fail the test. Generated data only.
 */
export function fakeFetch(script: Record<string, Reply | Reply[]>) {
  const calls: Call[] = []
  const used: Record<string, number> = {}
  const fetchFn = async (input: RequestInfo | URL, init?: RequestInit): Promise<Response> => {
    const method = init?.method ?? 'GET'
    const path = String(input).replace(/^\/api\/v1/, '')
    calls.push({
      method,
      path,
      headers: (init?.headers ?? {}) as Record<string, string>,
      body: init?.body ? JSON.parse(String(init.body)) : undefined,
    })
    const key = `${method} ${path}`
    const replies = script[key]
    if (replies === undefined) throw new Error(`unexpected request ${key}`)
    const list = Array.isArray(replies) ? replies : [replies]
    const index = Math.min(used[key] ?? 0, list.length - 1)
    used[key] = (used[key] ?? 0) + 1
    const reply = list[index]
    if (reply instanceof Error) throw reply
    return typeof reply === 'function' ? reply() : reply.clone()
  }
  return { fetch: fetchFn as typeof fetch, calls }
}

export function json(status: number, body: unknown): () => Response {
  return () => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

export const fakeUser: UserInfo = {
  id: '00000000-0000-4000-8000-000000000001',
  schoolId: '00000000-0000-4000-8000-0000000000aa',
  roles: ['ADMIN'],
  language: 'FR',
  passwordChangeRequired: false,
}

export function tokens(user: Partial<UserInfo> = {}, n = 1): TokenResponse {
  return {
    tokenType: 'Bearer',
    accessToken: `access-${n}`,
    expiresIn: 900,
    refreshToken: `refresh-${n}`,
    user: { ...fakeUser, ...user },
  }
}

export function newClient(fetchFn: typeof fetch) {
  return new ApiClient({ fetch: fetchFn, storage: sessionStorage, language: () => i18n.resolvedLanguage ?? 'fr' })
}

export function renderApp(client: ApiClient, path = '/') {
  const router = createMemoryRouter(routes, { initialEntries: [path] })
  render(
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <AuthProvider client={client}>
        <RouterProvider router={router} />
      </AuthProvider>
    </ThemeProvider>,
  )
  return router
}
