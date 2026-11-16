/**
 * The list of supported languages. To add a language (e.g. Kannada):
 *   1. Add src/i18n/locales/kn.json (copy en.json and translate).
 *   2. Add one entry below: { code: 'kn', bcp47: 'kn-IN', englishName: 'Kannada', nativeName: 'ಕನ್ನಡ' }.
 *   3. Add the Noto Sans font for the script in index.html and src/index.css.
 * `bcp47` is the locale used for Android Text-to-Speech.
 */
export interface LocaleInfo {
  code: string;
  bcp47: string;
  englishName: string;
  nativeName: string;
}

export const LOCALES: readonly LocaleInfo[] = [
  { code: 'en', bcp47: 'en-IN', englishName: 'English', nativeName: 'English' },
  { code: 'hi', bcp47: 'hi-IN', englishName: 'Hindi', nativeName: 'हिन्दी' },
  { code: 'te', bcp47: 'te-IN', englishName: 'Telugu', nativeName: 'తెలుగు' },
  { code: 'ta', bcp47: 'ta-IN', englishName: 'Tamil', nativeName: 'தமிழ்' },
];

export const DEFAULT_LOCALE = 'en';

export function findLocale(code: string | null | undefined): LocaleInfo | undefined {
  return LOCALES.find((l) => l.code === code);
}
