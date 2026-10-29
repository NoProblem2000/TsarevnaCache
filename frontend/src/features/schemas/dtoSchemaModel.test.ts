import { describe, expect, it } from 'vitest';
import type { DtoClassSchema, DtoRegistryView } from '../../api/types';
import { emptyField, fromForm, generateTemplate, moveField, nestedSchema, toForm, validateForm } from './dtoSchemaModel';

const some2: DtoClassSchema = {
  factoryId: 1,
  classId: 2,
  className: 'Some2DTO',
  fields: [
    { order: 1, fieldName: 'value', fieldType: 'NULLABLE_LONG' },
    { order: 0, fieldName: 'label', fieldType: 'NULLABLE_STRING' },
  ],
};

const some: DtoClassSchema = {
  factoryId: 1,
  classId: 1,
  className: 'SomeDTO',
  fields: [
    { order: 0, fieldName: 'id', fieldType: 'NULLABLE_LONG' },
    { order: 1, fieldName: 'details', fieldType: 'NULLABLE_OBJECT', nestedClassId: 2, nestedClassName: 'Some2DTO' },
    { order: 2, fieldName: 'items', fieldType: 'NULLABLE_LIST', nestedClassName: 'Some2DTO' },
    { order: 3, fieldName: 'createdAt', fieldType: 'NULLABLE_DATE' },
    { order: 4, fieldName: 'amount', fieldType: 'NULLABLE_BIG_DECIMAL' },
    { order: 5, fieldName: 'self', fieldType: 'NULLABLE_OBJECT', nestedClassName: 'SomeDTO' },
    { order: 6, fieldName: 'other', fieldType: 'NULLABLE_OBJECT', nestedClassName: 'Foreign' },
    { order: 7, fieldName: 'version', fieldType: 'RAW_INT' },
    { order: 8, fieldName: 'flag', fieldType: 'RAW_BOOLEAN' },
    { order: 9, fieldName: 'scores', fieldType: 'NULLABLE_INTEGER_LIST' },
  ],
};

const foreign: DtoClassSchema = { factoryId: 9, classId: 5, className: 'Foreign', fields: [] };

const registry: DtoRegistryView = {
  factoryId: 1,
  factoryConstants: { SOMEDTO: 1, SOME2DTO: 2 },
  factoryClasses: { SomeDTO: 1, Some2DTO: 2 },
  activeFactoryIds: [1],
  pendingFactoryIds: [9],
  libraryFactories: {},
  file: './config/dto-schemas.json',
  schemas: [some, some2, foreign],
};

describe('generateTemplate', () => {
  it('fills every field and nests DTOs with markers', () => {
    const template = generateTemplate(some, registry, new Date(Date.UTC(2024, 0, 2, 3, 4, 5)));
    expect(template).toEqual({
      id: 0,
      details: { '@classId': 2, label: '', value: 0 },
      items: [{ '@classId': 2, label: '', value: 0 }],
      createdAt: '2024-01-02T03:04:05.000Z',
      amount: '0',
      self: null,
      other: { '@classId': 5, '@factoryId': 9 },
      version: 0,
      flag: false,
      scores: [0],
    });
    expect(Object.keys(template)[0]).toBe('id');
  });

  it('keeps the field order of the schema', () => {
    expect(Object.keys(generateTemplate(some2, registry))).toEqual(['label', 'value']);
  });
});

describe('schema form', () => {
  it('round trips a schema with the row order as byte order', () => {
    const form = toForm(some2);
    expect(form.fields.map((field) => field.fieldName)).toEqual(['label', 'value']);
    const moved = { ...form, fields: moveField(form.fields, 1, -1) };
    const schema = fromForm(moved, registry);
    expect(schema.fields.map((field) => [field.order, field.fieldName])).toEqual([
      [0, 'value'],
      [1, 'label'],
    ]);
  });

  it('resolves nested class ids and drops nested names of scalar fields', () => {
    const form = toForm(some);
    form.fields[0].nestedClassName = 'Some2DTO';
    const schema = fromForm(form, registry);
    expect(schema.fields[0].nestedClassName).toBeNull();
    expect(schema.fields[2]).toMatchObject({ nestedClassName: 'Some2DTO', nestedClassId: 2 });
    expect(schema.fields[6]).toMatchObject({ nestedClassName: 'Foreign', nestedClassId: null });
  });

  it('ignores moves outside the list', () => {
    const fields = [emptyField(), emptyField()];
    expect(moveField(fields, 0, -1)).toBe(fields);
    expect(moveField(fields, 1, 1)).toBe(fields);
  });

  it('reports invalid ids, names and duplicate fields', () => {
    const form = toForm(some2);
    form.classId = 'x';
    form.className = 'Some DTO';
    form.fields[1].fieldName = 'label';
    form.fields.push({ ...emptyField(), fieldName: '@classId' });
    expect(validateForm(form)).toEqual([
      { kind: 'classId' },
      { kind: 'className' },
      { kind: 'duplicate', index: 1, name: 'label' },
      { kind: 'fieldName', index: 2 },
    ]);
    expect(validateForm(toForm(some))).toEqual([]);
  });

  it('finds nested schemas by id or by name', () => {
    expect(nestedSchema(registry, 1, { nestedClassId: 2 })?.className).toBe('Some2DTO');
    expect(nestedSchema(registry, 1, { nestedClassName: 'Some2DTO' })?.classId).toBe(2);
    expect(nestedSchema(registry, 1, { nestedClassName: 'Missing' })).toBeUndefined();
  });
});
