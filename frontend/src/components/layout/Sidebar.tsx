import { useMemo, useState } from 'react';
import { ChevronDown, ChevronRight, FileCode2, HeartPulse, LayoutDashboard, RefreshCw, Search } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { NavLink } from 'react-router-dom';
import { useDtoSchemas, useHealthReport, useStructures } from '../../api/queries';
import { STRUCTURE_TYPES, type StructureDTO, type StructureType } from '../../api/types';
import { formatBytes, formatNumber } from '../../lib/format';
import { ErrorMessage } from '../ui/ErrorMessage';
import { Spinner } from '../ui/Spinner';
import { readShowInternal, writeShowInternal } from './sidebarPrefs';
import { STRUCTURE_ICONS } from './structureIcons';
import { structureRoute } from './structureRoutes';


/** Left navigation: overview link plus the filterable tree of distributed objects. */
export function Sidebar() {
  const { t, i18n } = useTranslation();
  const [filter, setFilter] = useState('');
  const [showInternal, setShowInternal] = useState(readShowInternal);
  const [collapsed, setCollapsed] = useState<Partial<Record<StructureType, boolean>>>({});
  const structures = useStructures(showInternal);
  const schemas = useDtoSchemas();
  const health = useHealthReport();
  const attention = health.data ? health.data.counts.critical + health.data.counts.warning : 0;

  const groups = useMemo(() => {
    const needle = filter.trim().toLowerCase();
    const result: [StructureType, StructureDTO[]][] = [];
    for (const type of STRUCTURE_TYPES) {
      const items = (structures.data?.groups[type] ?? []).filter(
        (item) => !needle || item.name.toLowerCase().includes(needle),
      );
      if (items.length > 0) {
        result.push([type, items]);
      }
    }
    return result;
  }, [structures.data, filter]);

  const toggleInternal = (value: boolean) => {
    setShowInternal(value);
    writeShowInternal(value);
  };

  return (
    <aside className="flex w-72 shrink-0 flex-col border-r border-gk-border bg-gk-panel">
      <nav className="border-b border-gk-border p-2">
        <NavLink
          to="/"
          end
          className={({ isActive }) =>
            `flex items-center gap-2 rounded-md px-2.5 py-2 transition-colors ${
              isActive ? 'bg-gk-cyan/10 text-gk-cyan' : 'text-gk-text hover:bg-gk-hover'
            }`
          }
        >
          <LayoutDashboard className="h-4 w-4" aria-hidden />
          {t('sidebar.overview')}
        </NavLink>
        <NavLink
          to="/healthcheck"
          className={({ isActive }) =>
            `mt-0.5 flex items-center gap-2 rounded-md px-2.5 py-2 transition-colors ${
              isActive ? 'bg-gk-cyan/10 text-gk-cyan' : 'text-gk-text hover:bg-gk-hover'
            }`
          }
        >
          <HeartPulse className="h-4 w-4" aria-hidden />
          <span className="flex-1">{t('sidebar.healthcheck')}</span>
          {health.data && attention > 0 && (
            <span
              className={`gk-badge ${health.data.counts.critical > 0 ? 'bg-gk-danger/20 text-gk-danger' : 'bg-gk-warning/20 text-gk-warning'}`}
              title={t('sidebar.healthcheckCount', { count: attention })}
            >
              {attention}
            </span>
          )}
        </NavLink>
        <NavLink
          to="/schemas"
          className={({ isActive }) =>
            `mt-0.5 flex items-center gap-2 rounded-md px-2.5 py-2 transition-colors ${
              isActive ? 'bg-gk-cyan/10 text-gk-cyan' : 'text-gk-text hover:bg-gk-hover'
            }`
          }
          title={t('sidebar.schemasHint')}
        >
          <FileCode2 className="h-4 w-4" aria-hidden />
          <span className="flex-1">{t('sidebar.schemas')}</span>
          {schemas.data && (
            <span className="gk-badge bg-gk-purple/20 text-gk-purple" title={t('sidebar.schemasCount', { count: schemas.data.schemas.length })}>
              {schemas.data.schemas.length}
            </span>
          )}
        </NavLink>
      </nav>

      <div className="space-y-2 border-b border-gk-border p-3">
        <div className="flex items-center justify-between">
          <span className="text-xs font-semibold uppercase tracking-wide text-gk-muted">{t('sidebar.structures')}</span>
          <button
            type="button"
            className="rounded p-1 text-gk-muted hover:bg-gk-hover hover:text-gk-cyan"
            onClick={() => void structures.refetch()}
            title={t('sidebar.refresh')}
            aria-label={t('sidebar.refresh')}
          >
            <RefreshCw className={`h-3.5 w-3.5 ${structures.isFetching ? 'animate-spin' : ''}`} aria-hidden />
          </button>
        </div>
        <label className="relative block">
          <Search className="pointer-events-none absolute left-2.5 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-gk-subtle" aria-hidden />
          <input
            type="search"
            value={filter}
            onChange={(event) => setFilter(event.target.value)}
            placeholder={t('sidebar.filterPlaceholder')}
            aria-label={t('sidebar.filterPlaceholder')}
            className="gk-input py-1.5 pl-8 text-xs"
          />
        </label>
        <label className="flex cursor-pointer items-center gap-2 text-xs text-gk-muted">
          <input
            type="checkbox"
            checked={showInternal}
            onChange={(event) => toggleInternal(event.target.checked)}
            className="accent-gk-cyan"
          />
          {t('sidebar.showInternal')}
        </label>
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto p-2">
        {structures.isPending && <Spinner className="p-2" />}
        {structures.isError && <ErrorMessage error={structures.error} onRetry={() => void structures.refetch()} showDetails={false} />}
        {structures.isSuccess && groups.length === 0 && (
          <p className="p-2 text-xs text-gk-muted">
            {filter ? t('sidebar.noMatches', { filter }) : t('sidebar.empty')}
          </p>
        )}
        {groups.map(([type, items]) => {
          const Icon = STRUCTURE_ICONS[type];
          const isCollapsed = collapsed[type] ?? false;
          return (
            <section key={type} className="mb-1">
              <button
                type="button"
                className="flex w-full items-center gap-1.5 rounded px-1.5 py-1 text-xs font-semibold uppercase tracking-wide text-gk-muted hover:text-gk-text"
                onClick={() => setCollapsed((current) => ({ ...current, [type]: !isCollapsed }))}
                aria-expanded={!isCollapsed}
              >
                {isCollapsed ? <ChevronRight className="h-3.5 w-3.5" aria-hidden /> : <ChevronDown className="h-3.5 w-3.5" aria-hidden />}
                <span className="flex-1 text-left">{t(`types.plural.${type}`)}</span>
                <span className="gk-badge bg-gk-purple/20 text-gk-purple">{items.length}</span>
              </button>
              {!isCollapsed && (
                <ul className="mt-0.5 space-y-px">
                  {items.map((item) => (
                    <li key={`${type}:${item.name}`}>
                      <NavLink
                        to={structureRoute(type, item.name)}
                        className={({ isActive }) =>
                          `group flex items-center gap-2 rounded-md py-1.5 pl-6 pr-2 transition-colors ${
                            isActive
                              ? 'bg-gk-cyan/10 text-gk-cyan shadow-[inset_2px_0_0_#00E5FF]'
                              : 'text-gk-text hover:bg-gk-hover'
                          }`
                        }
                      >
                        <Icon className="h-3.5 w-3.5 shrink-0 text-gk-subtle group-hover:text-gk-muted" aria-hidden />
                        <span className={`gk-mono min-w-0 flex-1 truncate ${item.internal ? 'italic text-gk-muted' : ''}`} title={item.name}>
                          {item.name}
                        </span>
                        {item.size !== undefined && (
                          <span
                            className="gk-badge bg-gk-bg text-gk-muted"
                            title={t('sidebar.entriesTooltip', { count: item.size })}
                          >
                            {formatNumber(item.size, i18n.language)}
                          </span>
                        )}
                        {item.ownedEntryMemoryCost !== undefined && item.ownedEntryMemoryCost > 0 && (
                          <span
                            className="gk-badge bg-gk-purple/15 text-gk-purple"
                            title={t('sidebar.memoryTooltip', { size: formatBytes(item.ownedEntryMemoryCost) })}
                          >
                            {formatBytes(item.ownedEntryMemoryCost)}
                          </span>
                        )}
                      </NavLink>
                    </li>
                  ))}
                </ul>
              )}
            </section>
          );
        })}
      </div>
    </aside>
  );
}
