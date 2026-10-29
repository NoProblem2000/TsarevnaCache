import { useEffect, useState } from 'react';
import { EyeOff } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import type { HealthCheckIssue } from '../../api/types';
import { ErrorMessage } from '../../components/ui/ErrorMessage';
import { Modal } from '../../components/ui/Modal';
import { Segmented } from '../../components/ui/Segmented';
import { wholeRuleId } from './healthModel';

type Scope = 'target' | 'rule';

interface IgnoreIssueModalProps {
  /** Issue to hide; the dialog is open while set. */
  issue: HealthCheckIssue | null;
  onClose: () => void;
  onConfirm: (issueId: string, reason: string) => Promise<unknown>;
}

/** Adds an issue (this target only) or its whole rule to the ignore list, with a reason. */
export function IgnoreIssueModal({ issue, onClose, onConfirm }: IgnoreIssueModalProps) {
  const { t } = useTranslation();
  const [scope, setScope] = useState<Scope>('target');
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);

  useEffect(() => {
    setScope('target');
    setReason('');
    setError(null);
  }, [issue]);

  if (!issue) {
    return null;
  }
  const issueId = scope === 'target' ? issue.issueId : wholeRuleId(issue);

  const submit = async () => {
    setBusy(true);
    setError(null);
    try {
      await onConfirm(issueId, reason.trim());
      onClose();
    } catch (failure) {
      setError(failure);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal
      open
      onClose={onClose}
      busy={busy}
      title={
        <span className="flex items-center gap-2">
          <EyeOff className="h-4 w-4 text-gk-cyan" aria-hidden />
          {t('health.ignoreDialog.title')}
        </span>
      }
      footer={
        <>
          <button type="button" className="gk-btn-secondary" onClick={onClose} disabled={busy}>
            {t('common.cancel')}
          </button>
          <button type="button" className="gk-btn-primary" onClick={() => void submit()} disabled={busy}>
            {t('health.ignoreCheck')}
          </button>
        </>
      }
    >
      <div className="space-y-4">
        <p className="text-gk-muted">
          {t(`health.checks.${issue.checkType}.title`)} · <span className="gk-mono text-gk-cyan">{issue.targetName}</span>
        </p>
        <div className="space-y-1.5">
          <span className="gk-label">{t('health.ignoreDialog.scope')}</span>
          <Segmented<Scope>
            ariaLabel={t('health.ignoreDialog.scope')}
            value={scope}
            onChange={setScope}
            options={[
              { value: 'target', label: t('health.ignoreDialog.scopeTarget') },
              { value: 'rule', label: t('health.ignoreDialog.scopeRule') },
            ]}
          />
          <p className="gk-mono break-all text-xs text-gk-subtle">{issueId}</p>
        </div>
        <label className="block">
          <span className="gk-label">{t('health.ignoreDialog.reason')}</span>
          <textarea
            className="gk-input min-h-[80px] resize-y"
            value={reason}
            onChange={(event) => setReason(event.target.value)}
            placeholder={t('health.ignoreDialog.reasonPlaceholder')}
            maxLength={2000}
          />
        </label>
        <p className="text-xs text-gk-muted">{t('health.ignoreDialog.hint')}</p>
        {error !== null && <ErrorMessage error={error} />}
      </div>
    </Modal>
  );
}
