import { Info } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Navigate, useParams } from 'react-router-dom';
import { ApiError } from '../api/client';
import { BROWSABLE_OTHER_TYPES } from '../api/types';
import { parseStructureType } from '../components/layout/structureRoutes';
import { ErrorMessage } from '../components/ui/ErrorMessage';
import { OtherStructureView } from '../features/structures/OtherStructureView';
import { StructureHeader } from './StructureHeader';
import { useStructureInfo } from './useStructureInfo';

/** Workspace of a non-map distributed object. */
export function StructurePage() {
  const { t } = useTranslation();
  const params = useParams();
  const type = parseStructureType(params.type);
  const name = decodeURIComponent(params.name ?? '');
  const { info, exists } = useStructureInfo(type ?? 'OTHER', name);

  if (!type) {
    return <Navigate to="/not-found" replace />;
  }
  if (type === 'MAP') {
    return <Navigate to={`/maps/${encodeURIComponent(name)}`} replace />;
  }
  const typePath = BROWSABLE_OTHER_TYPES[type];

  return (
    <div className="flex h-full flex-col">
      <StructureHeader type={type} name={name} info={info} />
      {exists === false && (
        <div className="p-6">
          <ErrorMessage error={new ApiError(404, 'STRUCTURE_NOT_FOUND', name)} showDetails={false} />
        </div>
      )}
      {exists !== false && typePath && <OtherStructureView key={`${type}:${name}`} type={type} typePath={typePath} name={name} />}
      {exists && !typePath && (
        <div className="p-6">
          <div className="flex items-start gap-2 rounded-lg border border-gk-border bg-gk-panel p-4 text-gk-muted">
            <Info className="mt-0.5 h-4 w-4 shrink-0 text-gk-cyan" aria-hidden />
            {t('other.noBrowser')}
          </div>
        </div>
      )}
    </div>
  );
}
