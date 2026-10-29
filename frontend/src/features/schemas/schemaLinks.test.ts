import { describe, expect, it } from 'vitest';
import type { UnresolvedObjectDTO } from '../../api/types';
import { configureSchemaRoute, dtoLabel, dtoProblem, editSchemaRoute, missingDtoTarget, schemaKey, type DtoTarget } from './schemaLinks';

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

describe('dtoProblem', () => {
  it('names the nested class that is missing, not the registered root', () => {
    const problem = dtoProblem(
      unresolved({
        errorCode: 'DTO_SCHEMA_MISSING',
        missingClass: 'Some2DTO (factoryId=1, classId=2)',
        metadata: {
          identified: true,
          factoryId: 1,
          classId: 1,
          missingFactoryId: 1,
          missingClassId: 2,
          missingClassName: 'Some2DTO',
          nested: true,
          rootFactoryId: 1,
          rootClassId: 1,
          rootClassName: 'SomeDTO',
          fieldPath: 'SomeDTO.field3',
        },
      }),
    );
    expect(problem).toEqual({
      kind: 'missing',
      target: { factoryId: 1, classId: 2, className: 'Some2DTO' },
      nested: true,
      root: { factoryId: 1, classId: 1, className: 'SomeDTO' },
      fieldPath: 'SomeDTO.field3',
    });
    expect(configureSchemaRoute((problem as { target: DtoTarget }).target)).toContain('classId=2');
  });

  it('turns a field layout error into an edit of the existing schema', () => {
    const problem = dtoProblem(
      unresolved({
        errorCode: 'DTO_FIELD_READ_ERROR',
        metadata: {
          failedFactoryId: 1,
          failedClassId: 1,
          failedClassName: 'SomeDTO',
          fieldOrder: 3,
          fieldName: 'field3',
          fieldType: 'NULLABLE_STRING',
          fieldError: 'EOFException',
        },
      }),
    );
    expect(problem).toMatchObject({
      kind: 'field',
      schema: { factoryId: 1, classId: 1, className: 'SomeDTO' },
      fieldOrder: 3,
      fieldName: 'field3',
      fieldType: 'NULLABLE_STRING',
    });
    expect(editSchemaRoute(1, 1)).toBe('/schemas?edit=1/1');
  });

  it('is undefined for other failures, keys are factoryId:classId', () => {
    expect(dtoProblem(unresolved({ errorCode: 'CLASS_NOT_FOUND' }))).toBeUndefined();
    expect(
      dtoProblem(unresolved({ errorCode: 'DATA_SERIALIZABLE_FACTORY_MISSING', metadata: { identified: true, factoryId: 9, classId: 1 } })),
    ).toBeUndefined();
    expect(schemaKey(1, 12)).toBe('1:12');
    expect(dtoLabel({ factoryId: 1, classId: 2, className: 'Some2DTO' })).toBe('Some2DTO (classId: 2)');
    expect(dtoLabel({ factoryId: 4, classId: 2 }, 1)).toBe('factoryId: 4, classId: 2');
  });
});
