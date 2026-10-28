import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import type { StructureDTO, StructureType } from '../api/types';
import { formatBytes, formatNumber } from '../lib/format';

interface StructureHeaderProps {
  type: StructureType;
  name: string;
  /** Registry entry of the structure (size, memory), when known. */
  info?: StructureDTO;
  actions?: ReactNode;
}

/** Title bar of a structure workspace: type badge, name, size and memory. */
export function StructureHeader({ type, name, info, actions }: StructureHeaderProps) {
  const { t, i18n } = useTranslation();
  return (
    <header className="flex flex-wrap items-center gap-3 border-b border-gk-border bg-gk-panel px-6 py-4">
      <span className="gk-badge bg-gk-purple/20 py-1 text-gk-purple">{t(`types.singular.${type}`)}</span>
      <h1 className="gk-mono min-w-0 truncate text-lg font-semibold text-gk-text" title={name}>
        {name}
      </h1>
      {info?.size !== undefined && (
        <span className="gk-badge bg-gk-card py-1 text-gk-muted">
          {type === 'MAP' ? t('map.entryCount', { count: info.size }) : `${formatNumber(info.size, i18n.language)} ${t('common.items')}`}
        </span>
      )}
      {info?.ownedEntryMemoryCost !== undefined && (
        <span className="gk-badge bg-gk-card py-1 text-gk-muted">
          {t('dashboard.owned')}: {formatBytes(info.ownedEntryMemoryCost)}
          {info.backupEntryMemoryCost !== undefined && ` · ${t('dashboard.backup')}: ${formatBytes(info.backupEntryMemoryCost)}`}
        </span>
      )}
      {actions && <div className="ml-auto flex items-center gap-2">{actions}</div>}
    </header>
  );
}
