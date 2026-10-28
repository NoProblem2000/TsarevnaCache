import { AlertTriangle } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import type { InspectedValue } from '../../api/types';

/** Purple badge naming the type of a key or value (the data type when editable, else its kind). */
export function TypeBadge({ value }: { value: InspectedValue }) {
  const { t } = useTranslation();
  if (!value.deserialized) {
    return <UnresolvedBadge />;
  }
  const label = value.dataType ? t(`dataTypes.${value.dataType}`) : t(`kinds.${value.kind}`);
  return (
    <span className="gk-badge max-w-full truncate bg-gk-purple/15 text-gk-purple" title={value.javaType}>
      {label}
    </span>
  );
}

/** Warning badge for values that could not be deserialized. */
export function UnresolvedBadge() {
  const { t } = useTranslation();
  return (
    <span className="gk-badge gap-1 bg-gk-warning/15 text-gk-warning">
      <AlertTriangle className="h-3 w-3" aria-hidden />
      {t('browser.unresolved')}
    </span>
  );
}

/** Single-line preview of an inspected value (monospace, truncated). */
export function ValuePreview({ value }: { value: InspectedValue }) {
  const { t } = useTranslation();
  if (!value.deserialized) {
    return (
      <span className="flex min-w-0 items-center gap-2">
        <UnresolvedBadge />
        {value.unresolved?.missingClass && (
          <span className="gk-mono truncate text-gk-muted" title={value.unresolved.missingClass}>
            {value.unresolved.missingClass}
          </span>
        )}
      </span>
    );
  }
  const text = value.preview ?? '';
  return (
    <span className="gk-mono block truncate" title={value.truncated ? t('browser.truncated') : text}>
      {text}
      {value.truncated && <span className="text-gk-subtle">…</span>}
    </span>
  );
}
