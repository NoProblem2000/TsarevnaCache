import { describe, expect, it } from 'vitest';
import type { InspectedValue } from '../../api/types';
import { editorTextToValue, initialEditorText, parseTtl } from './valueEditing';

const value = (partial: Partial<InspectedValue>): InspectedValue => ({
  deserialized: true,
  kind: 'STRING',
  truncated: false,
  ...partial,
});

describe('valueEditing', () => {
  it('pretty prints JSON-like values and keeps scalars exact', () => {
    expect(initialEditorText(value({ kind: 'JSON', dataType: 'JSON', value: { a: 1 } }))).toBe('{\n  "a": 1\n}');
    expect(initialEditorText(value({ kind: 'STRING', dataType: 'STRING', value: 'hello\nworld' }))).toBe('hello\nworld');
    expect(initialEditorText(value({ kind: 'NUMBER', dataType: 'LONG', value: 9007199254740992, preview: '9007199254740993' })))
      .toBe('9007199254740993');
  });

  it('parses JSON for JSON-like types and passes scalars as text', () => {
    expect(editorTextToValue('{"a":[1,2]}', 'CLASS')).toEqual({ ok: true, value: { a: [1, 2] } });
    expect(editorTextToValue('{bad', 'JSON').ok).toBe(false);
    expect(editorTextToValue('42', 'LONG')).toEqual({ ok: true, value: '42' });
  });

  it('parses TTL inputs', () => {
    expect(parseTtl('')).toEqual({ ok: true, value: undefined });
    expect(parseTtl(' 0 ')).toEqual({ ok: true, value: 0 });
    expect(parseTtl('3600')).toEqual({ ok: true, value: 3600 });
    expect(parseTtl('-1').ok).toBe(false);
    expect(parseTtl('1.5').ok).toBe(false);
  });
});
