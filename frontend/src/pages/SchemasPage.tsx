import { useEffect, useMemo, useRef, useState } from 'react';
import {
  AlertTriangle,
  Boxes,
  CheckCircle2,
  Download,
  Factory,
  FileCode2,
  FolderCog,
  Library,
  Plus,
  Search,
  Sparkles,
  Upload,
} from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { useSearchParams } from 'react-router-dom';
import { endpoints } from '../api/endpoints';
import { useDtoSchemas } from '../api/queries';
import type { DtoClassSchema, DtoParseResult, DtoRegistryView } from '../api/types';
import { ConfirmDialog } from '../components/ui/ConfirmDialog';
import { ErrorMessage } from '../components/ui/ErrorMessage';
import { Panel } from '../components/ui/Panel';
import { CenteredSpinner } from '../components/ui/Spinner';
import { useToast } from '../components/ui/Toast';
import { downloadJson, factoryRows, toForm, type SchemaForm } from '../features/schemas/dtoSchemaModel';
import { ImportSchemasModal } from '../features/schemas/ImportSchemasModal';
import { JavaPasteModal, type PasteMode } from '../features/schemas/JavaPasteModal';
import { SchemaCard } from '../features/schemas/SchemaCard';
import { SchemaEditorModal } from '../features/schemas/SchemaEditorModal';
import { schemaKey, useInvalidateSchemaData, type DtoTarget } from '../features/schemas/schemaLinks';

interface EditorState {
  initial: SchemaForm;
  original?: { factoryId: number; classId: number };
  warnings: string[];
  /** Class id the opened target expects (offered as a one-click choice, never filled in silently). */
  suggestedClassId?: number;
}

interface PasteState {
  mode: PasteMode;
  target?: DtoTarget;
  /** New on every open, so the dialog always starts with a clean state. */
  session: number;
}

let pasteSessions = 0;
const pasteState = (mode: PasteMode, target?: DtoTarget): PasteState => ({ mode, target, session: ++pasteSessions });
const sameSchema = (schema: DtoClassSchema, factoryId: number | undefined, classId: number | undefined) =>
  Number(schema.factoryId) === Number(factoryId) && Number(schema.classId) === Number(classId);

const intParam = (value: string | null) => (value !== null && /^-?\d+$/.test(value) ? Number(value) : undefined);

/**
 * DTO Schema Studio: registered IdentifiedDataSerializable schemas, the factory mapping, Smart
 * Java Paste, the visual schema editor and import / export of `dto-schemas.json`.
 *
 * URL parameters: `?configure=1&factoryId=1&classId=3[&className=X]` starts a schema for a class
 * found without one, `?edit=1/3` opens an existing schema.
 */
