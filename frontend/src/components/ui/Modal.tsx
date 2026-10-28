import { useEffect, useRef, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { X } from 'lucide-react';
import { useTranslation } from 'react-i18next';

interface ModalProps {
  open: boolean;
  title: ReactNode;
  onClose: () => void;
  children: ReactNode;
  footer?: ReactNode;
  /** Tailwind max-width class of the dialog. */
  widthClass?: string;
  /** Prevents closing with Escape/backdrop while an operation is running. */
  busy?: boolean;
}

/** Centered dialog with backdrop, Escape-to-close and initial focus inside the dialog. */
export function Modal({ open, title, onClose, children, footer, widthClass = 'max-w-lg', busy = false }: ModalProps) {
  const { t } = useTranslation();
  const dialogRef = useRef<HTMLDivElement>(null);
  // callers pass inline closures; keep the latest ones without re-running the effects below
  const onCloseRef = useRef(onClose);
  const busyRef = useRef(busy);
  onCloseRef.current = onClose;
  busyRef.current = busy;

  // focus the first field once when the dialog opens (not on every re-render)
  useEffect(() => {
    if (open) {
      dialogRef.current?.querySelector<HTMLElement>('input, select, textarea, button:not([data-close])')?.focus();
    }
  }, [open]);

  useEffect(() => {
    if (!open) {
      return;
    }
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !busyRef.current) {
        onCloseRef.current();
      }
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [open]);

  if (!open) {
    return null;
  }
  return createPortal(
    <div className="fixed inset-0 z-50 flex animate-fade-in items-center justify-center bg-black/60 p-4" onMouseDown={() => !busyRef.current && onCloseRef.current()}>
      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        className={`gk-panel w-full ${widthClass} shadow-panel`}
        onMouseDown={(event) => event.stopPropagation()}
      >
        <div className="flex items-center gap-3 border-b border-gk-border px-5 py-3.5">
          <h2 className="min-w-0 flex-1 truncate text-base font-semibold">{title}</h2>
          <button
            type="button"
            data-close
            className="rounded p-1 text-gk-muted hover:bg-gk-hover hover:text-gk-text"
            onClick={onClose}
            disabled={busy}
            aria-label={t('common.close')}
          >
            <X className="h-4 w-4" aria-hidden />
          </button>
        </div>
        <div className="max-h-[70vh] overflow-y-auto px-5 py-4">{children}</div>
        {footer && <div className="flex justify-end gap-2 border-t border-gk-border px-5 py-3">{footer}</div>}
      </div>
    </div>,
    document.body,
  );
}
