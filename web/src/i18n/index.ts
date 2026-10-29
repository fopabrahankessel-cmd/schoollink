import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import en from './en.json'
import fr from './fr.json'

// NFR-12: every UI string exists in French and English (i18n.test.ts checks the keys match).
export const LANGUAGES = ['fr', 'en'] as const
export type Language = (typeof LANGUAGES)[number]

export const LANGUAGE_KEY = 'schoollink.language'

/** The language chosen on this device, else the browser's if supported, else French. */
export function initialLanguage(): Language {
  try {
    const stored = localStorage.getItem(LANGUAGE_KEY)
    if (stored === 'fr' || stored === 'en') return stored
  } catch {
    // storage blocked
  }
  return navigator.language?.toLowerCase().startsWith('en') ? 'en' : 'fr'
}

export function setLanguage(language: Language): void {
  try {
    localStorage.setItem(LANGUAGE_KEY, language)
  } catch {
    // storage blocked: the choice lasts until the page is reloaded
  }
  void i18n.changeLanguage(language)
}

i18n.on('languageChanged', (language) => {
  document.documentElement.lang = language
})

void i18n.use(initReactI18next).init({
  resources: { fr: { translation: fr }, en: { translation: en } },
  lng: initialLanguage(),
  fallbackLng: 'fr',
  supportedLngs: LANGUAGES,
  initAsync: false,
  interpolation: { escapeValue: false }, // React escapes
})

export default i18n
