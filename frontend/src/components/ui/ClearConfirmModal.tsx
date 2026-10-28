import { useState } from 'react';
import { AlertOctagon, Eraser } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { ErrorMessage } from './ErrorMessage';
import { Modal } from './Modal';

interface ClearConfirmModalProps {
  open: boolean;
  /** Name of the structure; the user has to type it to enable the button. */
  name: string;
  title: string;
  onConfirm: () => Promise<unknown>;
  onClose: () => void;
}

/** Destructive confirmation that requires typing the structure name (used by Clear Map). */
export function ClearConfirmModal({ open, name, title, onConfirm, onClose }: ClearConfirmModalProps) {
  const { t } = useTranslation();
  const [typed, setTyped] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const matches = typed === name;

  const close = () => {
    setTyped('');
    setError(null);
    onClose();
  };
  const confirm = async () => {
    if (!matches) {
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await onConfirm();
      close();
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
      widthClass="max-w-md"
      title={title}
      footer={
        <>
          <button type="button" className="gk-btn-secondary" onClick={close} disabled={busy}>
            {t('common.cancel')}
          </button>
          <button type="button" className="gk-btn-danger" onClick={() => void confirm()} disabled={!matches || busy}>
            <Eraser className="h-4 w-4" aria-hidden />
            {t('clearModal.confirmButton')}
          </button>
        </>
      }
    >
      <form
        className="space-y-4"
        onSubmit={(event) => {
          event.preventDefault();
          void confirm();
        }}
      >
        <div className="flex items-start gap-3 rounded-md border border-gk-danger/40 bg-gk-danger/10 p-3">
          <AlertOctagon className="mt-0.5 h-5 w-5 shrink-0 text-gk-danger" aria-hidden />
          <p className="text-gk-text">{t('clearModal.warning', { name })}</p>
        </div>
        <label className="block">
          <span className="mb-1.5 block text-gk-muted">
            {t('clearModal.typeToConfirm')} <span className="gk-mono select-all font-semibold text-gk-danger">{name}</span>
          </span>
          <input
            className={`gk-input gk-mono ${typed && !matches ? 'border-gk-danger' : ''}`}
            value={typed}
            onChange={(event) => setTyped(event.target.value)}
            autoComplete="off"
            spellCheck={false}
            aria-label={t('clearModal.typeToConfirm')}
          />
        </label>
        {error !== null && <ErrorMessage error={error} />}
      </form>
    </Modal>
  );
}
