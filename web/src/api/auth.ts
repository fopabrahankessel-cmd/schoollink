import type { ApiClient, TokenResponse, UserInfo } from './client'

export interface SchoolSummary {
  id: string
  name: string
}

/** POST /auth/login. D16: schoolId is needed only when the login exists in several schools. */
export function login(client: ApiClient, login: string, password: string, schoolId?: string) {
  return client.request<TokenResponse>('/auth/login', {
    method: 'POST',
    body: { login, password, ...(schoolId ? { schoolId } : {}) },
    auth: false,
  })
}

/** D16: the school picker. */
export function schools(client: ApiClient) {
  return client.request<SchoolSummary[]>('/auth/schools', { auth: false })
}

export function me(client: ApiClient) {
  return client.request<UserInfo>('/me')
}

/** D18: replaces a temporary password; returns a fresh token pair. */
export function changePassword(client: ApiClient, currentPassword: string, newPassword: string) {
  return client.request<TokenResponse>('/me/password', { method: 'POST', body: { currentPassword, newPassword } })
}
