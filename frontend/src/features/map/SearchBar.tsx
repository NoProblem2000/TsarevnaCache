import { useState, type FormEvent } from 'react';
import { Info, Search, X } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { KEY_TYPES, type DataType } from '../../api/types';
import { Segmented } from '../../components/ui/Segmented';

export type SearchMode = 'all' | 'key' | 'predicate';

/** The search currently applied to the table. */
export type AppliedSearch =
  | { mode: 'all' }
  | { mode: 'key'; key: string; keyType: DataType; keyClassName?: string }
  | { mode: 'predicate'; predicate: string };

interface SearchBarProps {
  applied: AppliedSearch;
  onApply: (search: AppliedSearch) => void;
}

/**
 * Search panel of the data browser: all entries, exact key (with key type selector) or a
 * Hazelcast SQL predicate.
 */
export function SearchBar({ applied, onApply }: SearchBarProps) {
  const { t } = useTranslation();
  const [mode, setMode] = useState<SearchMode>(applied.mode);
  const [key, setKey] = useState(applied.mode === 'key' ? applied.key : '');
  const [keyType, setKeyType] = useState<DataType>(applied.mode === 'key' ? applied.keyType : 'STRING');
  const [keyClassName, setKeyClassName] = useState(applied.mode === 'key' ? applied.keyClassName ?? '' : '');
  const [predicate, setPredicate] = useState(applied.mode === 'predicate' ? applied.predicate : '');

  const changeMode = (next: SearchMode) => {
    setMode(next);
    if (next === 'all') {
      onApply({ mode: 'all' });
    }
  };

  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (mode === 'key' && key.length > 0) {
      onApply({ mode: 'key', key, keyType, keyClassName: keyType === 'CLASS' ? keyClassName.trim() : undefined });
    } else if (mode === 'predicate' && predicate.trim()) {
      onApply({ mode: 'predicate', predicate: predicate.trim() });
    }
  };

  const clear = () => {
    setKey('');
    setPredicate('');
    setMode('all');
    onApply({ mode: 'all' });
  };

  const canSubmit = (mode === 'key' && key.length > 0 && (keyType !== 'CLASS' || keyClassName.trim())) || (mode === 'predicate' && predicate.trim());

  return (
    <form onSubmit={submit} className="space-y-2">
      <div className="flex flex-wrap items-center gap-2">
        <Segmented
          ariaLabel={t('browser.searchMode.all')}
          value={mode}
          onChange={changeMode}
          options={[
            { value: 'all', label: t('browser.searchMode.all') },
            { value: 'key', label: t('browser.searchMode.key') },
            { value: 'predicate', label: t('browser.searchMode.predicate') },
          ]}
        />

        {mode === 'key' && (
          <>
            <select
              className="gk-input w-auto py-1.5 text-xs"
              value={keyType}
              onChange={(event) => setKeyType(event.target.value as DataType)}
              aria-label={t('putModal.keyType')}
            >
              {KEY_TYPES.map((type) => (
                <option key={type} value={type}>
                  {t(`dataTypes.${type}`)}
                </option>
              ))}
            </select>
            {keyType === 'CLASS' && (
              <input
                className="gk-input gk-mono w-56 py-1.5 text-xs"
                value={keyClassName}
                onChange={(event) => setKeyClassName(event.target.value)}
                placeholder={t('browser.keyClassPlaceholder')}
                aria-label={t('putModal.keyClassName')}
              />
            )}
            <input
              className="gk-input gk-mono min-w-[200px] flex-1 py-1.5 text-xs"
              value={key}
              onChange={(event) => setKey(event.target.value)}
              placeholder={t('browser.keyPlaceholder')}
              aria-label={t('browser.keyPlaceholder')}
              autoFocus
            />
          </>
        )}

        {mode === 'predicate' && (
          <input
            className="gk-input gk-mono min-w-[280px] flex-1 py-1.5 text-xs"
            value={predicate}
            onChange={(event) => setPredicate(event.target.value)}
            placeholder={t('browser.predicatePlaceholder')}
            aria-label={t('browser.searchMode.predicate')}
            autoFocus
          />
        )}

        {mode !== 'all' && (
          <>
            <button type="submit" className="gk-btn-primary py-1.5" disabled={!canSubmit}>
              <Search className="h-3.5 w-3.5" aria-hidden />
              {t('browser.search')}
            </button>
            <button type="button" className="gk-btn-ghost py-1.5" onClick={clear}>
              <X className="h-3.5 w-3.5" aria-hidden />
              {t('browser.clearSearch')}
            </button>
          </>
        )}
      </div>
      {mode === 'predicate' && (
        <p className="flex items-start gap-1.5 text-xs text-gk-muted">
          <Info className="mt-0.5 h-3 w-3 shrink-0" aria-hidden />
          {t('browser.predicateHint')}
        </p>
      )}
    </form>
  );
}
