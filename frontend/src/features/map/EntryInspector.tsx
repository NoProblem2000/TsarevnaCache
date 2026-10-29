import { useEffect, useState, type ReactNode } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Copy, Lock, Save, Trash2 } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { endpoints } from '../../api/endpoints';
import { queryKeys } from '../../api/queries';
import type { EntryDTO, InspectedValue, PutEntryRequest } from '../../api/types';
import { JsonEditor } from '../../components/editor/JsonEditor';
import { toEditorText } from '../../components/editor/jsonText';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { Drawer } from '../../components/ui/Drawer';
import { ErrorMessage, useErrorText } from '../../components/ui/ErrorMessage';
import { CenteredSpinner } from '../../components/ui/Spinner';
import { useToast } from '../../components/ui/Toast';
import { TypeBadge } from '../../components/ui/ValueBadges';
import { formatBytes, formatDateTime, formatDuration, formatNumber } from '../../lib/format';
import { UnresolvedPanel } from './UnresolvedPanel';
import { useInvalidateMapData } from './useInvalidateMapData';
import { editorTextToValue, initialEditorText, isJsonEdited, parseTtl } from './valueEditing';

interface EntryInspectorProps {
  mapName: string;
  /** Serialized key of the inspected entry; the drawer is closed when null. */
  keyRef: string | null;
  onClose: () => void;
}

/**
 * Entry Inspector drawer: key, editable value (JSON editor for JSON/class/Compact values),
 * EntryView metadata, Save (put with optional TTL) and Delete.
 */
export function EntryInspector({ mapName, keyRef, onClose }: EntryInspectorProps) {
  const { t } = useTranslation();
  const entry = useQuery({
    queryKey: queryKeys.mapEntry(mapName, keyRef ?? ''),
    queryFn: () => endpoints.maps.entry(mapName, { keyRef: keyRef ?? undefined }),
    enabled: keyRef !== null,
    refetchOnWindowFocus: false,
  });

  return (
    <Drawer
      open={keyRef !== null}
      onClose={onClose}
      title={t('inspector.title')}
      subtitle={
        entry.data && (
          <div className="flex min-w-0 items-center gap-2">
            <span className="gk-mono truncate text-gk-cyan" title={entry.data.key.preview}>
              {entry.data.key.preview ?? entry.data.key.unresolved?.missingClass}
            </span>
            <TypeBadge value={entry.data.key} />
          </div>
        )
      }
    >
      {entry.isPending && <CenteredSpinner />}
      {entry.isError && <ErrorMessage error={entry.error} onRetry={() => void entry.refetch()} />}
      {entry.data && <InspectorBody key={entry.data.keyRef} mapName={mapName} entry={entry.data} onDeleted={onClose} />}
    </Drawer>
  );
}

