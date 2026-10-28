import { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { Eraser, Info } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Navigate, useParams } from 'react-router-dom';
import { ApiError } from '../api/client';
import { endpoints } from '../api/endpoints';
import { queryKeys } from '../api/queries';
import { BROWSABLE_OTHER_TYPES } from '../api/types';
import { parseStructureType } from '../components/layout/structureRoutes';
import { ClearConfirmModal } from '../components/ui/ClearConfirmModal';
import { ErrorMessage } from '../components/ui/ErrorMessage';
import { useToast } from '../components/ui/Toast';
import { OtherStructureView } from '../features/structures/OtherStructureView';
import { StructureHeader } from './StructureHeader';
import { useStructureInfo } from './useStructureInfo';

/** Workspace of a non-map distributed object. */
export function StructurePage() {
  const { t } = useTranslation();
  const toast = useToast();
  const queryClient = useQueryClient();
  const params = useParams();
  const type = parseStructureType(params.type);
  const name = decodeURIComponent(params.name ?? '');
  const { info, exists } = useStructureInfo(type ?? 'OTHER', name);
  const [clearOpen, setClearOpen] = useState(false);

  if (!type) {
    return <Navigate to="/not-found" replace />;
  }
  if (type === 'MAP') {
    return <Navigate to={`/maps/${encodeURIComponent(name)}`} replace />;
  }
  const typePath = BROWSABLE_OTHER_TYPES[type];

  const clear = async () => {
    if (!typePath) {
      return;
    }
    await endpoints.other.clear(typePath, name, name);
    toast.success(t('other.cleared', { name }));
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: queryKeys.other(typePath, name) }),
      queryClient.invalidateQueries({ queryKey: queryKeys.allStructures }),
    ]);
  };

  return (
    <div className="flex h-full flex-col">
      <StructureHeader type={type} name={name} info={info} />
      {exists === false && (
        <div className="p-6">
          <ErrorMessage error={new ApiError(404, 'STRUCTURE_NOT_FOUND', name)} showDetails={false} />
        </div>
      )}
      {exists !== false && typePath && (
        <OtherStructureView
          key={`${type}:${name}`}
          type={type}
          typePath={typePath}
          name={name}
          toolbarActions={
            <button type="button" className="gk-btn-danger" onClick={() => setClearOpen(true)}>
              <Eraser className="h-4 w-4" aria-hidden />
              {t('other.clear')}
            </button>
          }
        />
      )}
      {exists && !typePath && (
        <div className="p-6">
          <div className="flex items-start gap-2 rounded-lg border border-gk-border bg-gk-panel p-4 text-gk-muted">
            <Info className="mt-0.5 h-4 w-4 shrink-0 text-gk-cyan" aria-hidden />
            {t('other.noBrowser')}
          </div>
        </div>
      )}
      <ClearConfirmModal
        open={clearOpen}
        name={name}
        title={t('clearModal.titleStructure', { type: t(`types.singular.${type}`) })}
        onConfirm={clear}
        onClose={() => setClearOpen(false)}
      />
    </div>
  );
}
