import { describe, expect, it } from 'vitest';
import type { EntryDTO } from '../../api/types';
import { DEFAULT_SORT, hitIntensity, nextSort, pageMaxHits, presetOf } from './entrySort';

describe('entry sorting', () => {
  it('starts a new column in its default direction and flips the active one', () => {
    expect(nextSort(DEFAULT_SORT, 'HITS')).toEqual({ sortBy: 'HITS', direction: 'DESC' });
    expect(nextSort({ sortBy: 'HITS', direction: 'DESC' }, 'HITS')).toEqual({ sortBy: 'HITS', direction: 'ASC' });
    expect(nextSort({ sortBy: 'HITS', direction: 'ASC' }, 'KEY')).toEqual({ sortBy: 'KEY', direction: 'ASC' });
    expect(nextSort({ sortBy: 'COST', direction: 'DESC' }, 'NONE')).toEqual(DEFAULT_SORT);
  });

  it('recognizes the quick presets', () => {
    expect(presetOf(DEFAULT_SORT)).toBe('default');
    expect(presetOf({ sortBy: 'NONE', direction: 'ASC' })).toBe('default');
    expect(presetOf({ sortBy: 'HITS', direction: 'DESC' })).toBe('hot');
    expect(presetOf({ sortBy: 'HITS', direction: 'ASC' })).toBe('cold');
    expect(presetOf({ sortBy: 'COST', direction: 'DESC' })).toBe('largest');
    expect(presetOf({ sortBy: 'LAST_ACCESS', direction: 'DESC' })).toBeUndefined();
  });

  it('rates hits relative to the most popular entry', () => {
    expect(hitIntensity(100, 100)).toEqual({ ratio: 1, tone: 'hot' });
    expect(hitIntensity(50, 100)?.tone).toBe('warm');
    expect(hitIntensity(5, 100)?.tone).toBe('cool');
    expect(hitIntensity(0, 100)).toEqual({ ratio: 0, tone: 'cold' });
    expect(hitIntensity(0, 0)).toEqual({ ratio: 0, tone: 'cold' });
    expect(hitIntensity(undefined, 100)).toBeUndefined();
    expect(hitIntensity(3, undefined)).toBeUndefined();
  });

  it('finds the highest hit count of a page', () => {
    const entry = (hits?: number) => ({ keyRef: String(hits), metadata: hits === undefined ? {} : { hits } }) as EntryDTO;
    expect(pageMaxHits([entry(3), entry(9), entry(undefined)])).toBe(9);
    expect(pageMaxHits([entry(undefined)])).toBeUndefined();
  });
});
