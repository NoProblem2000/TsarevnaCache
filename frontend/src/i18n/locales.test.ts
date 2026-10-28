import { describe, expect, it } from 'vitest';
import en from './locales/en.json';
import ru from './locales/ru.json';

type Tree = { [key: string]: string | Tree };

/** Flattens nested keys; plural suffixes are reduced to their base key. */
function keys(tree: Tree, prefix = ''): Set<string> {
  const result = new Set<string>();
  for (const [key, value] of Object.entries(tree)) {
    const path = prefix ? `${prefix}.${key}` : key;
    if (typeof value === 'string') {
      result.add(path.replace(/_(zero|one|two|few|many|other)$/, ''));
    } else {
      keys(value, path).forEach((nested) => result.add(nested));
    }
  }
  return result;
}

const BACKEND_ERROR_CODES = [
  'AUTH_REQUIRED', 'AUTH_INVALID_CREDENTIALS', 'ACCESS_DENIED', 'CSRF_TOKEN_INVALID', 'VALIDATION_FAILED',
  'VALUE_REQUIRED', 'VALUE_CONVERSION_FAILED', 'INVALID_JSON', 'CLASS_NOT_FOUND', 'CLASS_NOT_ALLOWED',
  'CLASS_INSTANTIATION_FAILED', 'SCHEMA_REQUIRED', 'SCHEMA_INVALID', 'INVALID_KEY_REF', 'INVALID_CURSOR',
  'INVALID_PREDICATE', 'CONFIRMATION_REQUIRED', 'UNSUPPORTED_STRUCTURE_TYPE', 'MAP_NOT_FOUND',
  'STRUCTURE_NOT_FOUND', 'ENTRY_NOT_FOUND', 'ITEM_NOT_FOUND', 'NOT_FOUND', 'METHOD_NOT_ALLOWED',
  'CLUSTER_UNAVAILABLE', 'CLUSTER_TIMEOUT', 'INTERNAL_ERROR',
];

describe('locales', () => {
  it('English and Russian define the same keys', () => {
    const english = keys(en as Tree);
    const russian = keys(ru as Tree);
    expect([...english].filter((key) => !russian.has(key))).toEqual([]);
    expect([...russian].filter((key) => !english.has(key))).toEqual([]);
  });

  it('every backend error code is translated', () => {
    for (const code of BACKEND_ERROR_CODES) {
      expect((en as Tree & { errors: Record<string, string> }).errors[code]).toBeTruthy();
      expect((ru as Tree & { errors: Record<string, string> }).errors[code]).toBeTruthy();
    }
  });

  it('no translation is empty', () => {
    const check = (tree: Tree) => {
      for (const value of Object.values(tree)) {
        if (typeof value === 'string') {
          expect(value.trim()).not.toBe('');
        } else {
          check(value);
        }
      }
    };
    check(en as Tree);
    check(ru as Tree);
  });
});
