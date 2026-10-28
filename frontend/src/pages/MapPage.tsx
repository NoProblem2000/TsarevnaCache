import { useTranslation } from 'react-i18next';
import { useParams } from 'react-router-dom';
import { ErrorMessage } from '../components/ui/ErrorMessage';
import { ApiError } from '../api/client';
import { StructureHeader } from './StructureHeader';
import { useStructureInfo } from './useStructureInfo';

/** Workspace of one IMap. */
export function MapPage() {
  const { t } = useTranslation();
  const name = decodeURIComponent(useParams().mapName ?? '');
  const { info, exists } = useStructureInfo('MAP', name);

  return (
    <div className="flex min-h-full flex-col">
      <StructureHeader type="MAP" name={name} info={info} />
      <div className="flex-1 p-6">
        {exists === false && (
          <ErrorMessage error={new ApiError(404, 'MAP_NOT_FOUND', t('map.notFound', { name }))} />
        )}
      </div>
    </div>
  );
}
