import type { ApiClient } from './client'

/** UC-A7, D36: the letterhead printed on the DM's reports; the admin writes it. */
export const LETTERHEAD_PATH = '/school/letterhead'
const LOGO_PATH = `${LETTERHEAD_PATH}/logo`

export type LetterheadLayout = 'BILINGUAL' | 'FRENCH' | 'ENGLISH'
export type LetterheadSize = 'SMALL' | 'NORMAL' | 'LARGE'

export interface LetterheadLine {
  text: string
  bold: boolean
  size: LetterheadSize
}

/** {@code configured}: at least one line or a logo; otherwise the header is the school name alone. */
export interface Letterhead {
  schoolName: string
  configured: boolean
  layout: LetterheadLayout
  french: LetterheadLine[]
  english: LetterheadLine[]
  hasLogo: boolean
  /** Changes with each new logo: the key to fetch it again. */
  logoVersion: string | null
}

export interface LetterheadRequest {
  layout: LetterheadLayout
  french: LetterheadLine[]
  english: LetterheadLine[]
}

/** The server's limits (D36), checked here too so a mistake shows before sending. */
export const MAX_LINES = 12
export const MAX_TEXT = 100
export const MAX_LOGO_BYTES = 1_048_576
export const LOGO_TYPES = ['image/png', 'image/jpeg']

export const saveLetterhead = (client: ApiClient, body: LetterheadRequest) =>
  client.request<Letterhead>(LETTERHEAD_PATH, { method: 'PUT', body })

export function uploadLogo(client: ApiClient, file: File) {
  const form = new FormData()
  form.append('file', file)
  return client.request<Letterhead>(LOGO_PATH, { method: 'PUT', body: form })
}

export const removeLogo = (client: ApiClient) => client.request<Letterhead>(LOGO_PATH, { method: 'DELETE' })

/** The logo needs the token, so it is fetched as a blob and shown through an object URL. */
export const fetchLogo = (client: ApiClient) => client.blob(LOGO_PATH)
