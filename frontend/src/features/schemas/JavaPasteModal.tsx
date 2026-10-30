import { useEffect, useRef, useState, type DragEvent } from 'react';
import { AlertTriangle, ArrowLeft, CheckCircle2, Factory, FilePlus2, FolderOpen, PenLine, ScanSearch, Sparkles, UploadCloud } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { endpoints } from '../../api/endpoints';
import type { BatchImportResult, DtoParseResult, FactoryParseResult } from '../../api/types';
import { JsonEditor } from '../../components/editor/JsonEditor';
import { ErrorMessage } from '../../components/ui/ErrorMessage';
import { Modal } from '../../components/ui/Modal';
import { Segmented } from '../../components/ui/Segmented';
import { useToast } from '../../components/ui/Toast';
import { BatchImportReport } from './BatchImportReport';
import { droppedFiles, isJavaFile, picked, readJavaFiles, withSkipped, type PickedFile } from './javaFiles';
import { useInvalidateSchemaData, type DtoTarget } from './schemaLinks';

export type PasteMode = 'factory' | 'dto';

interface JavaPasteModalProps {
  open: boolean;
  initialMode: PasteMode;
  /** Class that needs a schema (from "Configure DTO Schema"); shown as context. */
  target?: DtoTarget;
  onClose: () => void;
  /** A DTO was analyzed: continue in the schema editor. */
  onAnalyzed: (result: DtoParseResult) => void;
  /** Skip the parser and define the fields by hand. */
  onManual?: () => void;
}

const FACTORY_PLACEHOLDER = `public class DataSerializableFactory implements com.hazelcast.nio.serialization.DataSerializableFactory {
    public static final int FACTORY_ID = 1;
    public static final int SOMEDTO = 1;

    public IdentifiedDataSerializable create(int typeId) {
        return switch (typeId) {
            case SOMEDTO -> new SomeDTO();
            default -> null;
        };
    }
}`;

const DTO_PLACEHOLDER = `public class SomeDTO implements IdentifiedDataSerializable {
    private Long id;
    private String name;

    public int getClassId() { return DataSerializableFactory.SOMEDTO; }

    public void readData(ObjectDataInput in) throws IOException {
        this.id = SerializationUtils.readNullableLong(in);
        this.name = SerializationUtils.readNullableString(in);
    }
}`;

