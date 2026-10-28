import { Loader2 } from 'lucide-react';
import { useTranslation } from 'react-i18next';

/** Animated loading indicator with an accessible label. */
export function Spinner({ label, className = '' }: { label?: string; className?: string }) {
  const { t } = useTranslation();
  return (
    <span role="status" className={`inline-flex items-center gap-2 text-gk-muted ${className}`}>
      <Loader2 className="h-4 w-4 animate-spin text-gk-cyan" aria-hidden />
      <span>{label ?? t('common.loading')}</span>
    </span>
  );
}

/** Spinner centered in its container. */
export function CenteredSpinner({ label }: { label?: string }) {
  return (
    <div className="flex h-full min-h-[200px] items-center justify-center">
      <Spinner label={label} />
    </div>
  );
}
