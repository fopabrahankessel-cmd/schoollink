import '@testing-library/jest-dom/vitest'
import { cleanup, configure } from '@testing-library/react'
import { afterEach, beforeEach } from 'vitest'
import i18n from '../i18n'

// findBy*/waitFor give up after 1 s by default. A full parallel run renders the MUI screens
// several times slower than one file alone, so screens that pass alone failed at random in the
// whole suite. Waiting longer costs nothing when a test passes: the query returns as soon as
// the element appears.
configure({ asyncUtilTimeout: 5_000 })

// jsdom reports an English browser; tests start in French, the default language.
beforeEach(async () => {
  await i18n.changeLanguage('fr')
})

afterEach(() => {
  cleanup()
  localStorage.clear()
  sessionStorage.clear()
})