function InspectorBody({ mapName, entry, onDeleted }: { mapName: string; entry: EntryDTO; onDeleted: () => void }) {
  const { t, i18n } = useTranslation();
  const toast = useToast();
  const errorText = useErrorText();
  const queryClient = useQueryClient();
  const invalidate = useInvalidateMapData(mapName);
  const value = entry.value;
  const editable = value.deserialized && value.dataType !== undefined && value.warning === undefined;
  const jsonEdited = isJsonEdited(value.dataType);

  const [text, setText] = useState(() => initialEditorText(value));
  const [ttl, setTtl] = useState(() => (entry.metadata?.ttl !== undefined ? String(Math.round(entry.metadata.ttl / 1000)) : ''));
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<unknown>(null);
  const [confirmDelete, setConfirmDelete] = useState(false);

  // a saved entry replaces the cached one: show what is now stored
  useEffect(() => {
    setText(initialEditorText(entry.value));
    setTtl(entry.metadata?.ttl !== undefined ? String(Math.round(entry.metadata.ttl / 1000)) : '');
  }, [entry]);

  const parsed = editable && value.dataType ? editorTextToValue(text, value.dataType) : null;
  const ttlParsed = parseTtl(ttl);
  const dirty = editable && text !== initialEditorText(value);

  const save = async () => {
    if (!value.dataType || !parsed?.ok || !ttlParsed.ok) {
      return;
    }
    setSaving(true);
    setSaveError(null);
    try {
      const request: PutEntryRequest = {
        keyRef: entry.keyRef,
        value: parsed.value,
        valueType: value.dataType,
        valueClassName: value.dataType === 'CLASS' || value.dataType === 'IDENTIFIED_DTO' ? value.javaType : undefined,
        valueSchema: value.dataType === 'COMPACT' ? value.schema : undefined,
        valueFactoryId: value.dataType === 'IDENTIFIED_DTO' ? numberMeta(value, 'factoryId') : undefined,
        valueClassId: value.dataType === 'IDENTIFIED_DTO' ? numberMeta(value, 'classId') : undefined,
        ttlSeconds: ttlParsed.value,
      };
      const saved = await endpoints.maps.put(mapName, request);
      queryClient.setQueryData(queryKeys.mapEntry(mapName, entry.keyRef), saved);
      toast.success(t('browser.saved'));
      await invalidate();
    } catch (error) {
      setSaveError(error);
    } finally {
      setSaving(false);
    }
  };

  const remove = async () => {
    await endpoints.maps.remove(mapName, { keyRef: entry.keyRef });
    toast.success(t('browser.deleted'));
    queryClient.removeQueries({ queryKey: queryKeys.mapEntry(mapName, entry.keyRef) });
    await invalidate();
    onDeleted();
  };

  const copyKeyRef = async () => {
    try {
      await navigator.clipboard.writeText(entry.keyRef);
      toast.success(t('inspector.keyRefCopied'));
    } catch {
      toast.error(errorText(null));
    }
  };

  const metadata = entry.metadata ?? {};
  const n = (number: number | undefined) => formatNumber(number, i18n.language);

  return (
    <div className="space-y-6">
      <Section
        title={t('inspector.key')}
        actions={
          <button type="button" className="gk-btn-ghost px-2 py-1 text-xs" onClick={() => void copyKeyRef()}>
            <Copy className="h-3.5 w-3.5" aria-hidden />
            {t('inspector.copyKeyRef')}
          </button>
        }
      >
        <TypeInfo value={entry.key} />
        {entry.key.deserialized ? (
          <ReadOnlyValue value={entry.key} />
        ) : (
          entry.key.unresolved && <UnresolvedPanel unresolved={entry.key.unresolved} isKey />
        )}
      </Section>

      <Section title={t('inspector.value')}>
        <TypeInfo value={value} />
        {!value.deserialized && value.unresolved && <UnresolvedPanel unresolved={value.unresolved} />}
        {value.deserialized && !editable && (
          <>
            <div className="flex items-start gap-2 rounded-md border border-gk-border bg-gk-bg p-3 text-xs text-gk-muted">
              <Lock className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden />
              <span>
                <span className="font-medium text-gk-text">{t('inspector.readOnly')}.</span>{' '}
                {value.warning ? t('inspector.notEditableWarning') : t('inspector.readOnlyHint')}
              </span>
            </div>
            <ReadOnlyValue value={value} />
          </>
        )}
        {editable && (
          <div className="space-y-2">
            <p className="text-xs text-gk-muted">{t('inspector.editorHint')}</p>
            {jsonEdited ? (
              <JsonEditor value={text} onChange={setText} minHeight="220px" ariaLabel={t('inspector.value')} />
            ) : value.dataType === 'STRING' ? (
              <textarea
                className="gk-input gk-mono min-h-[120px] resize-y"
                value={text}
                onChange={(event) => setText(event.target.value)}
                aria-label={t('inspector.value')}
              />
            ) : (
              <input className="gk-input gk-mono" value={text} onChange={(event) => setText(event.target.value)} aria-label={t('inspector.value')} />
            )}
            {jsonEdited && parsed && !parsed.ok && (
              <p className="text-xs text-gk-danger">
                {t('inspector.invalidJson')}: <span className="gk-mono">{parsed.message}</span>
              </p>
            )}
          </div>
        )}
        {value.schema && <SchemaTable value={value} />}
      </Section>

      <Section title={t('inspector.metadata')}>
        <dl className="grid grid-cols-2 gap-x-6 gap-y-2 sm:grid-cols-3">
          <Meta label={t('inspector.cost')} value={metadata.cost !== undefined ? `${formatBytes(metadata.cost)} (${n(metadata.cost)})` : '—'} />
          <Meta label={t('inspector.hits')} value={n(metadata.hits)} />
          <Meta label={t('inspector.version')} value={n(metadata.version)} />
          <Meta label={t('inspector.creationTime')} value={formatDateTime(metadata.creationTime, i18n.language)} />
          <Meta label={t('inspector.lastUpdateTime')} value={formatDateTime(metadata.lastUpdateTime, i18n.language)} />
          <Meta label={t('inspector.lastAccessTime')} value={formatDateTime(metadata.lastAccessTime, i18n.language)} />
          <Meta label={t('inspector.lastStoredTime')} value={formatDateTime(metadata.lastStoredTime, i18n.language)} />
          <Meta
            label={t('inspector.expirationTime')}
            value={metadata.expirationTime ? formatDateTime(metadata.expirationTime, i18n.language) : t('browser.noExpiry')}
          />
          <Meta label={t('inspector.ttl')} value={metadata.ttl !== undefined ? formatDuration(metadata.ttl, t) : '—'} />
          <Meta label={t('inspector.maxIdle')} value={metadata.maxIdle !== undefined ? formatDuration(metadata.maxIdle, t) : '—'} />
          <Meta label={t('inspector.serializedSize')} value={value.serializedSize !== undefined ? formatBytes(value.serializedSize) : '—'} />
        </dl>
      </Section>

      {saveError !== null && <ErrorMessage error={saveError} />}

      <div className="sticky bottom-0 -mx-5 -mb-4 flex flex-wrap items-end gap-3 border-t border-gk-border bg-gk-panel px-5 py-3">
        <button type="button" className="gk-btn-danger" onClick={() => setConfirmDelete(true)}>
          <Trash2 className="h-4 w-4" aria-hidden />
          {t('inspector.deleteEntry')}
        </button>
        {editable && (
          <>
            <label className="ml-auto block w-48">
              <span className="gk-label">{t('inspector.ttlSeconds')}</span>
              <input
                className={`gk-input gk-mono py-1.5 ${ttlParsed.ok ? '' : 'border-gk-danger'}`}
                value={ttl}
                onChange={(event) => setTtl(event.target.value)}
                placeholder={t('putModal.ttlPlaceholder')}
                title={t('inspector.ttlKeep')}
                inputMode="numeric"
              />
            </label>
            <button
              type="button"
              className="gk-btn-primary"
              onClick={() => void save()}
              disabled={saving || !parsed?.ok || !ttlParsed.ok}
              title={!ttlParsed.ok ? t('inspector.ttlInvalid') : undefined}
            >
              <Save className="h-4 w-4" aria-hidden />
              {saving ? t('inspector.saving') : t('inspector.saveChanges')}
              {dirty && <span className="h-1.5 w-1.5 rounded-full bg-gk-bg" aria-hidden />}
            </button>
          </>
        )}
        {editable && <p className="w-full text-right text-[11px] text-gk-subtle">{ttlParsed.ok ? t('inspector.ttlKeep') : t('inspector.ttlInvalid')}</p>}
      </div>

      <ConfirmDialog
        open={confirmDelete}
        title={t('inspector.deleteEntry')}
        message={t('browser.deleteConfirm')}
        confirmLabel={t('common.delete')}
        danger
        onConfirm={remove}
        onClose={() => setConfirmDelete(false)}
      />
    </div>
  );
}

