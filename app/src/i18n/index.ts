import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import { DEFAULT_LOCALE, LOCALES, findLocale } from './locales';

// Every JSON file in ./locales is loaded automatically; locales.ts decides which ones are offered.
const files = import.meta.glob<{ default: Record<string, unknown> }>('./locales/*.json', { eager: true });

export const resources = Object.fromEntries(
  Object.entries(files).map(([path, mod]) => {
    const code = path.match(/\/(\w+)\.json$/)![1];
    return [code, { translation: mod.default }];
  }),
);

const STORAGE_KEY = 'mr.appLanguage';

function savedLanguage(): string {
  try {
    const saved = localStorage.getItem(STORAGE_KEY);
    if (findLocale(saved)) return saved!;
  } catch {
    /* storage blocked: fall through */
  }
  return DEFAULT_LOCALE;
}

i18n.use(initReactI18next).init({
  resources,
  lng: savedLanguage(),
  fallbackLng: DEFAULT_LOCALE,
  supportedLngs: LOCALES.map((l) => l.code),
  interpolation: { escapeValue: false },
});

function applyHtmlLang(lng: string) {
  if (typeof document !== 'undefined') document.documentElement.lang = lng;
}
applyHtmlLang(i18n.language);
i18n.on('languageChanged', (lng) => {
  applyHtmlLang(lng);
  try {
    localStorage.setItem(STORAGE_KEY, lng);
  } catch {
    /* ignore */
  }
});

export default i18n;
