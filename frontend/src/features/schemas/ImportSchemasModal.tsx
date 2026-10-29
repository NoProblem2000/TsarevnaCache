import { useEffect, useState } from 'react';
import { Upload } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { endpoints } from '../../api/endpoints';
import type { DtoSchemaRegistryFile } from '../../api/types';
import { ErrorMessage } from '../../components/ui/ErrorMessage';
import { Modal } from '../../components/ui/Modal';
import { Segmented } from '../../components/ui/Segmented';
import { useToast } from '../../components/ui/Toast';
import { useInvalidateSchemaData } from './schemaLinks';

type ImportMode = 'merge' | 'replace';

interface ImportSchemasModalProps {
  /** Selected file; the dialog is open while set. */
  file: File | null;
  onClose: () => void;
}

/** Reads a `dto-schemas.json`, shows what it contains and imports it (merge or replace). */
export function ImportSchemasModal({ file, onClose }: ImportSchemasModalProps) {
  const { t } = useTranslation();
  const toast = useToast();
  const invalidate = useInvalidateSchemaData();
  const [content, setContent] = useState<DtoSchemaRegistryFile | null>(null);
  const [readError, setReadError] = useState<string | null>(null);
  const [mode, setMode] = useState<ImportMode>('merge');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);

  useEffect(() => {
    setContent(null);
    setReadError(null);
    setError(null);
    setMode('merge');
    if (!file) {
      return;
    }
    let cancelled = false;
    file
      .text()
      .then((text) => {
        const parsed = JSON.parse(text) as Partial<DtoSchemaRegistryFile>;
        if (!parsed || typeof parsed !== 'object' || !Array.isArray(parsed.schemas)) {
          throw new Error(t('schemas.import.notRegistry'));
        }
        if (!cancelled) {
          setContent(parsed as DtoSchemaRegistryFile);
        }
      })
      .catch((failure: unknown) => {
        if (!cancelled) {
          setReadError(failure instanceof Error ? failure.message : String(failure));
        }
      });
    return () => {
      cancelled = true;
    };
  }, [file, t]);

  const submit = async () => {
    if (!content) {
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const view = await endpoints.schemas.importFile(content, mode);
      await invalidate();
      toast.success(t('schemas.import.done', { count: view.schemas.length }));
      onClose();
    } catch (failure) {
      setError(failure);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal
      open={file !== null}
      onClose={onClose}
      busy={busy}
      title={
        <span className="flex items-center gap-2">
          <Upload className="h-4 w-4 text-gk-cyan" aria-hidden />
          {t('schemas.import.title')}
        </span>
      }
      footer={
        <>
          <button type="button" className="gk-btn-secondary" onClick={onClose} disabled={busy}>
            {t('common.cancel')}
          </button>
          <button
            type="button"
            className={mode === 'replace' ? 'gk-btn-danger' : 'gk-btn-primary'}
            onClick={() => void submit()}
            disabled={busy || !content}
          >
            {busy ? t('schemas.import.importing') : t('schemas.import.submit')}
          </button>
        </>
      }
    >
      <div className="space-y-3">
        <p className="gk-mono truncate text-xs text-gk-muted">{file?.name}</p>
        {readError && <p className="rounded-md border border-gk-danger/40 bg-gk-danger/10 p-3 text-gk-text">{readError}</p>}
        {content && (
          <>
            <p className="text-gk-text">
              {t('schemas.import.summary', {
                count: content.schemas.length,
                constants: Object.keys(content.factoryConstants ?? {}).length,
                factoryId: content.factoryId ?? 1,
              })}
            </p>
            <ul className="flex flex-wrap gap-1.5">
              {content.schemas.slice(0, 30).map((schema) => (
                <li key={`${schema.factoryId ?? 1}/${schema.classId}`} className="gk-badge gk-mono bg-gk-bg text-gk-cyan">
                  {schema.className}
                </li>
              ))}
              {content.schemas.length > 30 && <li className="gk-badge bg-gk-bg text-gk-muted">+{content.schemas.length - 30}</li>}
            </ul>
            <Segmented<ImportMode>
              ariaLabel={t('schemas.import.mode')}
              value={mode}
              onChange={setMode}
              options={[
                { value: 'merge', label: t('schemas.import.merge') },
                { value: 'replace', label: t('schemas.import.replace') },
              ]}
            />
            <p className="text-xs text-gk-muted">{mode === 'merge' ? t('schemas.import.mergeHint') : t('schemas.import.replaceHint')}</p>
          </>
        )}
        {error !== null && <ErrorMessage error={error} />}
      </div>
    </Modal>
  );
}
