import { useState, type ReactNode } from 'react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { Inbox, RefreshCw, Wind } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { ApiError } from '../../api/client';
import { endpoints } from '../../api/endpoints';
import { queryKeys } from '../../api/queries';
import type { EntryDTO, EntryQuery, EntrySortBy } from '../../api/types';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { ErrorMessage } from '../../components/ui/ErrorMessage';
import { CenteredSpinner } from '../../components/ui/Spinner';
import { useToast } from '../../components/ui/Toast';
import { EntryTable } from './EntryTable';
import { MissingSchemasBanner } from './MissingSchemasBanner';
import { DEFAULT_SCAN_LIMIT, DEFAULT_SORT, isSorted, nextSort, type EntrySort } from './entrySort';
import { Pagination } from './Pagination';
import { SearchBar, type AppliedSearch } from './SearchBar';
import { SortToolbar } from './SortToolbar';
import { useInvalidateMapData } from './useInvalidateMapData';

interface DataBrowserProps {
  mapName: string;
  /** Extra toolbar buttons (Put entry, Clear map). */
  toolbarActions?: ReactNode;
  /** Opens the entry inspector for a row. */
  onOpenEntry?: (entry: EntryDTO) => void;
}

/**
 * Data browser of one map. Unfiltered pages are cursor based: the cursors of visited pages are
 * kept so "previous" works; predicate pages are addressed by index. A sort (hot keys analyzer)
 * ranks a sample of the entries (filtered by the predicate, if any) on the server; its pages are
 * addressed by index as well.
 */
