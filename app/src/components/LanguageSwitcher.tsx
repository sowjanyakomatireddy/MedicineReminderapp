import { useTranslation } from 'react-i18next';
import { useId } from 'react';
import { LOCALES } from '../i18n/locales';

export function LanguageSwitcher() {
  const { t, i18n } = useTranslation();
  const id = useId();
  return (
    <div className="flex items-center gap-2">
      <label htmlFor={id} className="text-sm text-muted">
        {t('common.language')}
      </label>
      <select
        id={id}
        value={i18n.resolvedLanguage}
        onChange={(e) => void i18n.changeLanguage(e.target.value)}
        className="min-h-11 rounded-lg border border-muted/40 bg-white px-2"
      >
        {LOCALES.map((l) => (
          <option key={l.code} value={l.code} lang={l.code}>
            {l.nativeName}
          </option>
        ))}
      </select>
    </div>
  );
}