/** Numeric metadata of an inspected value (e.g. the factory/class id of an IDENTIFIED_DTO). */
function numberMeta(value: InspectedValue, key: string): number | undefined {
  const raw = value.metadata?.[key];
  return typeof raw === 'number' ? raw : undefined;
}

function Section({ title, actions, children }: { title: string; actions?: ReactNode; children: ReactNode }) {
  return (
    <section className="space-y-2">
      <div className="flex items-center gap-2">
        <h3 className="text-xs font-semibold uppercase tracking-wide text-gk-muted">{title}</h3>
        <div className="h-px flex-1 bg-gk-border" aria-hidden />
        {actions}
      </div>
      {children}
    </section>
  );
}

function TypeInfo({ value }: { value: InspectedValue }) {
  const { t } = useTranslation();
  return (
    <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs">
      <span className="text-gk-muted">
        {t('inspector.kind')}: <span className="text-gk-text">{t(`kinds.${value.kind}`)}</span>
      </span>
      {value.javaType && (
        <span className="min-w-0 text-gk-muted">
          {t('inspector.javaType')}: <span className="gk-mono break-all text-gk-purple">{value.javaType}</span>
        </span>
      )}
      {value.dataType && (
        <span className="text-gk-muted">
          {t('inspector.dataType')}: <span className="text-gk-text">{t(`dataTypes.${value.dataType}`)}</span>
        </span>
      )}
    </div>
  );
}

