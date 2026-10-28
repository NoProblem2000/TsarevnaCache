import { AlertTriangle } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { ApiError } from '../../api/client';

/** Translated message for any error thrown by the API client. */
export function useErrorText() {
  const { t, i18n } = useTranslation();
  return (error: unknown): string => {
    if (error instanceof ApiError) {
      const key = `errors.${error.code}`;
      return i18n.exists(key) ? t(key) : error.message;
    }
    return t('errors.UNKNOWN');
  };
}

interface ErrorMessageProps {
  error: unknown;
  onRetry?: () => void;
  /** Show the technical backend message below the translated one. */
  showDetails?: boolean;
}

/** Inline error box with an optional retry button. */
export function ErrorMessage({ error, onRetry, showDetails = true }: ErrorMessageProps) {
  const { t } = useTranslation();
  const errorText = useErrorText();
  const technical = error instanceof ApiError && showDetails && error.message !== errorText(error) ? error.message : null;
  return (
    <div role="alert" className="flex items-start gap-3 rounded-lg border border-gk-danger/40 bg-gk-danger/10 p-3">
      <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-gk-danger" aria-hidden />
      <div className="min-w-0 flex-1">
        <p className="text-gk-text">{errorText(error)}</p>
        {technical && <p className="gk-mono mt-1 break-words text-xs text-gk-muted">{technical}</p>}
      </div>
      {onRetry && (
        <button type="button" className="gk-btn-secondary shrink-0" onClick={onRetry}>
          {t('common.retry')}
        </button>
      )}
    </div>
  );
}
