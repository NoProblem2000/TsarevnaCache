/// <reference types="node" />
import { readFileSync } from 'node:fs';
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

/** Enum constants of a backend Java enum, read from its source so new codes cannot be forgotten. */
function javaEnumConstants(relativePath: string, constantPattern: RegExp): string[] {
  const source = readFileSync(new URL(`../../../src/main/java/io/github/noproblem2000/tsarevnacache/${relativePath}`, import.meta.url), 'utf8');
  return [...source.matchAll(constantPattern)].map((match) => match[1]);
}

const BACKEND_ERROR_CODES = javaEnumConstants('api/error/ErrorCode.java', /^\s{4}([A-Z_]+)\(HttpStatus/gm);
const DESERIALIZATION_CODES = javaEnumConstants('api/dto/DeserializationErrorCode.java', /^\s{4}([A-Z_]+)\s*[,;]?\s*$/gm);
const HEALTH_CHECK_TYPES = javaEnumConstants('healthcheck/HealthCheckType.java', /^\s{4}([A-Z_]+)\(HealthTargetType/gm);

describe('locales', () => {
  it('English and Russian define the same keys', () => {
    const english = keys(en as Tree);
    const russian = keys(ru as Tree);
    expect([...english].filter((key) => !russian.has(key))).toEqual([]);
    expect([...russian].filter((key) => !english.has(key))).toEqual([]);
  });

  it('every backend error code is translated', () => {
    expect(BACKEND_ERROR_CODES.length).toBeGreaterThan(20);
    for (const code of BACKEND_ERROR_CODES) {
      expect((en as Tree & { errors: Record<string, string> }).errors[code], code).toBeTruthy();
      expect((ru as Tree & { errors: Record<string, string> }).errors[code], code).toBeTruthy();
    }
  });

  it('every deserialization error code is translated', () => {
    expect(DESERIALIZATION_CODES.length).toBe(9);
    for (const code of DESERIALIZATION_CODES) {
      expect((en as Tree & { deserialization: Record<string, string> }).deserialization[code], code).toBeTruthy();
      expect((ru as Tree & { deserialization: Record<string, string> }).deserialization[code], code).toBeTruthy();
    }
  });

  it('every healthcheck rule has a title, description and recommendation', () => {
    expect(HEALTH_CHECK_TYPES.length).toBe(7);
    type Health = { checks: Record<string, { title: string; description: string }>; recommendations: Record<string, string> };
    for (const locale of [en, ru] as unknown as { health: Health }[]) {
      for (const type of HEALTH_CHECK_TYPES) {
        expect(locale.health.checks[type]?.title, type).toBeTruthy();
        expect(locale.health.checks[type]?.description, type).toBeTruthy();
        expect(locale.health.recommendations[type], type).toBeTruthy();
      }
      // variants emitted by HealthCheckRules
      expect(locale.health.recommendations.MAP_UNBOUNDED_NO_TTL_CRITICAL).toBeTruthy();
      expect(locale.health.recommendations.DTO_UNREGISTERED_SCHEMA_RESTART).toBeTruthy();
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