function ReadOnlyValue({ value }: { value: InspectedValue }) {
  if (typeof value.value === 'string' && value.kind !== 'JSON') {
    return <pre className="gk-mono max-h-64 overflow-auto whitespace-pre-wrap break-all rounded-md border border-gk-border bg-gk-bg p-3">{value.value}</pre>;
  }
  if (value.value === undefined || value.value === null || typeof value.value !== 'object') {
    return <pre className="gk-mono rounded-md border border-gk-border bg-gk-bg p-3">{value.preview ?? String(value.value)}</pre>;
  }
  return <JsonEditor value={toEditorText(value.value)} readOnly minHeight="60px" />;
}

function SchemaTable({ value }: { value: InspectedValue }) {
  const { t } = useTranslation();
  if (!value.schema) {
    return null;
  }
  return (
    <details className="rounded-md border border-gk-border bg-gk-bg">
      <summary className="cursor-pointer px-3 py-2 text-xs text-gk-muted hover:text-gk-text">
        {t('inspector.schema')} · <span className="gk-mono text-gk-purple">{value.schema.format} {value.schema.typeName}</span>
      </summary>
      <table className="w-full text-left text-xs">
        <thead className="text-gk-muted">
          <tr>
            <th className="px-3 py-1.5 font-medium">{t('inspector.schemaField')}</th>
            <th className="px-3 py-1.5 font-medium">{t('inspector.schemaKind')}</th>
          </tr>
        </thead>
        <tbody>
          {value.schema.fields.map((field) => (
            <tr key={field.name} className="border-t border-gk-border">
              <td className="gk-mono px-3 py-1.5 text-gk-cyan">{field.name}</td>
              <td className="gk-mono px-3 py-1.5 text-gk-purple">
                {field.kind}
                {field.schema && <span className="text-gk-muted"> ({field.schema.typeName})</span>}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </details>
  );
}

function Meta({ label, value }: { label: string; value: ReactNode }) {
  return (
    <div className="min-w-0">
      <dt className="text-[11px] uppercase tracking-wide text-gk-subtle">{label}</dt>
      <dd className="gk-mono truncate text-gk-text" title={typeof value === 'string' ? value : undefined}>
        {value}
      </dd>
    </div>
  );
}
