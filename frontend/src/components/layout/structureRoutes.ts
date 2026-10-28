import type { StructureType } from '../../api/types';

/** URL of the workspace of a distributed object. */
export function structureRoute(type: StructureType, name: string): string {
  return type === 'MAP'
    ? `/maps/${encodeURIComponent(name)}`
    : `/structures/${type.toLowerCase()}/${encodeURIComponent(name)}`;
}

/** Parses the `:type` segment of `/structures/:type/:name`. */
export function parseStructureType(segment: string | undefined): StructureType | null {
  const upper = segment?.toUpperCase();
  const known: StructureType[] = [
    'MAP', 'REPLICATED_MAP', 'MULTI_MAP', 'QUEUE', 'SET', 'LIST', 'TOPIC', 'RELIABLE_TOPIC',
    'FLAKE_ID_GENERATOR', 'OTHER',
  ];
  return known.find((type) => type === upper) ?? null;
}
