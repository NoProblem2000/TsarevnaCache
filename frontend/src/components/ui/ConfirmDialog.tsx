import { useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { ErrorMessage } from './ErrorMessage';
import { Modal } from './Modal';

interface ConfirmDialogProps {
  open: boolean;
  title: ReactNode;
  message: ReactNode;
  confirmLabel?: string;
  danger?: boolean;
  onConfirm: () => Promise<unknown>;
  onClose: () => void;
}

/** Simple yes/no confirmation that runs an async action and shows its error inline. */
export function ConfirmDialog({ open, title, message, confirmLabel, danger = false, onConfirm, onClose }: ConfirmDialogProps) {
  const { t } = useTranslation();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);

  const close = () => {
    setError(null);
    onClose();
  };
  const confirm = async () => {
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
      title={title}
      onClose={close}
      busy={busy}
      widthClass="max-w-md"
      footer={
        <>
          <button type="button" className="gk-btn-secondary" onClick={close} disabled={busy}>
            {t('common.cancel')}
          </button>
          <button type="button" className={danger ? 'gk-btn-danger' : 'gk-btn-primary'} onClick={() => void confirm()} disabled={busy}>
            {confirmLabel ?? t('common.confirm')}
          </button>
        </>
      }
    >
      <div className="space-y-3">
        <p className="text-gk-muted">{message}</p>
        {error !== null && <ErrorMessage error={error} />}
      </div>
    </Modal>
  );
}
