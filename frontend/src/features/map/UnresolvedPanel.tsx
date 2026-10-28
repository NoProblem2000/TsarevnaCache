import { AlertTriangle } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import type { UnresolvedObjectDTO } from '../../api/types';
import { formatBytes } from '../../lib/format';

/** Explains why a key or value could not be deserialized and what to add to the classpath. */
export function UnresolvedPanel({ unresolved, isKey = false }: { unresolved: UnresolvedObjectDTO; isKey?: boolean }) {
  const { t } = useTranslation();
  const metadata = Object.entries(unresolved.metadata ?? {});
  return (
    <div className="space-y-3 rounded-lg border border-gk-warning/40 bg-gk-warning/5 p-4">
      <div className="flex items-start gap-2">
        <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-gk-warning" aria-hidden />
        <div>
          <p className="font-medium text-gk-text">{isKey ? t('inspector.unresolvedKeyTitle') : t('inspector.unresolvedTitle')}</p>
          <p className="mt-0.5 text-gk-muted">{t(`deserialization.${unresolved.errorCode}`)}</p>
        </div>
      </div>
      <dl className="grid grid-cols-[max-content_1fr] gap-x-4 gap-y-1.5 text-xs">
        {unresolved.missingClass && (
          <>
            <dt className="text-gk-muted">{t('inspector.missingClass')}</dt>
            <dd className="gk-mono break-all text-gk-warning">{unresolved.missingClass}</dd>
          </>
        )}
        <dt className="text-gk-muted">{t('inspector.serializer')}</dt>
        <dd className="gk-mono text-gk-text">{unresolved.serializer ?? '—'}</dd>
        <dt className="text-gk-muted">{t('inspector.typeId')}</dt>
        <dd className="gk-mono text-gk-text">{unresolved.typeId}</dd>
        {unresolved.serializedSize !== undefined && (
          <>
            <dt className="text-gk-muted">{t('inspector.serializedSize')}</dt>
            <dd className="gk-mono text-gk-text">{formatBytes(unresolved.serializedSize)}</dd>
          </>
        )}
        {unresolved.entryCost !== undefined && (
          <>
            <dt className="text-gk-muted">{t('inspector.entryCost')}</dt>
            <dd className="gk-mono text-gk-text">{formatBytes(unresolved.entryCost)}</dd>
          </>
        )}
        {metadata.length > 0 && (
          <>
            <dt className="text-gk-muted">{t('inspector.headerMetadata')}</dt>
            <dd className="gk-mono break-all text-gk-text">
              {metadata.map(([key, value]) => `${key}=${String(value)}`).join(', ')}
            </dd>
          </>
        )}
        {unresolved.reason && (
          <>
            <dt className="text-gk-muted">{t('inspector.reason')}</dt>
            <dd className="gk-mono break-words text-gk-muted">{unresolved.reason}</dd>
          </>
        )}
      </dl>
      <p className="text-xs text-gk-muted">{t('inspector.unresolvedHint')}</p>
    </div>
  );
}
