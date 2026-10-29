import { useQuery } from '@tanstack/react-query';
import { Activity, Copy } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { PROMETHEUS_PATH, endpoints } from '../../api/endpoints';
import { ErrorMessage } from '../../components/ui/ErrorMessage';
import { Modal } from '../../components/ui/Modal';
import { CenteredSpinner } from '../../components/ui/Spinner';
import { useToast } from '../../components/ui/Toast';

/** URL of the Prometheus exporter, a scrape configuration and a live preview of the metrics. */
export function PrometheusModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { t } = useTranslation();
  const toast = useToast();
  const url = `${window.location.origin}${PROMETHEUS_PATH}`;
  const preview = useQuery({
    queryKey: ['healthcheck', 'prometheus'],
    queryFn: endpoints.healthcheck.prometheus,
    enabled: open,
    refetchOnWindowFocus: false,
  });
  const scrapeConfig = `scrape_configs:
  - job_name: hazelcast-inspector
    metrics_path: ${PROMETHEUS_PATH}
    scheme: ${window.location.protocol.replace(':', '')}
    basic_auth:
      username: <admin user>
      password: <admin password>
    static_configs:
      - targets: ['${window.location.host}']`;

  const copy = async (text: string) => {
    try {
      await navigator.clipboard.writeText(text);
      toast.success(t('common.copied'));
    } catch {
      toast.error(t('errors.UNKNOWN'));
    }
  };

  return (
    <Modal
      open={open}
      onClose={onClose}
      widthClass="max-w-3xl"
      title={
        <span className="flex items-center gap-2">
          <Activity className="h-4 w-4 text-gk-cyan" aria-hidden />
          {t('health.prometheus.title')}
        </span>
      }
      footer={
        <button type="button" className="gk-btn-secondary" onClick={onClose}>
          {t('common.close')}
        </button>
      }
    >
      <div className="space-y-4">
        <div>
          <span className="gk-label">{t('health.prometheus.url')}</span>
          <div className="flex gap-2">
            <input className="gk-input gk-mono" value={url} readOnly aria-label={t('health.prometheus.url')} />
            <button type="button" className="gk-btn-secondary shrink-0" onClick={() => void copy(url)}>
              <Copy className="h-4 w-4" aria-hidden />
              {t('common.copy')}
            </button>
          </div>
          <p className="mt-1 text-xs text-gk-muted">{t('health.prometheus.authHint')}</p>
        </div>
        <div>
          <div className="mb-1 flex items-center">
            <span className="gk-label mb-0">{t('health.prometheus.scrapeConfig')}</span>
            <button type="button" className="gk-btn-ghost ml-auto px-2 py-0.5 text-xs" onClick={() => void copy(scrapeConfig)}>
              <Copy className="h-3.5 w-3.5" aria-hidden />
              {t('common.copy')}
            </button>
          </div>
          <pre className="gk-mono overflow-x-auto rounded-md border border-gk-border bg-gk-bg p-3 text-xs text-gk-text">{scrapeConfig}</pre>
        </div>
        <div>
          <span className="gk-label">{t('health.prometheus.preview')}</span>
          {preview.isPending && <CenteredSpinner />}
          {preview.isError && <ErrorMessage error={preview.error} onRetry={() => void preview.refetch()} />}
          {preview.data !== undefined && (
            <pre className="gk-mono max-h-72 overflow-auto rounded-md border border-gk-border bg-gk-bg p-3 text-xs leading-relaxed">
              {preview.data.split('\n').map((line, index) => (
                <span key={index} className={`block ${line.startsWith('#') ? 'text-gk-subtle' : 'text-gk-text'}`}>
                  {line || ' '}
                </span>
              ))}
            </pre>
          )}
        </div>
      </div>
    </Modal>
  );
}
