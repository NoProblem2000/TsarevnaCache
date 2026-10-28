import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import en from './locales/en.json';
import ru from './locales/ru.json';

/** Supported UI languages; English is the default. */
export const LANGUAGES = ['en', 'ru'] as const;
export type Language = (typeof LANGUAGES)[number];

const STORAGE_KEY = 'tsarevna.language';
const DEFAULT_LANGUAGE: Language = 'en';

function isLanguage(value: unknown): value is Language {
  return typeof value === 'string' && (LANGUAGES as readonly string[]).includes(value);
}

/** Language stored by the language switch, falling back to English. */
export function storedLanguage(): Language {
  try {
    const value = window.localStorage.getItem(STORAGE_KEY);
    return isLanguage(value) ? value : DEFAULT_LANGUAGE;
  } catch {
    return DEFAULT_LANGUAGE;
  }
}

/** Switches the UI language and remembers it in localStorage. */
export async function changeLanguage(language: Language): Promise<void> {
  try {
    window.localStorage.setItem(STORAGE_KEY, language);
  } catch {
    // storage may be unavailable (private mode); the choice then lasts for this tab only
  }
  document.documentElement.lang = language;
  await i18n.changeLanguage(language);
}

void i18n.use(initReactI18next).init({
  resources: {
    en: { translation: en },
    ru: { translation: ru },
  },
  lng: storedLanguage(),
  fallbackLng: DEFAULT_LANGUAGE,
  interpolation: { escapeValue: false },
  returnNull: false,
});

document.documentElement.lang = i18n.language;

export default i18n;