/** A pasted / opened source that looks like a `DataSerializableFactory` rather than a DTO. */
const looksLikeFactory = (source: string) =>
  /\bint\s+FACTORY_ID\s*=/.test(source) && /\bcreate\s*\(\s*(?:final\s+)?int\s+\w+\s*\)/.test(source) && !/\bint\s+getClassId\s*\(/.test(source);

/**
 * "Smart Java Paste": parses a pasted `DataSerializableFactory.java` (ids are stored at once) or a
 * DTO class (the result opens in the visual schema editor). Several `.java` files, a whole folder
 * or files dropped onto the dialog are imported in one batch: the backend finds the factory,
 * ignores everything that is not a DTO of it and saves the rest; a summary is shown afterwards.
 */
export function JavaPasteModal({ open, initialMode, target, onClose, onAnalyzed, onManual }: JavaPasteModalProps) {
  const { t } = useTranslation();
  const toast = useToast();
  const invalidate = useInvalidateSchemaData();
  const fileInput = useRef<HTMLInputElement>(null);
  const folderInput = useRef<HTMLInputElement>(null);
  const dragDepth = useRef(0);
  const [mode, setMode] = useState<PasteMode>(initialMode);
  const [sources, setSources] = useState<Record<PasteMode, string>>({ factory: '', dto: '' });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [factoryResult, setFactoryResult] = useState<FactoryParseResult | null>(null);
  const [batchResult, setBatchResult] = useState<BatchImportResult | null>(null);
  const [progress, setProgress] = useState<{ files: number } | null>(null);
  const [dragging, setDragging] = useState(false);

  // every open starts clean: no source, result or error of a previously parsed class survives
  // (the page also remounts the dialog per open)
  useEffect(() => {
    if (open) {
      setMode(initialMode);
      setSources({ factory: '', dto: '' });
      setError(null);
      setFactoryResult(null);
      setBatchResult(null);
    }
  }, [open, initialMode]);

  // `webkitdirectory` is not part of React's input attributes
  useEffect(() => {
    folderInput.current?.setAttribute('webkitdirectory', '');
    folderInput.current?.setAttribute('directory', '');
  }, []);

  const source = sources[mode];
  const setSource = (value: string) => {
    setSources((current) => ({ ...current, [mode]: value }));
    if (value.trim() === '') {
      // cleared input: forget what was parsed from the previous text
      setError(null);
      setFactoryResult(null);
    }
  };

  const close = () => {
    if (!busy) {
      onClose();
    }
  };

  /** One file: shown in the editor (switching to the factory mode when it is the factory). */
  const openSingle = async (file: File) => {
    const text = await file.text();
    const nextMode: PasteMode = looksLikeFactory(text) ? 'factory' : mode;
    setMode(nextMode);
    setSources((current) => ({ ...current, [nextMode]: text }));
    setFactoryResult(null);
    setError(null);
  };

  /** Several files or a folder: read in parallel and imported in one batch. */
  const importBatch = async (files: PickedFile[]) => {
    setBusy(true);
    setError(null);
    setProgress({ files: files.length });
    try {
      const { sources: javaSources, skipped } = await readJavaFiles(files);
      if (javaSources.length === 0) {
        setError(new Error(t('schemas.batch.noJavaFiles', { count: files.length })));
        return;
      }
      const result = withSkipped(await endpoints.schemas.batchImportJava(javaSources), skipped);
      setBatchResult(result);
      await invalidate();
      toast.success(t('schemas.batch.toast', { count: result.importedCount, ignored: result.ignoredCount }));
    } catch (failure) {
      setError(failure);
    } finally {
      setProgress(null);
      setBusy(false);
    }
  };

  const handleFiles = async (files: PickedFile[], fromFolder: boolean) => {
    if (files.length === 0 || busy) {
      return;
    }
    if (files.length === 1 && !fromFolder && isJavaFile(files[0].path)) {
      await openSingle(files[0].file);
    } else {
      await importBatch(files);
    }
  };

  const onDragEnter = (event: DragEvent) => {
    if (!event.dataTransfer.types.includes('Files')) {
      return;
    }
    event.preventDefault();
    dragDepth.current += 1;
    setDragging(true);
  };
  const onDragLeave = (event: DragEvent) => {
    event.preventDefault();
    dragDepth.current = Math.max(0, dragDepth.current - 1);
    if (dragDepth.current === 0) {
      setDragging(false);
    }
  };
  const onDrop = async (event: DragEvent) => {
    event.preventDefault();
    dragDepth.current = 0;
    setDragging(false);
    const items = Array.from(event.dataTransfer.items ?? []);
    const folder = items.some(
      (item) => (item as DataTransferItem & { webkitGetAsEntry?: () => { isDirectory: boolean } | null }).webkitGetAsEntry?.()?.isDirectory,
    );
    await handleFiles(await droppedFiles(event.dataTransfer), folder);
  };

  const parseFactory = async () => {
    setBusy(true);
    setError(null);
    try {
      const result = await endpoints.schemas.parseFactory(source, true);
      setFactoryResult(result);
      await invalidate();
      toast.success(t('schemas.paste.factorySaved', { count: Object.keys(result.constants).length, factoryId: result.factoryId }));
    } catch (failure) {
      setError(failure);
    } finally {
      setBusy(false);
    }
  };

  const analyzeDto = async () => {
    setBusy(true);
    setError(null);
    try {
      onAnalyzed(await endpoints.schemas.parseDto(source));
    } catch (failure) {
      setError(failure);
    } finally {
      setBusy(false);
    }
  };

  const fileInputs = (
    <>
      <input
        ref={fileInput}
        type="file"
        multiple
        accept=".java"
        className="hidden"
        onChange={(event) => {
          const files = picked(Array.from(event.target.files ?? []));
          event.target.value = '';
          void handleFiles(files, false);
        }}
      />
      <input
        ref={folderInput}
        type="file"
        multiple
        className="hidden"
        onChange={(event) => {
          const files = picked(Array.from(event.target.files ?? []));
          event.target.value = '';
          void handleFiles(files, true);
        }}
      />
    </>
  );

  return (
    <Modal
      open={open}
      onClose={close}
      busy={busy}
      widthClass="max-w-4xl"
      title={
        <span className="flex items-center gap-2">
          <Sparkles className="h-4 w-4 text-gk-cyan" aria-hidden />
          {batchResult ? t('schemas.batch.title') : t('schemas.paste.title')}
        </span>
      }
      footer={
        batchResult ? (
          <>
            <button type="button" className="gk-btn-ghost mr-auto" onClick={() => setBatchResult(null)} disabled={busy}>
              <ArrowLeft className="h-4 w-4" aria-hidden />
              {t('schemas.batch.back')}
            </button>
            <button type="button" className="gk-btn-secondary" onClick={() => fileInput.current?.click()} disabled={busy}>
              <FilePlus2 className="h-4 w-4" aria-hidden />
              {t('schemas.batch.importMore')}
            </button>
            <button type="button" className="gk-btn-primary" onClick={close} disabled={busy}>
              {t('common.close')}
            </button>
          </>
        ) : (
          <>
            {mode === 'dto' && onManual && (
              <button type="button" className="gk-btn-ghost mr-auto" onClick={onManual} disabled={busy}>
                <PenLine className="h-4 w-4" aria-hidden />
                {t('schemas.paste.manual')}
              </button>
            )}
            <button type="button" className="gk-btn-secondary" onClick={close} disabled={busy}>
              {factoryResult ? t('common.close') : t('common.cancel')}
            </button>
            {mode === 'factory' ? (
              <button type="button" className="gk-btn-primary" onClick={() => void parseFactory()} disabled={busy || source.trim() === ''}>
                <Factory className="h-4 w-4" aria-hidden />
                {busy ? t('schemas.paste.parsing') : t('schemas.paste.parseFactory')}
              </button>
            ) : (
              <button type="button" className="gk-btn-primary" onClick={() => void analyzeDto()} disabled={busy || source.trim() === ''}>
                <ScanSearch className="h-4 w-4" aria-hidden />
                {busy ? t('schemas.paste.parsing') : t('schemas.paste.analyze')}
              </button>
            )}
          </>
        )
      }
    >
      <div
        className="relative space-y-3"
        onDragEnter={onDragEnter}
        onDragOver={(event) => {
          if (event.dataTransfer.types.includes('Files')) {
            event.preventDefault();
            event.dataTransfer.dropEffect = 'copy';
          }
        }}
        onDragLeave={onDragLeave}
        onDrop={(event) => void onDrop(event)}
      >
        {fileInputs}
        {dragging && (
          <div className="pointer-events-none absolute inset-0 z-20 flex flex-col items-center justify-center gap-2 rounded-lg border-2 border-dashed border-gk-cyan bg-gk-bg/90 text-gk-cyan">
            <UploadCloud className="h-8 w-8" aria-hidden />
            <p className="text-sm font-medium">{t('schemas.batch.dropNow')}</p>
            <p className="text-xs text-gk-muted">{t('schemas.batch.dropHint')}</p>
          </div>
        )}
        {progress && (
          <div className="absolute inset-0 z-10 flex flex-col items-center justify-center gap-2 rounded-lg bg-gk-bg/85 text-gk-text">
            <UploadCloud className="h-7 w-7 animate-pulse text-gk-cyan" aria-hidden />
            <p className="text-sm">{t('schemas.batch.importing', { count: progress.files })}</p>
          </div>
        )}

        {batchResult ? (
          <BatchImportReport result={batchResult} />
        ) : (
          <>
            <div className="flex flex-wrap items-center gap-2">
              <Segmented<PasteMode>
                ariaLabel={t('schemas.paste.mode')}
                value={mode}
                onChange={(next) => {
                  setMode(next);
                  setError(null);
                }}
                options={[
                  { value: 'factory', label: t('schemas.paste.modeFactory') },
                  { value: 'dto', label: t('schemas.paste.modeDto') },
                ]}
              />
              <button
                type="button"
                className="gk-btn-ghost ml-auto px-2 py-1 text-xs"
                onClick={() => fileInput.current?.click()}
                disabled={busy}
                title={t('schemas.batch.openFilesHint')}
              >
                <FilePlus2 className="h-3.5 w-3.5" aria-hidden />
                {t('schemas.batch.openFiles')}
              </button>
              <button
                type="button"
                className="gk-btn-ghost px-2 py-1 text-xs"
                onClick={() => folderInput.current?.click()}
                disabled={busy}
                title={t('schemas.batch.selectFolderHint')}
              >
                <FolderOpen className="h-3.5 w-3.5" aria-hidden />
                {t('schemas.batch.selectFolder')}
              </button>
            </div>

            {target && mode === 'dto' && (
              <p className="rounded-md border border-gk-cyan/30 bg-gk-cyan/5 px-3 py-2 text-xs text-gk-text">
                {target.className
                  ? t('schemas.paste.targetHint', { name: target.className, factoryId: target.factoryId, classId: target.classId })
                  : t('schemas.paste.targetHintUnknown', { factoryId: target.factoryId, classId: target.classId })}
              </p>
            )}
            <p className="text-xs text-gk-muted">{mode === 'factory' ? t('schemas.paste.factoryHint') : t('schemas.paste.dtoHint')}</p>

            <JsonEditor
              language="text"
              value={source}
              onChange={setSource}
              minHeight="280px"
              height="320px"
              placeholder={mode === 'factory' ? FACTORY_PLACEHOLDER : DTO_PLACEHOLDER}
              ariaLabel={mode === 'factory' ? t('schemas.paste.modeFactory') : t('schemas.paste.modeDto')}
            />

            <button
              type="button"
              className="flex w-full items-center justify-center gap-2 rounded-lg border border-dashed border-gk-border px-3 py-3 text-xs text-gk-muted transition-colors hover:border-gk-cyan/60 hover:text-gk-cyan"
              onClick={() => fileInput.current?.click()}
              disabled={busy}
            >
              <UploadCloud className="h-4 w-4" aria-hidden />
              {t('schemas.batch.dropZone')}
            </button>

            {mode === 'factory' && factoryResult && <FactoryResult result={factoryResult} />}
          </>
        )}
        {error !== null && <ErrorMessage error={error} />}
      </div>
    </Modal>
  );
}


function FactoryResult({ result }: { result: FactoryParseResult }) {
  const { t } = useTranslation();
  const classByConstant = Object.entries(result.classes);
  const constants = Object.entries(result.constants).sort((a, b) => a[1] - b[1]);
  return (
    <div className="space-y-2 rounded-lg border border-gk-green/40 bg-gk-green/5 p-3 text-xs">
      <p className="flex items-center gap-1.5 font-medium text-gk-green">
        <CheckCircle2 className="h-3.5 w-3.5" aria-hidden />
        {t('schemas.paste.factoryResult', { factoryId: result.factoryId, count: constants.length })}
      </p>
      <div className="flex flex-wrap gap-1.5">
        {constants.map(([name, id]) => {
          const className = classByConstant.find(([, classId]) => classId === id)?.[0];
          return (
            <span key={name} className="gk-badge gk-mono bg-gk-bg text-gk-text">
              <span className="text-gk-cyan">{name}</span>&nbsp;=&nbsp;{id}
              {className && <span className="text-gk-purple">&nbsp;→ {className}</span>}
            </span>
          );
        })}
      </div>
      {result.warnings.length > 0 && (
        <ul className="space-y-0.5 text-gk-warning">
          {result.warnings.map((warning) => (
            <li key={warning} className="flex items-start gap-1.5">
              <AlertTriangle className="mt-0.5 h-3 w-3 shrink-0" aria-hidden />
              <span className="gk-mono break-words">{warning}</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
