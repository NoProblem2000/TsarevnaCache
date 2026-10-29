import { describe, expect, it } from 'vitest';
import type { UnresolvedObjectDTO } from '../../api/types';
import { configureSchemaRoute, missingDtoTarget } from './schemaLinks';

const unresolved = (overrides: Partial<UnresolvedObjectDTO>): UnresolvedObjectDTO => ({
  deserialized: false,
  errorCode: 'DESERIALIZATION_ERROR',
  typeId: -2,
  metadata: {},
  ...overrides,
});

describe('missingDtoTarget', () => {
  it('reads the missing (nested) class of DTO_SCHEMA_MISSING', () => {
    const target = missingDtoTarget(
      unresolved({
        errorCode: 'DTO_SCHEMA_MISSING',
        missingClass: 'Some2DTO (factoryId=1, classId=2)',
        metadata: { identified: true, factoryId: 1, classId: 1, missingFactoryId: 1, missingClassId: 2 },
      }),
    );
    expect(target).toEqual({ factoryId: 1, classId: 2, className: 'Some2DTO' });
    expect(configureSchemaRoute(target!)).toBe('/schemas?configure=1&factoryId=1&classId=2&className=Some2DTO');
  });

  it('offers unregistered IdentifiedDataSerializable factories too', () => {
    expect(
      missingDtoTarget(
        unresolved({ errorCode: 'DATA_SERIALIZABLE_FACTORY_MISSING', metadata: { identified: true, factoryId: 1000, classId: 5 } }),
      ),
    ).toEqual({ factoryId: 1000, classId: 5 });
  });

  it('ignores everything else', () => {
    expect(missingDtoTarget(undefined)).toBeUndefined();
    expect(missingDtoTarget(unresolved({ errorCode: 'CLASS_NOT_FOUND' }))).toBeUndefined();
    expect(missingDtoTarget(unresolved({ errorCode: 'DATA_SERIALIZABLE_FACTORY_MISSING', metadata: { identified: false } }))).toBeUndefined();
    expect(
      missingDtoTarget(unresolved({ errorCode: 'DTO_SCHEMA_MISSING', missingClass: 'factoryId=1, classId=3', metadata: { missingFactoryId: 1, missingClassId: 3 } })),
    ).toEqual({ factoryId: 1, classId: 3, className: undefined });
  });
});
