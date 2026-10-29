import { FileCode2, PackageX, PenLine } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import type { EntryDTO } from '../../api/types';
import {
  configureSchemaRoute,
  dtoLabel,
  dtoProblem,
  editSchemaRoute,
  missingDtoTarget,
  schemaKey,
  type DtoProblem,
  type DtoRoot,
  type DtoTarget,
} from '../schemas/schemaLinks';

export interface MissingTarget {
  target: DtoTarget;
  count: number;
  /** Registered root DTOs that cannot be read only because of this nested class. */
  roots: DtoRoot[];
}

export interface FieldErrorTarget {
  problem: Extract<DtoProblem, { kind: 'field' }>;
  count: number;
}

/**
 * Distinct DTO classes without a schema among the keys and values of a page, keyed strictly by
 * `factoryId:classId` of the class that is really missing (a nested one when the root is registered).
 */
export function missingTargets(entries: EntryDTO[]): MissingTarget[] {
  const found = new Map<string, MissingTarget>();
  for (const entry of entries) {
    for (const value of [entry.key, entry.value]) {
      const problem = dtoProblem(value.unresolved);
      const target = problem?.kind === 'missing' ? problem.target : problem ? undefined : missingDtoTarget(value.unresolved);
      if (!target) {
        continue;
      }
      const id = schemaKey(target.factoryId, target.classId);
      const current = found.get(id) ?? { target, count: 0, roots: [] };
      const root = problem?.kind === 'missing' && problem.nested ? problem.root : undefined;
      found.set(id, {
        target: current.target.className ? current.target : target,
        count: current.count + 1,
        roots:
          root && !current.roots.some((known) => schemaKey(known.factoryId, known.classId) === schemaKey(root.factoryId, root.classId))
            ? [...current.roots, root]
            : current.roots,
      });
    }
  }
  return [...found.values()];
}

/** Distinct field layout errors (class + field) of a page. */
export function fieldErrorTargets(entries: EntryDTO[]): FieldErrorTarget[] {
  const found = new Map<string, FieldErrorTarget>();
  for (const entry of entries) {
    for (const value of [entry.key, entry.value]) {
      const problem = dtoProblem(value.unresolved);
      if (problem?.kind !== 'field') {
        continue;
      }
      const id = `${schemaKey(problem.schema.factoryId, problem.schema.classId)}#${problem.fieldOrder}`;
      found.set(id, { problem, count: (found.get(id)?.count ?? 0) + 1 });
    }
  }
  return [...found.values()];
}

/**
 * Banner above the entry table: classes without a schema ("Configure Missing DTO" for exactly the
 * missing class, nested or not) and schemas that do not match the stored bytes ("Edit Schema").
 */
export function MissingSchemasBanner({ entries }: { entries: EntryDTO[] }) {
  const { t } = useTranslation();
  const missing = missingTargets(entries);
  const fieldErrors = fieldErrorTargets(entries);
  if (missing.length === 0 && fieldErrors.length === 0) {
    return null;
  }
  const missingTotal = missing.reduce((sum, item) => sum + item.count, 0);
  const errorTotal = fieldErrors.reduce((sum, item) => sum + item.count, 0);
  return (
    <div className="space-y-2 border-b border-gk-warning/30 bg-gk-warning/5 px-4 py-2.5">
      {missing.length > 0 && (
        <div className="flex flex-wrap items-center gap-3">
          <PackageX className="h-4 w-4 shrink-0 text-gk-warning" aria-hidden />
          <p className="min-w-0 flex-1 text-xs text-gk-text">{t('browser.missingSchemas', { count: missingTotal })}</p>
          <div className="flex flex-wrap gap-2">
            {missing.map(({ target, roots }) => (
              <Link
                key={schemaKey(target.factoryId, target.classId)}
                to={configureSchemaRoute(target)}
                className="gk-btn-secondary border-gk-cyan/40 px-2.5 py-1 text-xs text-gk-cyan hover:bg-gk-cyan/10"
                title={
                  roots.length > 0
                    ? t('dtoProblem.nestedIn', { roots: roots.map((root) => dtoLabel(root)).join(', '), missing: dtoLabel(target) })
                    : undefined
                }
              >
                <FileCode2 className="h-3.5 w-3.5" aria-hidden />
                {target.className ? `${target.className} · ` : ''}
                {t('dtoProblem.configureMissing', { classId: target.classId })}
                {roots.length > 0 && <span className="text-gk-subtle">({t('dtoProblem.nestedBadge')})</span>}
              </Link>
            ))}
          </div>
        </div>
      )}
      {fieldErrors.length > 0 && (
        <div className="flex flex-wrap items-center gap-3">
          <PenLine className="h-4 w-4 shrink-0 text-gk-danger" aria-hidden />
          <p className="min-w-0 flex-1 text-xs text-gk-text">{t('browser.fieldErrors', { count: errorTotal })}</p>
          <div className="flex flex-wrap gap-2">
            {fieldErrors.map(({ problem }) => (
              <Link
                key={`${schemaKey(problem.schema.factoryId, problem.schema.classId)}#${problem.fieldOrder}`}
                to={editSchemaRoute(problem.schema.factoryId, problem.schema.classId)}
                className="gk-btn-secondary border-gk-danger/40 px-2.5 py-1 text-xs text-gk-text hover:bg-gk-danger/10"
                title={problem.fieldError}
              >
                <PenLine className="h-3.5 w-3.5" aria-hidden />
                {problem.schema.className ? `${problem.schema.className} #${problem.fieldOrder} ${problem.fieldName} · ` : ''}
                {t('dtoProblem.editSchema', { classId: problem.schema.classId })}
              </Link>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
