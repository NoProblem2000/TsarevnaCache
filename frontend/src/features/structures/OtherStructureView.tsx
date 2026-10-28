import { useState, type ReactNode } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Inbox, Info, RefreshCw, Trash2 } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { endpoints } from '../../api/endpoints';
import { queryKeys } from '../../api/queries';
import type { OtherStructureItem, StructureType } from '../../api/types';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { ErrorMessage } from '../../components/ui/ErrorMessage';
import { CenteredSpinner } from '../../components/ui/Spinner';
import { useToast } from '../../components/ui/Toast';
import { TypeBadge, ValuePreview } from '../../components/ui/ValueBadges';
import { formatNumber } from '../../lib/format';

interface OtherStructureViewProps {
  type: StructureType;
  typePath: string;
  name: string;
  /** Extra toolbar buttons (e.g. Clear). */
  toolbarActions?: ReactNode;
}

/** Items of a queue, set, list, multimap or replicated map with per-item removal. */
export function OtherStructureView({ type, typePath, name, toolbarActions }: OtherStructureViewProps) {
  const { t, i18n } = useTranslation();
  const toast = useToast();
  const queryClient = useQueryClient();
  const [toRemove, setToRemove] = useState<OtherStructureItem | null>(null);
  const view = useQuery({
    queryKey: queryKeys.other(typePath, name),
    queryFn: () => endpoints.other.view(typePath, name, 50),
    refetchInterval: 10_000,
  });
  const keyed = type === 'MULTI_MAP' || type === 'REPLICATED_MAP';

  const remove = async (item: OtherStructureItem) => {
    // multimaps remove a single value (ref + keyRef); replicated maps remove by key
    await endpoints.other.removeItem(typePath, name, type === 'REPLICATED_MAP' ? undefined : item.ref, item.keyRef);
    toast.success(t('other.removed'));
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: queryKeys.other(typePath, name) }),
      queryClient.invalidateQueries({ queryKey: queryKeys.allStructures }),
    ]);
  };

  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <div className="flex flex-wrap items-center gap-3 border-b border-gk-border px-4 py-3">
        {view.data && (
          <span className="text-gk-muted">
            {t('other.size')}: <span className="gk-mono text-gk-text">{formatNumber(view.data.size, i18n.language)}</span>
          </span>
        )}
        {view.data?.itemsAvailable && view.data.items.length > 0 && view.data.size > view.data.items.length && (
          <span className="text-xs text-gk-muted">{t('other.showing', { count: view.data.items.length })}</span>
        )}
        <div className="ml-auto flex items-center gap-2">
          <button type="button" className="gk-btn-ghost" onClick={() => void view.refetch()} aria-label={t('common.refresh')} title={t('common.refresh')}>
            <RefreshCw className={`h-4 w-4 ${view.isFetching ? 'animate-spin' : ''}`} aria-hidden />
          </button>
          {toolbarActions}
        </div>
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto">
        {view.isPending && <CenteredSpinner />}
        {view.isError && (
          <div className="p-4">
            <ErrorMessage error={view.error} onRetry={() => void view.refetch()} />
          </div>
        )}
        {view.data && !view.data.itemsAvailable && (
          <div className="m-4 flex items-start gap-2 rounded-lg border border-gk-border bg-gk-panel p-4 text-gk-muted">
            <Info className="mt-0.5 h-4 w-4 shrink-0 text-gk-cyan" aria-hidden />
            {t('other.itemsUnavailable', { size: formatNumber(view.data.size, i18n.language) })}
          </div>
        )}
        {view.data?.itemsAvailable && view.data.items.length === 0 && (
          <div className="flex flex-col items-center gap-2 py-16 text-gk-muted">
            <Inbox className="h-8 w-8 text-gk-subtle" aria-hidden />
            {t('other.empty')}
          </div>
        )}
        {view.data?.itemsAvailable && view.data.items.length > 0 && (
          <table className="w-full table-fixed text-left">
            <thead className="sticky top-0 bg-gk-panel text-xs uppercase tracking-wide text-gk-muted">
              <tr>
                <th className="w-12 px-3 py-2 font-medium">#</th>
                {keyed && <th className="w-[28%] px-3 py-2 font-medium">{t('other.key')}</th>}
                <th className="px-3 py-2 font-medium">{t('other.value')}</th>
                <th className="w-[120px] px-3 py-2 font-medium">{t('inspector.schemaKind')}</th>
                <th className="w-16 px-3 py-2" />
              </tr>
            </thead>
            <tbody>
              {view.data.items.map((item, index) => (
                <tr key={`${item.keyRef ?? ''}:${item.ref ?? ''}:${index}`} className="group border-t border-gk-border hover:bg-gk-card">
                  <td className="gk-mono px-3 py-2 text-gk-subtle">{index}</td>
                  {keyed && item.key && (
                    <td className="px-3 py-2">
                      <ValuePreview value={item.key} />
                    </td>
                  )}
                  <td className="px-3 py-2">
                    <ValuePreview value={item.value} />
                  </td>
                  <td className="px-3 py-2">
                    <TypeBadge value={item.value} />
                  </td>
                  <td className="px-3 py-2 text-right">
                    <button
                      type="button"
                      className="rounded p-1.5 text-gk-subtle opacity-60 hover:bg-gk-danger/15 hover:text-gk-danger group-hover:opacity-100"
                      onClick={() => setToRemove(item)}
                      title={type === 'REPLICATED_MAP' ? t('other.removeKey') : t('other.removeItem')}
                      aria-label={t('other.removeItem')}
                    >
                      <Trash2 className="h-3.5 w-3.5" aria-hidden />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      <ConfirmDialog
        open={toRemove !== null}
        title={t('other.removeItem')}
        message={
          <>
            {t('other.removeConfirm')}
            {toRemove && <span className="gk-mono mt-2 block truncate text-gk-cyan">{toRemove.key?.preview ?? toRemove.value.preview}</span>}
          </>
        }
        confirmLabel={t('common.delete')}
        danger
        onConfirm={() => (toRemove ? remove(toRemove) : Promise.resolve())}
        onClose={() => setToRemove(null)}
      />
    </div>
  );
}
