import { FileCode2, PackageX } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import type { EntryDTO } from '../../api/types';
import { configureSchemaRoute, missingDtoTarget, type DtoTarget } from '../schemas/schemaLinks';

/** Distinct DTO classes without a schema among the keys and values of a page. */
export function missingTargets(entries: EntryDTO[]): { target: DtoTarget; count: number }[] {
  const found = new Map<string, { target: DtoTarget; count: number }>();
  for (const entry of entries) {
    for (const value of [entry.key, entry.value]) {
      const target = missingDtoTarget(value.unresolved);
      if (target) {
        const id = `${target.factoryId}/${target.classId}`;
        const current = found.get(id);
        found.set(id, { target: current?.target.className ? current.target : target, count: (current?.count ?? 0) + 1 });
      }
    }
  }
  return [...found.values()];
}

/** "N entries use IdentifiedDataSerializable classes without a schema" with one button per class. */
export function MissingSchemasBanner({ entries }: { entries: EntryDTO[] }) {
  const { t } = useTranslation();
  const missing = missingTargets(entries);
  if (missing.length === 0) {
    return null;
  }
  const total = missing.reduce((sum, item) => sum + item.count, 0);
  return (
    <div className="flex flex-wrap items-center gap-3 border-b border-gk-warning/30 bg-gk-warning/5 px-4 py-2.5">
      <PackageX className="h-4 w-4 shrink-0 text-gk-warning" aria-hidden />
      <p className="min-w-0 flex-1 text-xs text-gk-text">{t('browser.missingSchemas', { count: total })}</p>
      <div className="flex flex-wrap gap-2">
        {missing.map(({ target }) => (
          <Link
            key={`${target.factoryId}/${target.classId}`}
            to={configureSchemaRoute(target)}
            className="gk-btn-secondary border-gk-cyan/40 px-2.5 py-1 text-xs text-gk-cyan hover:bg-gk-cyan/10"
          >
            <FileCode2 className="h-3.5 w-3.5" aria-hidden />
            {target.className ? `${target.className} · ` : ''}
            {t('inspector.configureDtoSchema', { factoryId: target.factoryId, classId: target.classId })}
          </Link>
        ))}
      </div>
    </div>
  );
}
