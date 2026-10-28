import { BarChart3, Table2 } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { useParams, useSearchParams } from 'react-router-dom';
import { ApiError } from '../api/client';
import { ErrorMessage } from '../components/ui/ErrorMessage';
import { Tabs } from '../components/ui/Tabs';
import { DataBrowser } from '../features/map/DataBrowser';
import { MapStatsTab } from '../features/map/MapStatsTab';
import { StructureHeader } from './StructureHeader';
import { useStructureInfo } from './useStructureInfo';

type MapTab = 'data' | 'stats';

/** Workspace of one IMap: data browser and statistics tabs (the tab is kept in the URL). */
export function MapPage() {
  const { t } = useTranslation();
  const name = decodeURIComponent(useParams().mapName ?? '');
  const [searchParams, setSearchParams] = useSearchParams();
  const tab: MapTab = searchParams.get('tab') === 'stats' ? 'stats' : 'data';
  const { info, exists } = useStructureInfo('MAP', name);

  return (
    <div className="flex h-full flex-col">
      <StructureHeader type="MAP" name={name} info={info} />
      {exists === false ? (
        <div className="p-6">
          <ErrorMessage error={new ApiError(404, 'MAP_NOT_FOUND', t('map.notFound', { name }))} showDetails={false} />
        </div>
      ) : (
        <>
          <Tabs<MapTab>
            active={tab}
            onChange={(next) => setSearchParams(next === 'data' ? {} : { tab: next }, { replace: true })}
            tabs={[
              { id: 'data', label: t('map.tabs.data'), icon: Table2 },
              { id: 'stats', label: t('map.tabs.stats'), icon: BarChart3 },
            ]}
          />
          {tab === 'data' ? (
            <DataBrowser key={name} mapName={name} />
          ) : (
            <div className="min-h-0 flex-1 overflow-y-auto">
              <MapStatsTab mapName={name} />
            </div>
          )}
        </>
      )}
    </div>
  );
}
