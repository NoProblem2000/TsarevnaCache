import { useEffect, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { X } from 'lucide-react';
import { useTranslation } from 'react-i18next';

interface DrawerProps {
  open: boolean;
  title: ReactNode;
  subtitle?: ReactNode;
  onClose: () => void;
  children: ReactNode;
  footer?: ReactNode;
}

/** Right side panel that slides over the workspace; Escape or the backdrop closes it. */
export function Drawer({ open, title, subtitle, onClose, children, footer }: DrawerProps) {
  const { t } = useTranslation();

  useEffect(() => {
    if (!open) {
      return;
    }
    const onKey = (event: KeyboardEvent) => {
      // let open dialogs (e.g. a delete confirmation) handle Escape themselves
      if (event.key === 'Escape' && !document.querySelector('[role="dialog"][aria-modal="true"]')) {
        onClose();
      }
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [open, onClose]);

  if (!open) {
    return null;
  }
  return createPortal(
    <div className="fixed inset-0 z-40 flex justify-end">
      <div className="absolute inset-0 animate-fade-in bg-black/40" onMouseDown={onClose} aria-hidden />
      <aside
        role="complementary"
        aria-label={typeof title === 'string' ? title : undefined}
        className="relative flex h-full w-full max-w-3xl animate-slide-in flex-col border-l border-gk-border bg-gk-panel shadow-panel"
      >
        <div className="flex items-start gap-3 border-b border-gk-border px-5 py-3.5">
          <div className="min-w-0 flex-1">
            <h2 className="text-base font-semibold text-gk-text">{title}</h2>
            {subtitle && <div className="mt-0.5 min-w-0">{subtitle}</div>}
          </div>
          <button
            type="button"
            className="rounded p-1 text-gk-muted hover:bg-gk-hover hover:text-gk-text"
            onClick={onClose}
            aria-label={t('common.close')}
          >
            <X className="h-5 w-5" aria-hidden />
          </button>
        </div>
        <div className="min-h-0 flex-1 overflow-y-auto px-5 py-4">{children}</div>
        {footer && <div className="flex flex-wrap items-center gap-2 border-t border-gk-border px-5 py-3">{footer}</div>}
      </aside>
    </div>,
    document.body,
  );
}
