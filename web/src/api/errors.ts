import type { TFunction } from 'i18next'
import { ApiError, NETWORK_ERROR, SERVER_ERROR } from './client'

/** The text to show for a failed request: the server's localized message when there is one. */
export function errorMessage(error: unknown, t: TFunction): string {
  if (error instanceof ApiError) {
    if (error.code === NETWORK_ERROR) return t('errors.network')
    if (error.code !== SERVER_ERROR && error.message) return error.message
  }
  return t('errors.server')
}
