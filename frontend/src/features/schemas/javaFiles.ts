import type { BatchImportResult, IgnoredFile, JavaFileSource } from '../../api/types';

/** Larger files are not sent (the backend rejects sources above 1 MB as well). */
export const MAX_JAVA_FILE_BYTES = 1_000_000;

export const isJavaFile = (name: string) => name.toLowerCase().endsWith('.java');

/** A file as shown in reports: the path inside the selected folder, else the file name. */
export function fileLabel(file: File, path?: string): string {
  return path ?? (file.webkitRelativePath || file.name);
}

/** A file picked or dropped by the user, with its path inside a dropped / selected folder. */
export interface PickedFile {
  file: File;
  path: string;
}

export const picked = (files: Iterable<File>): PickedFile[] => Array.from(files, (file) => ({ file, path: fileLabel(file) }));

/**
 * Reads all `.java` files in parallel (`Promise.all` of `file.text()`). Other files and files above
 * 1 MB are not read; they are returned as `skipped` so the report can list them.
 */
export async function readJavaFiles(files: PickedFile[]): Promise<{ sources: JavaFileSource[]; skipped: IgnoredFile[] }> {
  const skipped: IgnoredFile[] = [];
  const java: PickedFile[] = [];
  for (const item of files) {
    if (!isJavaFile(item.path)) {
      skipped.push({ fileName: item.path, reason: 'Not a .java file' });
    } else if (item.file.size > MAX_JAVA_FILE_BYTES) {
      skipped.push({ fileName: item.path, reason: 'Larger than 1 MB' });
    } else {
      java.push(item);
    }
  }
  const contents = await Promise.all(java.map((item) => item.file.text()));
  return { sources: java.map((item, index) => ({ fileName: item.path, content: contents[index] })), skipped };
}

/** The server result plus the files the browser did not send. */
export function withSkipped(result: BatchImportResult, skipped: IgnoredFile[]): BatchImportResult {
  if (skipped.length === 0) {
    return result;
  }
  return {
    ...result,
    totalFiles: result.totalFiles + skipped.length,
    ignoredCount: result.ignoredCount + skipped.length,
    ignored: [...result.ignored, ...skipped],
  };
}

/** `CustomRequest, Status, Point, …` — the first names of the ignored files for the collapsed summary. */
export function ignoredPreview(ignored: IgnoredFile[], max = 3): string {
  const names = ignored.map((item) => item.className ?? baseName(item.fileName));
  return names.length > max ? `${names.slice(0, max).join(', ')}, …` : names.join(', ');
}

const baseName = (path: string) => path.split(/[\\/]/).pop()?.replace(/\.java$/i, '') ?? path;

/** Ignored files grouped by reason, the largest group first. */
export function groupIgnored(ignored: IgnoredFile[]): { reason: string; files: IgnoredFile[] }[] {
  const groups = new Map<string, IgnoredFile[]>();
  for (const item of ignored) {
    groups.set(item.reason, [...(groups.get(item.reason) ?? []), item]);
  }
  return [...groups.entries()].map(([reason, files]) => ({ reason, files })).sort((a, b) => b.files.length - a.files.length);
}

// ---------------------------------------------------------------- drag and drop

interface EntryLike {
  isFile: boolean;
  isDirectory: boolean;
  fullPath: string;
  file?: (success: (file: File) => void, failure: (error: unknown) => void) => void;
  createReader?: () => { readEntries: (success: (entries: EntryLike[]) => void, failure: (error: unknown) => void) => void };
}

async function entryFiles(entry: EntryLike): Promise<PickedFile[]> {
  if (entry.isFile && entry.file) {
    const read = entry.file.bind(entry);
    const file = await new Promise<File>((resolve, reject) => read(resolve, reject));
    return [{ file, path: entry.fullPath.replace(/^\//, '') }];
  }
  if (entry.isDirectory && entry.createReader) {
    const reader = entry.createReader();
    const children: EntryLike[] = [];
    // readEntries returns the entries in chunks (about 100 per call) until an empty chunk
    for (;;) {
      const chunk = await new Promise<EntryLike[]>((resolve, reject) => reader.readEntries(resolve, reject));
      if (chunk.length === 0) {
        break;
      }
      children.push(...chunk);
    }
    return (await Promise.all(children.map(entryFiles))).flat();
  }
  return [];
}

/**
 * Files of a drop event: plain files and, recursively, the files of dropped folders (through
 * `webkitGetAsEntry`, supported by all current browsers). Falls back to `dataTransfer.files`.
 */
export async function droppedFiles(dataTransfer: DataTransfer): Promise<PickedFile[]> {
  const entries = Array.from(dataTransfer.items ?? [])
    .filter((item) => item.kind === 'file')
    .map((item) => (item as DataTransferItem & { webkitGetAsEntry?: () => EntryLike | null }).webkitGetAsEntry?.() ?? null);
  if (entries.length > 0 && entries.every((entry) => entry !== null)) {
    return (await Promise.all((entries as EntryLike[]).map(entryFiles))).flat();
  }
  return picked(Array.from(dataTransfer.files));
}
