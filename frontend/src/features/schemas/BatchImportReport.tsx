import { useState } from 'react';
import { AlertTriangle, CheckCircle2, ChevronDown, ChevronRight, Factory, FileX2, Link2, RefreshCw } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import type { BatchImportResult } from '../../api/types';
import { schemaKey } from './schemaLinks';
import { groupIgnored, ignoredPreview } from './javaFiles';

/**
 * Summary of a batch import of `.java` files: imported schemas (green), the factory found on the
 * way, problems in valid DTOs and the ignored files (gray, collapsed by default).
 */
export function BatchImportReport({ result }: { result: BatchImportResult }) {
  const { t } = useTranslation();
  const [showIgnored, setShowIgnored] = useState(false);
  const updated = new Set(result.updatedKeys);
  const problems = result.errors.filter((error) => !error.imported);
  const fieldWarnings = result.errors.filter((error) => error.imported);
  return (
    <div className="space-y-3 text-xs" aria-live="polite">
      <p className="text-gk-muted">{t('schemas.batch.processed', { count: result.totalFiles })}</p>

      {result.factoryUpdated && (
        <p className="flex items-center gap-1.5 rounded-md border border-gk-cyan/30 bg-gk-cyan/5 px-3 py-2 text-gk-text">
          <Factory className="h-3.5 w-3.5 shrink-0 text-gk-cyan" aria-hidden />
          {t('schemas.batch.factory', {
            file: result.factoryFile,
            factoryId: result.factoryId,
            count: result.factoryConstantCount,
          })}
        </p>
      )}

      <div className="rounded-lg border border-gk-green/40 bg-gk-green/5">
        <p className="flex items-center gap-2 px-3 py-2">
          <span className="gk-badge gap-1 bg-gk-green/15 py-1 font-semibold text-gk-green">
            <CheckCircle2 className="h-3.5 w-3.5" aria-hidden />
            {t('schemas.batch.imported', { count: result.importedCount })}
          </span>
          {result.linkedSchemas.length > 0 && (
            <span className="flex items-center gap-1 text-gk-muted">
              <Link2 className="h-3 w-3" aria-hidden />
              {t('schemas.batch.linked', { count: result.linkedSchemas.length })}
            </span>
          )}
        </p>
        {result.imported.length > 0 ? (
          <ul className="max-h-56 divide-y divide-gk-border/50 overflow-y-auto border-t border-gk-green/20">
            {result.imported.map((schema) => {
              const key = schemaKey(schema.factoryId, schema.classId);
              const nested = schema.fields.filter((field) => field.nestedClassName).length;
              return (
                <li key={key} className="flex items-center gap-2 px-3 py-1.5">
                  <span className="gk-mono w-14 shrink-0 text-right text-gk-subtle">#{schema.classId}</span>
                  <span className="gk-mono min-w-0 flex-1 truncate text-gk-text" title={schema.className}>
                    {schema.className}
                  </span>
                  <span className="text-gk-subtle">{t('schemas.card.fieldCount', { count: schema.fields.length })}</span>
                  {nested > 0 && <span className="text-gk-purple">{t('schemas.batch.nested', { count: nested })}</span>}
                  {updated.has(key) ? (
                    <span className="gk-badge gap-1 bg-gk-cyan/10 text-gk-cyan">
                      <RefreshCw className="h-3 w-3" aria-hidden />
                      {t('schemas.batch.updated')}
                    </span>
                  ) : (
                    <span className="gk-badge bg-gk-green/15 text-gk-green">{t('schemas.batch.added')}</span>
                  )}
                </li>
              );
            })}
          </ul>
        ) : (
          <p className="border-t border-gk-green/20 px-3 py-2 text-gk-muted">{t('schemas.batch.noneImported')}</p>
        )}
      </div>

      {(problems.length > 0 || fieldWarnings.length > 0) && (
        <div className="rounded-lg border border-gk-warning/40 bg-gk-warning/5 px-3 py-2">
          <p className="mb-1 flex items-center gap-1.5 font-medium text-gk-warning">
            <AlertTriangle className="h-3.5 w-3.5" aria-hidden />
            {t('schemas.batch.errors', { count: result.errors.length })}
          </p>
          <ul className="max-h-40 space-y-1 overflow-y-auto">
            {[...problems, ...fieldWarnings].map((error, index) => (
              <li key={`${error.fileName}-${index}`} className="break-words">
                <span className="gk-mono text-gk-text">{error.className ?? error.fileName}</span>
                <span className="text-gk-subtle"> · {error.imported ? t('schemas.batch.reviewField') : t('schemas.batch.notImported')}: </span>
                <span className="gk-mono text-gk-muted">{error.message}</span>
              </li>
            ))}
          </ul>
        </div>
      )}

      {result.ignoredCount > 0 && (
        <div className="rounded-lg border border-gk-border bg-gk-bg/60">
          <button
            type="button"
            className="flex w-full items-center gap-2 px-3 py-2 text-left text-gk-muted hover:text-gk-text"
            onClick={() => setShowIgnored((value) => !value)}
            aria-expanded={showIgnored}
          >
            {showIgnored ? <ChevronDown className="h-3.5 w-3.5" aria-hidden /> : <ChevronRight className="h-3.5 w-3.5" aria-hidden />}
            <FileX2 className="h-3.5 w-3.5" aria-hidden />
            <span className="font-medium">{t('schemas.batch.ignored', { count: result.ignoredCount })}</span>
            <span className="gk-mono min-w-0 flex-1 truncate text-gk-subtle">({ignoredPreview(result.ignored)})</span>
          </button>
          {showIgnored && (
            <div className="max-h-64 space-y-2 overflow-y-auto border-t border-gk-border px-3 py-2">
              {groupIgnored(result.ignored).map((group) => (
                <div key={group.reason}>
                  <p className="mb-1 text-gk-text">
                    {t(`schemas.batch.reasons.${reasonKey(group.reason)}`, { defaultValue: group.reason })}
                    <span className="text-gk-subtle"> · {group.files.length}</span>
                  </p>
                  <ul className="space-y-0.5 pl-3">
                    {group.files.map((file) => (
                      <li key={file.fileName} className="flex gap-2">
                        <span className="gk-mono min-w-0 flex-1 truncate text-gk-muted" title={file.fileName}>
                          {file.fileName}
                        </span>
                        {file.detail && <span className="gk-mono shrink-0 text-gk-subtle">{file.detail}</span>}
                      </li>
                    ))}
                  </ul>
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}

/** Translation key of a backend reason; unknown reasons are shown as sent. */
function reasonKey(reason: string): string {
  switch (reason) {
    case 'Not a Hazelcast IdentifiedDataSerializable DTO':
      return 'notDto';
    case 'Not registered in DataSerializableFactory':
      return 'notRegistered';
    case 'Not a .java file':
      return 'notJava';
    case 'Empty file':
      return 'empty';
    case 'Larger than 1 MB':
      return 'tooLarge';
    case 'Additional DataSerializableFactory':
      return 'extraFactory';
    default:
      return 'other';
  }
}
