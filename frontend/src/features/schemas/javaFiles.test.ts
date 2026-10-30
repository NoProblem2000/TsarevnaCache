import { describe, expect, it } from 'vitest';
import type { BatchImportResult } from '../../api/types';
import { groupIgnored, ignoredPreview, isJavaFile, picked, readJavaFiles, withSkipped } from './javaFiles';

const file = (name: string, content: string) => new File([content], name, { type: 'text/plain' });

describe('reading picked files', () => {
  it('reads every .java file in parallel and skips the rest', async () => {
    const files = picked([file('SomeDTO.java', 'class SomeDTO {}'), file('notes.md', '# notes'), file('Some2DTO.JAVA', 'class B {}')]);
    const { sources, skipped } = await readJavaFiles(files);
    expect(sources).toEqual([
      { fileName: 'SomeDTO.java', content: 'class SomeDTO {}' },
      { fileName: 'Some2DTO.JAVA', content: 'class B {}' },
    ]);
    expect(skipped).toEqual([{ fileName: 'notes.md', reason: 'Not a .java file' }]);
    expect(isJavaFile('model/A.java')).toBe(true);
    expect(isJavaFile('A.java.bak')).toBe(false);
  });

  it('keeps the folder path of picked files', async () => {
    const inFolder = file('Order.java', 'class Order {}');
    Object.defineProperty(inFolder, 'webkitRelativePath', { value: 'model/orders/Order.java' });
    const { sources } = await readJavaFiles(picked([inFolder]));
    expect(sources[0].fileName).toBe('model/orders/Order.java');
  });
});

describe('import report helpers', () => {
  const ignored = [
    { fileName: 'model/CustomRequest.java', className: 'CustomRequest', reason: 'Not a Hazelcast IdentifiedDataSerializable DTO' },
    { fileName: 'model/Status.java', className: 'Status', reason: 'Not a Hazelcast IdentifiedDataSerializable DTO', detail: 'enum' },
    { fileName: 'model/LegacyDTO.java', className: 'LegacyDTO', reason: 'Not registered in DataSerializableFactory' },
    { fileName: 'model/Point.java', reason: 'Not a Hazelcast IdentifiedDataSerializable DTO' },
  ];

  it('previews the first ignored class names and groups them by reason', () => {
    expect(ignoredPreview(ignored)).toBe('CustomRequest, Status, LegacyDTO, …');
    expect(ignoredPreview(ignored.slice(3))).toBe('Point');
    expect(groupIgnored(ignored).map((group) => [group.reason, group.files.length])).toEqual([
      ['Not a Hazelcast IdentifiedDataSerializable DTO', 3],
      ['Not registered in DataSerializableFactory', 1],
    ]);
  });

  it('adds the files the browser did not send to the server result', () => {
    const result: BatchImportResult = {
      totalFiles: 2,
      importedCount: 1,
      imported: [],
      updatedKeys: [],
      linkedSchemas: [],
      factoryUpdated: false,
      factoryConstantCount: 0,
      ignoredCount: 1,
      ignored: [ignored[0]],
      errors: [],
    };
    const merged = withSkipped(result, [{ fileName: 'notes.md', reason: 'Not a .java file' }]);
    expect(merged.totalFiles).toBe(3);
    expect(merged.ignoredCount).toBe(2);
    expect(merged.ignored.map((item) => item.fileName)).toEqual(['model/CustomRequest.java', 'notes.md']);
    expect(withSkipped(result, [])).toBe(result);
  });
});