export function SchemasPage() {
  const { t } = useTranslation();
  const toast = useToast();
  const invalidate = useInvalidateSchemaData();
  const registry = useDtoSchemas();
  const [searchParams, setSearchParams] = useSearchParams();
  const importInput = useRef<HTMLInputElement>(null);
  const [filter, setFilter] = useState('');
  const [editor, setEditor] = useState<EditorState | null>(null);
  const [paste, setPaste] = useState<PasteState | null>(null);
  const [toDelete, setToDelete] = useState<DtoClassSchema | null>(null);
  const [importFile, setImportFile] = useState<File | null>(null);
  const [highlight, setHighlight] = useState<string | null>(null);

  const data = registry.data;

  const blankForm = (target?: DtoTarget): SchemaForm => ({
    factoryId: String(target?.factoryId ?? data?.factoryId ?? 1),
    classId: target ? String(target.classId) : '',
    className: target?.className ?? '',
    fields: [],
  });

  const openEditor = (schema: DtoClassSchema) =>
    setEditor({ initial: toForm(schema), original: { factoryId: schema.factoryId, classId: schema.classId }, warnings: [] });

  // deep links from the map browser / inspector
  useEffect(() => {
    if (!data) {
      return;
    }
    if (searchParams.get('configure') === '1') {
      const factoryId = intParam(searchParams.get('factoryId'));
      const classId = intParam(searchParams.get('classId'));
      if (factoryId !== undefined && classId !== undefined) {
        const existing = data.schemas.find((schema) => sameSchema(schema, factoryId, classId));
        if (existing) {
          // the class got a schema in the meantime: show it instead of starting a second one
          toast.success(t('schemas.alreadyRegistered', { name: existing.className, classId: existing.classId }));
          setHighlight(schemaKey(existing.factoryId, existing.classId));
          openEditor(existing);
        } else {
          const knownName =
            Number(factoryId) === Number(data.factoryId)
              ? Object.entries(data.factoryClasses).find(([, id]) => Number(id) === Number(classId))?.[0]
              : undefined;
          setPaste(pasteState('dto', { factoryId, classId, className: searchParams.get('className') ?? knownName }));
        }
      }
      setSearchParams({}, { replace: true });
    } else if (searchParams.get('edit')) {
      const [factoryId, classId] = (searchParams.get('edit') ?? '').split('/').map(intParam);
      const existing = data.schemas.find((schema) => sameSchema(schema, factoryId, classId));
      if (existing) {
        setHighlight(schemaKey(existing.factoryId, existing.classId));
        openEditor(existing);
      }
      setSearchParams({}, { replace: true });
    }
  }, [data, searchParams]);

  const schemas = useMemo(() => {
    const needle = filter.trim().toLowerCase();
    return (data?.schemas ?? [])
      .filter(
        (schema) =>
          !needle ||
          schema.className.toLowerCase().includes(needle) ||
          String(schema.classId) === needle ||
          schema.fields.some((field) => field.fieldName.toLowerCase().includes(needle)),
      )
      .sort((a, b) => a.factoryId - b.factoryId || a.classId - b.classId);
  }, [data, filter]);

  const analyzed = (result: DtoParseResult, target?: DtoTarget) => {
    const parsedClassId = result.schema.classId;
    const resolved = parsedClassId !== null && parsedClassId !== undefined;
    const form = toForm({
      ...result.schema,
      factoryId: result.schema.factoryId ?? target?.factoryId ?? data?.factoryId ?? 1,
      classId: resolved ? parsedClassId : 0,
      fields: result.schema.fields ?? [],
    });
    if (!resolved) {
      // never a default id (0, 1 or the id of another class): the user picks it explicitly
      form.classId = '';
    }
    const warnings = [...result.warnings];
    if (target && resolved && Number(parsedClassId) !== Number(target.classId)) {
      warnings.unshift(t('schemas.editor.targetMismatch', { parsed: parsedClassId, expected: target.classId }));
    }
    setPaste(null);
    setEditor({ initial: form, warnings, suggestedClassId: resolved ? undefined : target?.classId });
  };

  const exportAll = async () => {
    try {
      downloadJson('dto-schemas.json', await endpoints.schemas.exportFile());
    } catch {
      toast.error(t('schemas.exportFailed'));
    }
  };

  const exportOne = (schema: DtoClassSchema, view: DtoRegistryView) => {
    const projectFactory = Number(schema.factoryId) === Number(view.factoryId);
    const constants = projectFactory
      ? Object.fromEntries(Object.entries(view.factoryConstants).filter(([, id]) => Number(id) === Number(schema.classId)))
      : {};
    downloadJson(`${schema.className}.dto-schema.json`, {
      formatVersion: 1,
      factoryId: view.factoryId,
      factoryConstants: constants,
      factoryClasses: projectFactory ? { [schema.className]: schema.classId } : {},
      schemas: [schema],
    });
  };

  const remove = async (schema: DtoClassSchema) => {
    await endpoints.schemas.remove(schema.factoryId, schema.classId);
    await invalidate();
    toast.success(t('schemas.deleted', { name: schema.className }));
  };

  if (registry.isPending) {
    return <CenteredSpinner />;
  }
  if (registry.isError || !data) {
    return (
      <div className="p-6">
        <ErrorMessage error={registry.error} onRetry={() => void registry.refetch()} />
      </div>
    );
  }

  return (
    <div className="flex h-full flex-col">
      <header className="flex flex-wrap items-center gap-3 border-b border-gk-border bg-gk-panel px-6 py-4">
        <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-gk-cyan/10 text-gk-cyan">
          <FileCode2 className="h-5 w-5" aria-hidden />
        </span>
        <div className="min-w-0">
          <h1 className="text-lg font-semibold text-gk-text">{t('schemas.title')}</h1>
          <p className="text-xs text-gk-muted">{t('schemas.subtitle')}</p>
        </div>
        <span className="gk-badge bg-gk-purple/20 py-1 text-gk-purple">{t('schemas.count', { count: data.schemas.length })}</span>
        <div className="ml-auto flex flex-wrap items-center gap-2">
          <button type="button" className="gk-btn-primary" onClick={() => setPaste(pasteState('dto'))}>
            <Sparkles className="h-4 w-4" aria-hidden />
            {t('schemas.smartPaste')}
          </button>
          <button type="button" className="gk-btn-secondary" onClick={() => setEditor({ initial: blankForm(), warnings: [] })}>
            <Plus className="h-4 w-4" aria-hidden />
            {t('schemas.newSchema')}
          </button>
          <button type="button" className="gk-btn-secondary" onClick={() => void exportAll()}>
            <Download className="h-4 w-4" aria-hidden />
            {t('schemas.exportJson')}
          </button>
          <button type="button" className="gk-btn-secondary" onClick={() => importInput.current?.click()}>
            <Upload className="h-4 w-4" aria-hidden />
            {t('schemas.importJson')}
          </button>
          <input
            ref={importInput}
            type="file"
            accept=".json,application/json"
            className="hidden"
            onChange={(event) => {
              setImportFile(event.target.files?.[0] ?? null);
              event.target.value = '';
            }}
          />
        </div>
      </header>

      <div className="min-h-0 flex-1 overflow-y-auto">
        <div className="grid gap-5 p-6 xl:grid-cols-[340px_minmax(0,1fr)]">
          <aside className="space-y-5">
            <FactoryPanel
              registry={data}
              onParseFactory={() => setPaste(pasteState('factory'))}
              onConfigure={(target) => setPaste(pasteState('dto', target))}
              onEdit={openEditor}
            />
            <StoragePanel registry={data} />
          </aside>

          <section className="min-w-0 space-y-4">
            {data.pendingFactoryIds.length > 0 && (
              <div className="flex items-start gap-2 rounded-lg border border-gk-warning/40 bg-gk-warning/5 p-3 text-xs">
                <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-gk-warning" aria-hidden />
                <span className="text-gk-text">{t('schemas.pendingRestart', { ids: data.pendingFactoryIds.join(', ') })}</span>
              </div>
            )}

            {data.schemas.length === 0 ? (
              <EmptyState onPasteFactory={() => setPaste(pasteState('factory'))} onPasteDto={() => setPaste(pasteState('dto'))} />
            ) : (
              <>
                <label className="relative block max-w-md">
                  <Search className="pointer-events-none absolute left-2.5 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-gk-subtle" aria-hidden />
                  <input
                    type="search"
                    value={filter}
                    onChange={(event) => setFilter(event.target.value)}
                    placeholder={t('schemas.filterPlaceholder')}
                    aria-label={t('schemas.filterPlaceholder')}
                    className="gk-input py-1.5 pl-8"
                  />
                </label>
                {schemas.length === 0 ? (
                  <p className="text-gk-muted">{t('schemas.noMatches', { filter })}</p>
                ) : (
                  <div className="grid gap-4 lg:grid-cols-2 2xl:grid-cols-3">
                    {schemas.map((schema) => (
                      <SchemaCard
                        key={schemaKey(schema.factoryId, schema.classId)}
                        schema={schema}
                        registry={data}
                        highlighted={highlight === schemaKey(schema.factoryId, schema.classId)}
                        onEdit={() => openEditor(schema)}
                        onDelete={() => setToDelete(schema)}
                        onExport={() => exportOne(schema, data)}
                      />
                    ))}
                  </div>
                )}
              </>
            )}
          </section>
        </div>
      </div>

      {paste && (
        <JavaPasteModal
          key={paste.session}
          open
          initialMode={paste.mode}
          target={paste.target}
          onClose={() => setPaste(null)}
          onAnalyzed={(result) => analyzed(result, paste.target)}
          onManual={() => {
            const target = paste.target;
            setPaste(null);
            setEditor({ initial: blankForm(target), warnings: [] });
          }}
        />
      )}
      {editor && (
        <SchemaEditorModal
          open
          initial={editor.initial}
          original={editor.original}
          warnings={editor.warnings}
          suggestedClassId={editor.suggestedClassId}
          registry={data}
          onClose={() => setEditor(null)}
        />
      )}
      <ImportSchemasModal file={importFile} onClose={() => setImportFile(null)} />
      <ConfirmDialog
        open={toDelete !== null}
        title={t('schemas.deleteTitle')}
        message={toDelete ? t('schemas.deleteConfirm', { name: toDelete.className, factoryId: toDelete.factoryId, classId: toDelete.classId }) : ''}
        confirmLabel={t('common.delete')}
        danger
        onConfirm={() => (toDelete ? remove(toDelete) : Promise.resolve())}
        onClose={() => setToDelete(null)}
      />
    </div>
  );
}

