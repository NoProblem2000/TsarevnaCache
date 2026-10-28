import { useStructures } from '../api/queries';
import type { StructureDTO, StructureType } from '../api/types';
import { readShowInternal } from '../components/layout/sidebarPrefs';

/**
 * Registry entry (size, memory) of one structure, taken from the sidebar's structures query so
 * no extra request is needed. `exists` is undefined while loading.
 */
export function useStructureInfo(type: StructureType, name: string): { info?: StructureDTO; exists?: boolean } {
  const structures = useStructures(readShowInternal());
  if (!structures.data) {
    return {};
  }
  const info = structures.data.groups[type]?.find((item) => item.name === name);
  return { info, exists: info !== undefined };
}