export function DataBrowser({ mapName, toolbarActions, onOpenEntry }: DataBrowserProps) {
  const { t } = useTranslation();
  const toast = useToast();
  const invalidate = useInvalidateMapData(mapName);
  const [search, setSearch] = useState<AppliedSearch>({ mode: 'all' });
  const [pageSize, setPageSize] = useState(20);
  const [page, setPage] = useState(0);
  const [cursors, setCursors] = useState<(string | undefined)[]>([undefined]);
  const [toDelete, setToDelete] = useState<EntryDTO | null>(null);
  const [evictOpen, setEvictOpen] = useState(false);
  const [sort, setSort] = useState<EntrySort>(DEFAULT_SORT);
  const [scanLimit, setScanLimit] = useState(DEFAULT_SCAN_LIMIT);
  const sorted = isSorted(sort) && search.mode !== 'key';

  const query: EntryQuery = { size: pageSize };
  if (search.mode === 'key') {
    Object.assign(query, { searchKey: search.key, keyType: search.keyType, keyClassName: search.keyClassName });
  } else if (sorted) {
    Object.assign(query, {
      sortBy: sort.sortBy,
      sortDirection: sort.direction,
      scanLimit,
      page,
      sqlPredicate: search.mode === 'predicate' ? search.predicate : undefined,
    });
  } else if (search.mode === 'predicate') {
    Object.assign(query, { sqlPredicate: search.predicate, page });
  } else {
    Object.assign(query, { cursor: cursors[page], page });
  }

  const entries = useQuery({
    queryKey: [...queryKeys.mapEntries(mapName), query],
    queryFn: () => endpoints.maps.entries(mapName, query),
    placeholderData: keepPreviousData,
  });

  const resetPaging = () => {
    setPage(0);
    setCursors([undefined]);
  };
  const applySearch = (next: AppliedSearch) => {
    setSearch(next);
    resetPaging();
  };
  const changePageSize = (size: number) => {
    setPageSize(size);
    resetPaging();
  };
  const changeSort = (next: EntrySort) => {
    setSort(next);
    resetPaging();
  };
  const sortByColumn = (column: EntrySortBy) => changeSort(nextSort(sorted ? sort : DEFAULT_SORT, column));
  const changeScanLimit = (limit: number) => {
    setScanLimit(limit);
    resetPaging();
  };
  const next = () => {
    const data = entries.data;
    if (!data?.hasNext) {
      return;
    }
    if (data.mode === 'ALL') {
      setCursors((current) => [...current.slice(0, page + 1), data.nextCursor]);
    }
    setPage((current) => current + 1);
  };

  const deleteEntry = async (entry: EntryDTO) => {
    await endpoints.maps.remove(mapName, { keyRef: entry.keyRef });
    toast.success(t('browser.deleted'));
    await invalidate();
  };
  const evictAll = async () => {
    await endpoints.maps.evictAll(mapName);
    toast.success(t('browser.evicted'));
    resetPaging();
    await invalidate();
  };

  const data = entries.data;
  const cursorExpired = entries.error instanceof ApiError && entries.error.code === 'INVALID_CURSOR';

  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <div className="flex flex-wrap items-start gap-3 border-b border-gk-border px-4 py-3">
        <div className="min-w-0 flex-1">
          <SearchBar applied={search} onApply={applySearch} />
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <button
            type="button"
            className="gk-btn-ghost"
            onClick={() => void entries.refetch()}
            title={t('browser.refresh')}
            aria-label={t('browser.refresh')}
          >
            <RefreshCw className={`h-4 w-4 ${entries.isFetching ? 'animate-spin' : ''}`} aria-hidden />
          </button>
          {toolbarActions}
          <button type="button" className="gk-btn-secondary" onClick={() => setEvictOpen(true)}>
            <Wind className="h-4 w-4" aria-hidden />
            {t('browser.evictAll')}
          </button>
        </div>
      </div>

      <SortToolbar
        sort={sort}
        onSortChange={changeSort}
        scanLimit={scanLimit}
        onScanLimitChange={changeScanLimit}
        page={entries.isError ? undefined : data}
        disabled={search.mode === 'key'}
      />

      {data && !entries.isError && <MissingSchemasBanner entries={data.entries} />}

      <div className="min-h-0 flex-1 overflow-y-auto">
        {entries.isPending && <CenteredSpinner />}
        {entries.isError && (
          <div className="p-4">
            <ErrorMessage error={entries.error} onRetry={cursorExpired ? resetPaging : () => void entries.refetch()} />
          </div>
        )}
        {data && !entries.isError && data.entries.length === 0 && (
          <div className="flex flex-col items-center gap-2 py-16 text-gk-muted">
            <Inbox className="h-8 w-8 text-gk-subtle" aria-hidden />
            {search.mode === 'all' && page === 0 ? t('browser.empty') : search.mode === 'key' ? t('browser.keyNotFound') : t('browser.noResults')}
          </div>
        )}
        {data && !entries.isError && data.entries.length > 0 && (
          <EntryTable
            entries={data.entries}
            onDelete={setToDelete}
            onOpen={onOpenEntry}
            sort={sorted ? sort : DEFAULT_SORT}
            onSort={search.mode === 'key' ? undefined : sortByColumn}
            maxHits={data.mode === 'SORTED' ? data.maxHits : undefined}
          />
        )}
      </div>

      {data && search.mode !== 'key' && (
        <Pagination
          page={page}
          pageSize={pageSize}
          shown={data.entries.length}
          hasNext={data.hasNext}
          total={data.total}
          onFirst={resetPaging}
          onPrevious={() => setPage((current) => Math.max(0, current - 1))}
          onNext={next}
          onPageSizeChange={changePageSize}
          busy={entries.isFetching}
        />
      )}

      <ConfirmDialog
        open={toDelete !== null}
        title={t('browser.delete')}
        message={
          <>
            {t('browser.deleteConfirm')}
            {toDelete && <span className="gk-mono mt-2 block truncate text-gk-cyan">{toDelete.key.preview ?? toDelete.keyRef}</span>}
          </>
        }
        confirmLabel={t('common.delete')}
        danger
        onConfirm={() => (toDelete ? deleteEntry(toDelete) : Promise.resolve())}
        onClose={() => setToDelete(null)}
      />
      <ConfirmDialog
        open={evictOpen}
        title={t('browser.evictConfirmTitle')}
        message={t('browser.evictConfirmText', { name: mapName })}
        confirmLabel={t('browser.evictAll')}
        onConfirm={evictAll}
        onClose={() => setEvictOpen(false)}
      />
    </div>
  );
}