/** FACTORY_ID and every class id of the parsed factory, with its schema status. */
function FactoryPanel({
  registry,
  onParseFactory,
  onConfigure,
  onEdit,
}: {
  registry: DtoRegistryView;
  onParseFactory: () => void;
  onConfigure: (target: DtoTarget) => void;
  onEdit: (schema: DtoClassSchema) => void;
}) {
  const { t } = useTranslation();
  const rows = factoryRows(registry);
  const registered = rows.filter((item) => item.schema).length;
  return (
    <Panel
      title={t('schemas.factory.title')}
      icon={Factory}
      actions={
        <button type="button" className="gk-btn-ghost px-2 py-1 text-xs" onClick={onParseFactory}>
          <Sparkles className="h-3.5 w-3.5" aria-hidden />
          {t('schemas.factory.parse')}
        </button>
      }
      bodyClassName="p-0"
    >
      <div className="flex items-center gap-2 border-b border-gk-border px-4 py-2.5 text-xs">
        <span className="text-gk-muted">FACTORY_ID</span>
        <span className="gk-mono text-gk-cyan">{registry.factoryId}</span>
        {rows.length > 0 && (
          <span className="text-gk-subtle">{t('schemas.factory.registeredCount', { registered, total: rows.length })}</span>
        )}
        {registry.activeFactoryIds.map(Number).includes(Number(registry.factoryId)) ? (
          <span className="gk-badge ml-auto gap-1 bg-gk-green/15 text-gk-green">
            <CheckCircle2 className="h-3 w-3" aria-hidden />
            {t('schemas.factory.active')}
          </span>
        ) : (
          <span className="gk-badge ml-auto bg-gk-warning/15 text-gk-warning">{t('schemas.card.pending')}</span>
        )}
      </div>
      {rows.length === 0 ? (
        <p className="px-4 py-4 text-xs text-gk-muted">{t('schemas.factory.empty')}</p>
      ) : (
        <ul className="max-h-[420px] divide-y divide-gk-border/60 overflow-y-auto text-xs">
          {rows.map((item) => {
            const name = item.className ?? item.schema?.className;
            return (
              <li key={item.classId} className="flex items-center gap-2 px-4 py-2">
                <span className="gk-mono w-7 shrink-0 text-right text-gk-subtle">{item.classId}</span>
                <div className="min-w-0 flex-1">
                  <span className={`gk-mono block truncate ${name ? 'text-gk-text' : 'text-gk-muted'}`} title={name}>
                    {name ?? t('schemas.factory.noClass')}
                  </span>
                  {item.constants.length > 0 && (
                    <span className="gk-mono block truncate text-[11px] text-gk-subtle">{item.constants.join(', ')}</span>
                  )}
                </div>
                {item.schema ? (
                  <button
                    type="button"
                    className="gk-badge gap-1 bg-gk-green/15 text-gk-green hover:bg-gk-green/25"
                    onClick={() => onEdit(item.schema!)}
                    title={t('schemas.card.edit')}
                  >
                    <CheckCircle2 className="h-3 w-3" aria-hidden />
                    {t('schemas.factory.registered')}
                    <span className="text-gk-green/80">· {t('schemas.card.edit')}</span>
                  </button>
                ) : (
                  <button
                    type="button"
                    className="gk-badge gap-1 bg-gk-warning/15 text-gk-warning hover:bg-gk-warning/25"
                    onClick={() => onConfigure({ factoryId: registry.factoryId, classId: item.classId, className: name })}
                  >
                    <Plus className="h-3 w-3" aria-hidden />
                    {t('schemas.factory.addSchema')}
                  </button>
                )}
              </li>
            );
          })}
        </ul>
      )}
    </Panel>
  );
}

