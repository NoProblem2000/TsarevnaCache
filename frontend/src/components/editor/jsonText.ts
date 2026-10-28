/** Pretty prints a JSON value for the editor. */
export function toEditorText(value: unknown): string {
  return value === undefined ? '' : JSON.stringify(value, null, 2);
}

/** Parses editor text, reporting the parser message when it is not valid JSON. */
export function parseEditorText(text: string): { ok: true; value: unknown } | { ok: false; message: string } {
  try {
    return { ok: true, value: JSON.parse(text) };
  } catch (error) {
    return { ok: false, message: error instanceof Error ? error.message : String(error) };
  }
}
