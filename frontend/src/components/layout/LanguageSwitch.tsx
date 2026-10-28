import { useTranslation } from 'react-i18next';
import { changeLanguage, LANGUAGES, type Language } from '../../i18n';

/** GitKraken-style segmented control switching between EN and RU. */
export function LanguageSwitch() {
  const { t, i18n } = useTranslation();
  const current = (i18n.resolvedLanguage ?? i18n.language) as Language;
  return (
    <div role="radiogroup" aria-label={t('topBar.language')} className="flex rounded-md border border-gk-border bg-gk-bg p-0.5">
      {LANGUAGES.map((language) => {
        const active = language === current;
        return (
          <button
            key={language}
            type="button"
            role="radio"
            aria-checked={active}
            onClick={() => void changeLanguage(language)}
            className={`rounded px-2.5 py-1 font-mono text-xs font-semibold uppercase transition-colors ${
              active ? 'bg-gk-cyan text-gk-bg shadow-glow' : 'text-gk-muted hover:text-gk-text'
            }`}
          >
            {language}
          </button>
        );
      })}
    </div>
  );
}
