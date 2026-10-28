import { useState } from 'react';
import { BarChart3, Eraser, Plus, Table2 } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { useParams, useSearchParams } from 'react-router-dom';
import { ApiError } from '../api/client';
import { endpoints } from '../api/endpoints';
import { ClearConfirmModal } from '../components/ui/ClearConfirmModal';
import { ErrorMessage } from '../components/ui/ErrorMessage';
import { Tabs } from '../components/ui/Tabs';
import { useToast } from '../components/ui/Toast';
import { DataBrowser } from '../features/map/DataBrowser';
import { EntryInspector } from '../features/map/EntryInspector';
import { MapStatsTab } from '../features/map/MapStatsTab';
import { PutEntryModal } from '../features/map/PutEntryModal';
import { useInvalidateMapData } from '../features/map/useInvalidateMapData';
import { StructureHeader } from './StructureHeader';
import { useStructureInfo } from './useStructureInfo';

type MapTab = 'data' | 'stats';

/**
 * Workspace of one IMap: data browser and statistics tabs (the tab is kept in the URL), the
 * entry inspector drawer and the Put Entry / Clear Map dialogs.
 */
export function MapPage() {
  const { t } = useTranslation();
  const toast = useToast();
  const name = decodeURIComponent(useParams().mapName ?? '');
  const [searchParams, setSearchParams] = useSearchParams();
  const tab: MapTab = searchParams.get('tab') === 'stats' ? 'stats' : 'data';
  const { info, exists } = useStructureInfo('MAP', name);
  const invalidate = useInvalidateMapData(name);
  const [selectedKeyRef, setSelectedKeyRef] = useState<string | null>(null);
  const [putOpen, setPutOpen] = useState(false);
  const [clearOpen, setClearOpen] = useState(false);
  // remounts the data browser (back to page 1) after the map was cleared
  const [browserVersion, setBrowserVersion] = useState(0);

  const clearMap = async () => {
    await endpoints.maps.clear(name, name);
    toast.success(t('browser.cleared', { name }));
    setBrowserVersion((version) => version + 1);
    await invalidate();
  };

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
            <DataBrowser
              key={`${name}:${browserVersion}`}
              mapName={name}
              onOpenEntry={(entry) => setSelectedKeyRef(entry.keyRef)}
              toolbarActions={
                <>
                  <button type="button" className="gk-btn-primary" onClick={() => setPutOpen(true)}>
                    <Plus className="h-4 w-4" aria-hidden />
                    {t('browser.addEntry')}
                  </button>
                  <button type="button" className="gk-btn-danger" onClick={() => setClearOpen(true)}>
                    <Eraser className="h-4 w-4" aria-hidden />
                    {t('browser.clearMap')}
                  </button>
                </>
              }
            />
          ) : (
            <div className="min-h-0 flex-1 overflow-y-auto">
              <MapStatsTab mapName={name} />
            </div>
          )}
        </>
      )}

      <EntryInspector mapName={name} keyRef={selectedKeyRef} onClose={() => setSelectedKeyRef(null)} />
      <PutEntryModal mapName={name} open={putOpen} onClose={() => setPutOpen(false)} onSaved={(entry) => setSelectedKeyRef(entry.keyRef)} />
      <ClearConfirmModal open={clearOpen} name={name} title={t('clearModal.title')} onConfirm={clearMap} onClose={() => setClearOpen(false)} />
    </div>
  );
}