/** Where the registry lives and which factories the running client serves. */
function StoragePanel({ registry }: { registry: DtoRegistryView }) {
  const { t } = useTranslation();
  const libraries = Object.entries(registry.libraryFactories ?? {});
  return (
    <Panel title={t('schemas.storage.title')} icon={FolderCog}>
      <dl className="space-y-3 text-xs">
        <div>
          <dt className="text-gk-muted">{t('schemas.storage.file')}</dt>
          <dd className="gk-mono mt-0.5 break-all text-gk-text">{registry.file}</dd>
        </div>
        <div>
          <dt className="text-gk-muted">{t('schemas.storage.activeFactories')}</dt>
          <dd className="mt-1 flex flex-wrap gap-1.5">
            {registry.activeFactoryIds.map((id) => (
              <span key={id} className="gk-badge gk-mono bg-gk-cyan/10 text-gk-cyan">
                {id}
              </span>
            ))}
          </dd>
        </div>
        <div>
          <dt className="flex items-center gap-1.5 text-gk-muted">
            <Library className="h-3.5 w-3.5" aria-hidden />
            {t('schemas.storage.libraryFactories')}
          </dt>
          <dd className="mt-1 space-y-1">
            {libraries.length === 0 ? (
              <span className="text-gk-subtle">{t('schemas.storage.noLibraryFactories')}</span>
            ) : (
              libraries.map(([id, className]) => (
                <span key={id} className="gk-mono block break-all text-gk-text">
                  <span className="text-gk-cyan">{id}</span> → {className}
                  {registry.activeFactoryIds.includes(Number(id)) && (
                    <span className="text-gk-subtle"> ({t('schemas.storage.fallback')})</span>
                  )}
                </span>
              ))
            )}
          </dd>
        </div>
        <p className="text-gk-subtle">{t('schemas.storage.shareHint')}</p>
      </dl>
    </Panel>
  );
}

