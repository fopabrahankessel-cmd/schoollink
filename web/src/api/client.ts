// HTTP client for the SchoolLink API (/api/v1). The access token lives in memory only; the
// refresh token is kept in sessionStorage, so a reload keeps the session and closing the tab
// ends it (NFR-7). On a 401 the client refreshes once, shared by concurrent requests, then retries.

export type Role = 'ADMIN' | 'TEACHER' | 'ASSISTANT_DM' | 'DM' | 'COUNSELLOR' | 'PARENT' | 'RESEARCHER'

export interface UserInfo {
  id: string
  schoolId: string | null
  roles: Role[]
  language: 'FR' | 'EN'
  passwordChangeRequired: boolean
}

export interface TokenResponse {
  tokenType: string
  accessToken: string
  expiresIn: number
  refreshToken: string
  user: UserInfo
}

/** The server's error body (SPEC §9): a stable code and a message in the request's language. */
export interface ApiErrorBody {
  code: string
  message: string
  field?: string | null
  details?: ApiErrorBody[]
}

export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly field: string | null
  readonly details: ApiErrorBody[]

  constructor(status: number, body: ApiErrorBody) {
    super(body.message)
    this.name = 'ApiError'
    this.status = status
    this.code = body.code
    this.field = body.field ?? null
    this.details = body.details ?? []
  }

  /** The message for one field, from the error itself or its details. */
  messageFor(field: string): string | undefined {
    if (this.field === field) return this.message
    return this.details.find((d) => d.field === field)?.message
  }
}

/** No answer from the server (offline, backend down): the UI shows its own message. */
export const NETWORK_ERROR = 'network'

/** An answer that is not the API's JSON (e.g. a proxy error page). */
export const SERVER_ERROR = 'server'

export const REFRESH_TOKEN_KEY = 'schoollink.refreshToken'

export interface RequestOptions {
  method?: string
  body?: unknown
  /** false for the sign-in endpoints: no token, and a 401 is an answer, not an expired session. */
  auth?: boolean
}

export interface ApiClientOptions {
  baseUrl?: string
  fetch?: typeof fetch
  storage?: Storage
  /** The UI language, sent as Accept-Language for the messages of signed-out requests. */
  language: () => string
}

type SessionListener = (user: UserInfo | null) => void

export class ApiClient {
  private readonly baseUrl: string
  private readonly fetchFn: typeof fetch
  private readonly storage: Storage
  private readonly language: () => string
  private accessToken: string | null = null
  private refreshing: Promise<boolean> | null = null
  private readonly listeners = new Set<SessionListener>()

  constructor(options: ApiClientOptions) {
    this.baseUrl = options.baseUrl ?? '/api/v1'
    this.fetchFn = options.fetch ?? ((input, init) => fetch(input, init))
    this.storage = options.storage ?? sessionStorage
    this.language = options.language
  }

  /** Called with the user after each sign-in or refresh, and with null when the session ends. */
  onSession(listener: SessionListener): () => void {
    this.listeners.add(listener)
    return () => this.listeners.delete(listener)
  }

  startSession(tokens: TokenResponse): void {
    this.accessToken = tokens.accessToken
    this.writeRefreshToken(tokens.refreshToken)
    this.listeners.forEach((l) => l(tokens.user))
  }

  endSession(): void {
    this.accessToken = null
    this.writeRefreshToken(null)
    this.listeners.forEach((l) => l(null))
  }

  /** Signs out here and on the server (the refresh token stops working). */
  async signOut(): Promise<void> {
    const refreshToken = this.readRefreshToken()
    this.endSession()
    if (refreshToken !== null) {
      // best effort: offline, the token still expires on its own
      await this.send('/auth/logout', { method: 'POST', body: { refreshToken } }, null).catch(() => undefined)
    }
  }

  hasSession(): boolean {
    return this.accessToken !== null || this.readRefreshToken() !== null
  }

  async request<T>(path: string, options: RequestOptions = {}): Promise<T> {
    const auth = options.auth ?? true
    const sentWith = this.accessToken
    let response = await this.send(path, options, auth ? sentWith : null)
    if (response.status === 401 && auth) {
      // Another request may have refreshed meanwhile: then only retry.
      const renewed = this.accessToken !== null && this.accessToken !== sentWith
      if (renewed || (await this.refresh())) {
        response = await this.send(path, options, this.accessToken)
      }
    }
    if (!response.ok) {
      throw await toApiError(response)
    }
    return (response.status === 204 ? undefined : await response.json()) as T
  }

  /** Renews the token pair with the refresh token; ends the session if it is refused. */
  refresh(): Promise<boolean> {
    this.refreshing ??= this.doRefresh().finally(() => {
      this.refreshing = null
    })
    return this.refreshing
  }

  private async doRefresh(): Promise<boolean> {
    const refreshToken = this.readRefreshToken()
    if (refreshToken === null) {
      this.endSession()
      return false
    }
    // A network error rejects here and keeps the refresh token for the next attempt.
    const response = await this.send('/auth/refresh', { method: 'POST', body: { refreshToken } }, null)
    if (!response.ok) {
      this.endSession()
      return false
    }
    this.startSession((await response.json()) as TokenResponse)
    return true
  }

  private async send(path: string, options: RequestOptions, token: string | null): Promise<Response> {
    const headers: Record<string, string> = { Accept: 'application/json', 'Accept-Language': this.language() }
    if (options.body !== undefined) headers['Content-Type'] = 'application/json'
    if (token !== null) headers.Authorization = `Bearer ${token}`
    try {
      return await this.fetchFn(this.baseUrl + path, {
        method: options.method ?? 'GET',
        headers,
        body: options.body === undefined ? undefined : JSON.stringify(options.body),
      })
    } catch {
      throw new ApiError(0, { code: NETWORK_ERROR, message: '' })
    }
  }

  private readRefreshToken(): string | null {
    try {
      return this.storage.getItem(REFRESH_TOKEN_KEY)
    } catch {
      return null
    }
  }

  private writeRefreshToken(token: string | null): void {
    try {
      if (token === null) this.storage.removeItem(REFRESH_TOKEN_KEY)
      else this.storage.setItem(REFRESH_TOKEN_KEY, token)
    } catch {
      // storage blocked: the session then lasts until the page is reloaded
    }
  }
}

async function toApiError(response: Response): Promise<ApiError> {
  try {
    const body = (await response.json()) as ApiErrorBody
    if (typeof body?.code === 'string') return new ApiError(response.status, body)
  } catch {
    // not JSON
  }
  return new ApiError(response.status, { code: SERVER_ERROR, message: '' })
}
