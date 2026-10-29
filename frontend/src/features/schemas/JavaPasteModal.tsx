import { useEffect, useRef, useState } from 'react';
import { AlertTriangle, CheckCircle2, FileUp, Factory, PenLine, ScanSearch, Sparkles } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { endpoints } from '../../api/endpoints';
import type { DtoParseResult, FactoryParseResult } from '../../api/types';
import { JsonEditor } from '../../components/editor/JsonEditor';
import { ErrorMessage } from '../../components/ui/ErrorMessage';
import { Modal } from '../../components/ui/Modal';
import { Segmented } from '../../components/ui/Segmented';
import { useToast } from '../../components/ui/Toast';
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

/**
 * "Smart Java Paste": parses a pasted `DataSerializableFactory.java` (ids are stored at once) or a
 * DTO class (the result opens in the visual schema editor).
 */
export function JavaPasteModal({ open, initialMode, target, onClose, onAnalyzed, onManual }: JavaPasteModalProps) {
  const { t } = useTranslation();
  const toast = useToast();
  const invalidate = useInvalidateSchemaData();
  const fileInput = useRef<HTMLInputElement>(null);
  const [mode, setMode] = useState<PasteMode>(initialMode);
  const [sources, setSources] = useState<Record<PasteMode, string>>({ factory: '', dto: '' });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [factoryResult, setFactoryResult] = useState<FactoryParseResult | null>(null);

  useEffect(() => {
    if (open) {
      setMode(initialMode);
      setError(null);
      setFactoryResult(null);
    }
  }, [open, initialMode]);

  const source = sources[mode];
  const setSource = (value: string) => setSources((current) => ({ ...current, [mode]: value }));

  const close = () => {
    if (!busy) {
      onClose();
    }
  };

  const openFile = async (file: File | undefined) => {
    if (!file) {
      return;
    }
    setSource(await file.text());
    setError(null);
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

  return (
    <Modal
      open={open}
      onClose={close}
      busy={busy}
      widthClass="max-w-4xl"
      title={
        <span className="flex items-center gap-2">
          <Sparkles className="h-4 w-4 text-gk-cyan" aria-hidden />
          {t('schemas.paste.title')}
        </span>
      }
      footer={
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
      }
    >
      <div className="space-y-3">
        <div className="flex flex-wrap items-center gap-3">
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
          <button type="button" className="gk-btn-ghost ml-auto px-2 py-1 text-xs" onClick={() => fileInput.current?.click()}>
            <FileUp className="h-3.5 w-3.5" aria-hidden />
            {t('schemas.paste.openFile')}
          </button>
          <input
            ref={fileInput}
            type="file"
            accept=".java,text/x-java-source,text/plain"
            className="hidden"
            onChange={(event) => {
              void openFile(event.target.files?.[0]);
              event.target.value = '';
            }}
          />
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
          minHeight="320px"
          height="380px"
          placeholder={mode === 'factory' ? FACTORY_PLACEHOLDER : DTO_PLACEHOLDER}
          ariaLabel={mode === 'factory' ? t('schemas.paste.modeFactory') : t('schemas.paste.modeDto')}
        />

        {mode === 'factory' && factoryResult && <FactoryResult result={factoryResult} />}
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
