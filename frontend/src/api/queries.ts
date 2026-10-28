import { useQuery } from '@tanstack/react-query';
import { endpoints } from './endpoints';

/** React Query keys, shared so mutations can invalidate the right caches. */
export const queryKeys = {
  overview: ['cluster', 'overview'] as const,
  structures: (includeInternal: boolean) => ['cluster', 'structures', includeInternal] as const,
  allStructures: ['cluster', 'structures'] as const,
  mapStats: (mapName: string) => ['maps', mapName, 'stats'] as const,
  mapEntries: (mapName: string) => ['maps', mapName, 'entries'] as const,
  mapEntry: (mapName: string, keyRef: string) => ['maps', mapName, 'entry', keyRef] as const,
  other: (typePath: string, name: string) => ['other', typePath, name] as const,
};

/** Cluster overview, refreshed every 10 seconds (drives the Online/Offline indicator). */
export function useClusterOverview() {
  return useQuery({
    queryKey: queryKeys.overview,
    queryFn: endpoints.cluster.overview,
    refetchInterval: 10_000,
  });
}

/** Aggregated LocalMapStats of one map, refreshed every 5 seconds while visible. */
export function useMapStats(mapName: string) {
  return useQuery({
    queryKey: queryKeys.mapStats(mapName),
    queryFn: () => endpoints.maps.stats(mapName),
    refetchInterval: 5_000,
  });
}

/** Distributed objects for the sidebar, refreshed every 15 seconds. */
export function useStructures(includeInternal: boolean) {
  return useQuery({
    queryKey: queryKeys.structures(includeInternal),
    queryFn: () => endpoints.cluster.structures(includeInternal),
    refetchInterval: 15_000,
  });
}
