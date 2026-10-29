import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach, beforeEach } from 'vitest'
import i18n from '../i18n'

// jsdom reports an English browser; tests start in French, the default language.
beforeEach(async () => {
  await i18n.changeLanguage('fr')
})

afterEach(() => {
  cleanup()
  localStorage.clear()
  sessionStorage.clear()
})
