import { useQueryClient } from '@tanstack/react-query';
import { useCallback } from 'react';
import { queryKeys } from '../../api/queries';

/** Refreshes everything that depends on a map's content after a modification. */
export function useInvalidateMapData(mapName: string) {
  const queryClient = useQueryClient();
  return useCallback(async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: queryKeys.mapEntries(mapName) }),
      queryClient.invalidateQueries({ queryKey: queryKeys.mapStats(mapName) }),
      queryClient.invalidateQueries({ queryKey: queryKeys.allStructures }),
      queryClient.invalidateQueries({ queryKey: queryKeys.overview }),
    ]);
  }, [queryClient, mapName]);
}
