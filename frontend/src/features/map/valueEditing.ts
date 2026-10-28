import type { DataType, InspectedValue, JsonValue } from '../../api/types';
import { parseEditorText, toEditorText } from '../../components/editor/jsonText';

/** Types edited in the JSON editor; all others use a single-line text input. */
export const JSON_EDITED_TYPES: DataType[] = ['JSON', 'CLASS', 'COMPACT'];

export function isJsonEdited(type: DataType | undefined): boolean {
  return type !== undefined && JSON_EDITED_TYPES.includes(type);
}

/** Initial editor text for an inspected value. */
export function initialEditorText(value: InspectedValue): string {
  if (isJsonEdited(value.dataType)) {
    return toEditorText(value.value);
  }
  if (value.dataType === 'STRING' && typeof value.value === 'string') {
    return value.value;
  }
  // scalars: the server side preview is the exact textual form (no JS number precision loss)
  return value.preview ?? (value.value === undefined ? '' : String(value.value));
}

/**
 * Converts editor text into the request value: parsed JSON for JSON-like types, raw text for
 * scalars (the backend converts and range-checks it).
 */
export function editorTextToValue(text: string, type: DataType): { ok: true; value: JsonValue } | { ok: false; message: string } {
  if (isJsonEdited(type)) {
    const parsed = parseEditorText(text);
    return parsed.ok ? { ok: true, value: parsed.value as JsonValue } : parsed;
  }
  return { ok: true, value: text };
}

/** Parses a TTL input: empty → map default (undefined), otherwise a non-negative integer. */
export function parseTtl(text: string): { ok: true; value: number | undefined } | { ok: false } {
  const trimmed = text.trim();
  if (trimmed === '') {
    return { ok: true, value: undefined };
  }
  if (!/^\d+$/.test(trimmed)) {
    return { ok: false };
  }
  return { ok: true, value: Number(trimmed) };
}
