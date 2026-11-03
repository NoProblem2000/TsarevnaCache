import type { EntryDTO, EntrySortBy, SortDirection } from '../../api/types';

/** Order of the data browser. */
export interface EntrySort {
  sortBy: EntrySortBy;
  direction: SortDirection;
}

export const DEFAULT_SORT: EntrySort = { sortBy: 'NONE', direction: 'DESC' };

/** Scan limits offered for sorted requests (the backend caps them at max-sort-scan-limit). */
export const SCAN_LIMITS = [1000, 2000, 5000] as const;
export const DEFAULT_SCAN_LIMIT = 1000;

export type SortPresetId = 'default' | 'hot' | 'cold' | 'largest';

/** Quick presets above the table. */
export const SORT_PRESETS: { id: SortPresetId; sort: EntrySort }[] = [
  { id: 'default', sort: DEFAULT_SORT },
  { id: 'hot', sort: { sortBy: 'HITS', direction: 'DESC' } },
  { id: 'cold', sort: { sortBy: 'HITS', direction: 'ASC' } },
  { id: 'largest', sort: { sortBy: 'COST', direction: 'DESC' } },
];

export const isSorted = (sort: EntrySort) => sort.sortBy !== 'NONE';

export const sameSort = (left: EntrySort, right: EntrySort) =>
  left.sortBy === right.sortBy && (left.sortBy === 'NONE' || left.direction === right.direction);

/** The preset matching the order, if any. */
export function presetOf(sort: EntrySort): SortPresetId | undefined {
  return SORT_PRESETS.find((preset) => sameSort(preset.sort, sort))?.id;
}

/** Keys start A→Z, metrics start with the largest / most recent value. */
export function defaultDirection(sortBy: EntrySortBy): SortDirection {
  return sortBy === 'KEY' ? 'ASC' : 'DESC';
}

/**
 * Click on a sortable column header: a new column starts in its default direction, the active
 * column flips its direction.
 */
export function nextSort(current: EntrySort, column: EntrySortBy): EntrySort {
  if (column === 'NONE') {
    return DEFAULT_SORT;
  }
  if (current.sortBy === column) {
    return { sortBy: column, direction: current.direction === 'DESC' ? 'ASC' : 'DESC' };
  }
  return { sortBy: column, direction: defaultDirection(column) };
}

export type HitTone = 'hot' | 'warm' | 'cool' | 'cold';

/** Hits of an entry relative to the most popular entry of the sample (or of the page). */
export interface HitIntensity {
  /** 0..1 */
  ratio: number;
  tone: HitTone;
}

export function hitIntensity(hits: number | undefined, maxHits: number | undefined): HitIntensity | undefined {
  if (hits === undefined || hits < 0 || maxHits === undefined || maxHits < 0) {
    return undefined;
  }
  const ratio = maxHits === 0 ? 0 : Math.min(1, hits / maxHits);
  const tone: HitTone = hits === 0 ? 'cold' : ratio >= 0.8 ? 'hot' : ratio >= 0.4 ? 'warm' : 'cool';
  return { ratio, tone };
}

/** Highest hit count among the entries; undefined when no entry reports hits. */
export function pageMaxHits(entries: EntryDTO[]): number | undefined {
  let max: number | undefined;
  for (const entry of entries) {
    const hits = entry.metadata?.hits;
    if (hits !== undefined && hits >= 0 && (max === undefined || hits > max)) {
      max = hits;
    }
  }
  return max;
}
