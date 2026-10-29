import { AlertTriangle, FileCode2 } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import type { UnresolvedObjectDTO } from '../../api/types';
import { formatBytes } from '../../lib/format';
import { configureSchemaRoute, missingDtoTarget } from '../schemas/schemaLinks';

/** Explains why a key or value could not be deserialized and what to add to the classpath. */
export function UnresolvedPanel({ unresolved, isKey = false }: { unresolved: UnresolvedObjectDTO; isKey?: boolean }) {
  const { t } = useTranslation();
  const metadata = Object.entries(unresolved.metadata ?? {});
  const dtoTarget = missingDtoTarget(unresolved);
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
      {dtoTarget ? (
        <div className="flex flex-wrap items-center gap-3 rounded-md border border-gk-cyan/30 bg-gk-cyan/5 p-3">
          <p className="min-w-0 flex-1 text-xs text-gk-text">{t('inspector.dtoSchemaHint')}</p>
          <Link to={configureSchemaRoute(dtoTarget)} className="gk-btn-primary px-3 py-1.5 text-xs">
            <FileCode2 className="h-3.5 w-3.5" aria-hidden />
            {t('inspector.configureDtoSchema', { factoryId: dtoTarget.factoryId, classId: dtoTarget.classId })}
          </Link>
        </div>
      ) : (
        <p className="text-xs text-gk-muted">{t('inspector.unresolvedHint')}</p>
      )}
    </div>
  );
}