function EmptyState({ onPasteFactory, onPasteDto }: { onPasteFactory: () => void; onPasteDto: () => void }) {
  const { t } = useTranslation();
  const steps = [
    { icon: Factory, title: t('schemas.empty.step1'), text: t('schemas.empty.step1Text'), action: onPasteFactory, label: t('schemas.factory.parse') },
    { icon: FileCode2, title: t('schemas.empty.step2'), text: t('schemas.empty.step2Text'), action: onPasteDto, label: t('schemas.paste.analyze') },
    { icon: Boxes, title: t('schemas.empty.step3'), text: t('schemas.empty.step3Text') },
  ];
  return (
    <div className="gk-panel p-6">
      <h2 className="text-base font-semibold text-gk-text">{t('schemas.empty.title')}</h2>
      <p className="mt-1 text-gk-muted">{t('schemas.empty.text')}</p>
      <ol className="mt-5 grid gap-4 md:grid-cols-3">
        {steps.map((step, index) => (
          <li key={step.title} className="gk-card flex flex-col gap-2 p-4">
            <span className="flex items-center gap-2 text-gk-cyan">
              <step.icon className="h-4 w-4" aria-hidden />
              <span className="gk-mono text-xs text-gk-subtle">{index + 1}</span>
              <span className="font-medium text-gk-text">{step.title}</span>
            </span>
            <p className="flex-1 text-xs text-gk-muted">{step.text}</p>
            {step.action && (
              <button type="button" className="gk-btn-secondary self-start px-2.5 py-1 text-xs" onClick={step.action}>
                {step.label}
              </button>
            )}
          </li>
        ))}
      </ol>
    </div>
  );
}
