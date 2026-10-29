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
import { downloadJson, toForm, type SchemaForm } from '../features/schemas/dtoSchemaModel';
import { ImportSchemasModal } from '../features/schemas/ImportSchemasModal';
import { JavaPasteModal, type PasteMode } from '../features/schemas/JavaPasteModal';
import { SchemaCard } from '../features/schemas/SchemaCard';
import { SchemaEditorModal } from '../features/schemas/SchemaEditorModal';
import { useInvalidateSchemaData, type DtoTarget } from '../features/schemas/schemaLinks';

interface EditorState {
  initial: SchemaForm;
  original?: { factoryId: number; classId: number };
  warnings: string[];
}

interface PasteState {
  mode: PasteMode;
  target?: DtoTarget;
}

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
        const existing = data.schemas.find((schema) => schema.factoryId === factoryId && schema.classId === classId);
        if (existing) {
          openEditor(existing);
        } else {
          const knownName = factoryId === data.factoryId
            ? Object.entries(data.factoryClasses).find(([, id]) => id === classId)?.[0]
            : undefined;
          setPaste({ mode: 'dto', target: { factoryId, classId, className: searchParams.get('className') ?? knownName } });
        }
      }
      setSearchParams({}, { replace: true });
    } else if (searchParams.get('edit')) {
      const [factoryId, classId] = (searchParams.get('edit') ?? '').split('/').map(intParam);
      const existing = data.schemas.find((schema) => schema.factoryId === factoryId && schema.classId === classId);
      if (existing) {
        setHighlight(`${existing.factoryId}/${existing.classId}`);
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
    const form = toForm({
      ...result.schema,
      factoryId: result.schema.factoryId ?? target?.factoryId ?? data?.factoryId ?? 1,
      classId: result.schema.classId ?? target?.classId ?? 0,
      fields: result.schema.fields ?? [],
    });
    if (result.schema.classId === null || result.schema.classId === undefined) {
      form.classId = target ? String(target.classId) : '';
    }
    const warnings = [...result.warnings];
    if (target && result.schema.classId !== null && result.schema.classId !== undefined && result.schema.classId !== target.classId) {
      warnings.unshift(t('schemas.editor.targetMismatch', { parsed: result.schema.classId, expected: target.classId }));
    }
    setPaste(null);
    setEditor({ initial: form, warnings });
  };

  const exportAll = async () => {
    try {
      downloadJson('dto-schemas.json', await endpoints.schemas.exportFile());
    } catch {
      toast.error(t('schemas.exportFailed'));
    }
  };

  const exportOne = (schema: DtoClassSchema, view: DtoRegistryView) => {
    const projectFactory = schema.factoryId === view.factoryId;
    downloadJson(`${schema.className}.dto-schema.json`, {
      formatVersion: 1,
      factoryId: view.factoryId,
      factoryConstants: {},
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
          <button type="button" className="gk-btn-primary" onClick={() => setPaste({ mode: 'dto' })}>
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
              onParseFactory={() => setPaste({ mode: 'factory' })}
              onConfigure={(target) => setPaste({ mode: 'dto', target })}
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
              <EmptyState onPasteFactory={() => setPaste({ mode: 'factory' })} onPasteDto={() => setPaste({ mode: 'dto' })} />
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
                        key={`${schema.factoryId}/${schema.classId}`}
                        schema={schema}
                        registry={data}
                        highlighted={highlight === `${schema.factoryId}/${schema.classId}`}
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
  const classes = Object.entries(registry.factoryClasses).sort((a, b) => a[1] - b[1]);
  const constantsWithoutClass = Object.entries(registry.factoryConstants)
    .filter(([, id]) => !classes.some(([, classId]) => classId === id))
    .sort((a, b) => a[1] - b[1]);
  const constantOf = (id: number) => Object.entries(registry.factoryConstants).find(([, value]) => value === id)?.[0];
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
        {registry.activeFactoryIds.includes(registry.factoryId) ? (
          <span className="gk-badge ml-auto gap-1 bg-gk-green/15 text-gk-green">
            <CheckCircle2 className="h-3 w-3" aria-hidden />
            {t('schemas.factory.active')}
          </span>
        ) : (
          <span className="gk-badge ml-auto bg-gk-warning/15 text-gk-warning">{t('schemas.card.pending')}</span>
        )}
      </div>
      {classes.length === 0 && constantsWithoutClass.length === 0 ? (
        <p className="px-4 py-4 text-xs text-gk-muted">{t('schemas.factory.empty')}</p>
      ) : (
        <ul className="max-h-[420px] divide-y divide-gk-border/60 overflow-y-auto text-xs">
          {classes.map(([className, classId]) => {
            const schema = registry.schemas.find((item) => item.factoryId === registry.factoryId && item.classId === classId);
            return (
              <li key={className} className="flex items-center gap-2 px-4 py-2">
                <span className="gk-mono w-7 shrink-0 text-right text-gk-subtle">{classId}</span>
                <div className="min-w-0 flex-1">
                  <span className="gk-mono block truncate text-gk-text" title={className}>
                    {className}
                  </span>
                  {constantOf(classId) && <span className="gk-mono block truncate text-[11px] text-gk-subtle">{constantOf(classId)}</span>}
                </div>
                {schema ? (
                  <button type="button" className="gk-badge gap-1 bg-gk-green/15 text-gk-green hover:bg-gk-green/25" onClick={() => onEdit(schema)}>
                    <CheckCircle2 className="h-3 w-3" aria-hidden />
                    {t('schemas.factory.hasSchema')}
                  </button>
                ) : (
                  <button
                    type="button"
                    className="gk-badge gap-1 bg-gk-warning/15 text-gk-warning hover:bg-gk-warning/25"
                    onClick={() => onConfigure({ factoryId: registry.factoryId, classId, className })}
                  >
                    <Plus className="h-3 w-3" aria-hidden />
                    {t('schemas.factory.addSchema')}
                  </button>
                )}
              </li>
            );
          })}
          {constantsWithoutClass.map(([name, id]) => (
            <li key={name} className="flex items-center gap-2 px-4 py-2 text-gk-muted">
              <span className="gk-mono w-7 shrink-0 text-right text-gk-subtle">{id}</span>
              <span className="gk-mono min-w-0 flex-1 truncate">{name}</span>
              <span className="text-[11px]">{t('schemas.factory.noClass')}</span>
            </li>
          ))}
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
