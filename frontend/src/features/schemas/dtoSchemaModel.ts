import type { DtoClassSchema, DtoFieldType, DtoRegistryView, JsonValue } from '../../api/types';

interface FieldTypeInfo {
  /** Short display name, e.g. `NullableLong`. */
  label: string;
  /** Java type of the field. */
  javaType: string;
  /** The `readData` call this type stands for. */
  readCall: string;
  /** Values can be (or contain) nested DTOs. */
  nestable: boolean;
}

const helper = (method: string) => `SerializationUtils.${method}(in)`;

/** Display data of every serialization type (mirrors `DtoFieldType.java`). */
export const FIELD_TYPE_INFO: Record<DtoFieldType, FieldTypeInfo> = {
  NULLABLE_LONG: { label: 'NullableLong', javaType: 'Long', readCall: helper('readNullableLong'), nestable: false },
  NULLABLE_STRING: { label: 'NullableString', javaType: 'String', readCall: helper('readNullableString'), nestable: false },
  NULLABLE_DATE: { label: 'NullableDate', javaType: 'Date', readCall: helper('readNullableDate'), nestable: false },
  NULLABLE_OBJECT: { label: 'NullableObject', javaType: 'Object / DTO', readCall: helper('readNullableObject'), nestable: true },
  NULLABLE_BOOLEAN: { label: 'NullableBoolean', javaType: 'Boolean', readCall: helper('readNullableBoolean'), nestable: false },
  NULLABLE_INT: { label: 'NullableInt', javaType: 'Integer', readCall: helper('readNullableInt'), nestable: false },
  NULLABLE_DOUBLE: { label: 'NullableDouble', javaType: 'Double', readCall: helper('readNullableDouble'), nestable: false },
  NULLABLE_BIG_DECIMAL: { label: 'NullableBigDecimal', javaType: 'BigDecimal', readCall: helper('readNullableBigDecimal'), nestable: false },
  NULLABLE_LIST: { label: 'NullableList', javaType: 'List<…>', readCall: helper('readNullableList'), nestable: true },
  NULLABLE_LONG_LIST: { label: 'NullableLongList', javaType: 'List<Long>', readCall: helper('readNullableLongList'), nestable: false },
  NULLABLE_INTEGER_LIST: { label: 'NullableIntegerList', javaType: 'List<Integer>', readCall: helper('readNullableIntegerList'), nestable: false },
  RAW_INT: { label: 'int', javaType: 'int', readCall: 'in.readInt()', nestable: false },
  RAW_LONG: { label: 'long', javaType: 'long', readCall: 'in.readLong()', nestable: false },
  RAW_BOOLEAN: { label: 'boolean', javaType: 'boolean', readCall: 'in.readBoolean()', nestable: false },
  RAW_STRING: { label: 'String (raw)', javaType: 'String', readCall: 'in.readString()', nestable: false },
  RAW_BYTE_ARRAY: { label: 'byte[]', javaType: 'byte[]', readCall: 'in.readByteArray()', nestable: false },
};

export const JAVA_IDENTIFIER = /^[A-Za-z_$][\w$]*$/;

/** One row of the visual schema editor (the row order is the byte order). */
export interface EditableField {
  /** Stable React key. */
  id: string;
  fieldName: string;
  fieldType: DtoFieldType;
  nestedClassName: string;
}

export interface SchemaForm {
  factoryId: string;
  classId: string;
  className: string;
  fields: EditableField[];
  rawJavaSource?: string | null;
}

let nextFieldId = 0;
export const newFieldId = () => `field-${++nextFieldId}`;

export function emptyField(): EditableField {
  return { id: newFieldId(), fieldName: '', fieldType: 'NULLABLE_STRING', nestedClassName: '' };
}

/** Editor state of an existing (or parsed) schema. */
export function toForm(schema: DtoClassSchema): SchemaForm {
  return {
    factoryId: String(schema.factoryId),
    classId: String(schema.classId),
    className: schema.className,
    rawJavaSource: schema.rawJavaSource,
    fields: [...schema.fields]
      .sort((a, b) => a.order - b.order)
      .map((field) => ({
        id: newFieldId(),
        fieldName: field.fieldName,
        fieldType: field.fieldType,
        nestedClassName: field.nestedClassName ?? '',
      })),
  };
}

/** Moves the row at `index` by `delta` (−1 up, +1 down); out of range moves are ignored. */
export function moveField(fields: EditableField[], index: number, delta: number): EditableField[] {
  const target = index + delta;
  if (index < 0 || index >= fields.length || target < 0 || target >= fields.length) {
    return fields;
  }
  const result = [...fields];
  [result[index], result[target]] = [result[target], result[index]];
  return result;
}

/** All class names a nested field can refer to: registered schemas and the parsed factory. */
export function knownClassNames(registry: DtoRegistryView | undefined): string[] {
  if (!registry) {
    return [];
  }
  const names = new Set([...registry.schemas.map((schema) => schema.className), ...Object.keys(registry.factoryClasses)]);
  return [...names].sort((a, b) => a.localeCompare(b));
}

/** Same as the backend: `SOME_DTO`, `SOMEDTO` and `SomeDTO` all match (`somedto`). */
export function normalizeName(name: string): string {
  return name.trim().toLowerCase().replace(/_/g, '');
}

/**
 * Class id of a DTO class name: a registered schema of the same factory, then the factory mapping
 * (class name, then a constant with the same normalized name). Ids are compared as numbers.
 */
export function resolveClassId(registry: DtoRegistryView | undefined, className: string, factoryId: number): number | undefined {
  if (!registry || !className) {
    return undefined;
  }
  const wanted = normalizeName(className);
  const sameFactory = registry.schemas.filter((schema) => Number(schema.factoryId) === Number(factoryId));
  const schema =
    sameFactory.find((candidate) => candidate.className === className) ??
    sameFactory.find((candidate) => normalizeName(candidate.className) === wanted);
  if (schema) {
    return Number(schema.classId);
  }
  if (Number(factoryId) !== Number(registry.factoryId)) {
    return undefined;
  }
  const byName = (names: Record<string, number>) =>
    names[className] ?? Object.entries(names).find(([name]) => normalizeName(name) === wanted)?.[1];
  const id = byName(registry.factoryClasses) ?? byName(registry.factoryConstants);
  return id === undefined ? undefined : Number(id);
}

export type FormProblem =
  | { kind: 'factoryId' }
  | { kind: 'classId' }
  | { kind: 'className' }
  | { kind: 'noFields' }
  | { kind: 'fieldName'; index: number }
  | { kind: 'duplicate'; index: number; name: string };

/** Client side validation (the backend validates again). Empty field lists are allowed. */
export function validateForm(form: SchemaForm): FormProblem[] {
  const problems: FormProblem[] = [];
  if (!/^-?\d+$/.test(form.factoryId.trim())) {
    problems.push({ kind: 'factoryId' });
  }
  if (!/^-?\d+$/.test(form.classId.trim())) {
    problems.push({ kind: 'classId' });
  }
  if (!JAVA_IDENTIFIER.test(form.className.trim())) {
    problems.push({ kind: 'className' });
  }
  const seen = new Set<string>();
  form.fields.forEach((field, index) => {
    const name = field.fieldName.trim();
    if (!JAVA_IDENTIFIER.test(name)) {
      problems.push({ kind: 'fieldName', index });
    } else if (seen.has(name)) {
      problems.push({ kind: 'duplicate', index, name });
    }
    seen.add(name);
  });
  return problems;
}

/** Schema to send to the backend; the row index becomes the field order. */
export function fromForm(form: SchemaForm, registry: DtoRegistryView | undefined): DtoClassSchema {
  const factoryId = Number(form.factoryId.trim());
  return {
    factoryId,
    classId: Number(form.classId.trim()),
    className: form.className.trim(),
    rawJavaSource: form.rawJavaSource ?? null,
    fields: form.fields.map((field, index) => {
      const nested = FIELD_TYPE_INFO[field.fieldType].nestable ? field.nestedClassName.trim() : '';
      return {
        order: index,
        fieldName: field.fieldName.trim(),
        fieldType: field.fieldType,
        nestedClassName: nested || null,
        nestedClassId: nested ? (resolveClassId(registry, nested, factoryId) ?? null) : null,
      };
    }),
  };
}

/** Schema of a nested field: by class id (same factory) or by class name. */
export function nestedSchema(
  registry: DtoRegistryView | undefined,
  factoryId: number,
  field: { nestedClassId?: number | null; nestedClassName?: string | null },
): DtoClassSchema | undefined {
  if (!registry) {
    return undefined;
  }
  if (field.nestedClassId !== undefined && field.nestedClassId !== null) {
    const byId = registry.schemas.find((schema) => Number(schema.factoryId) === Number(factoryId) && Number(schema.classId) === Number(field.nestedClassId));
    if (byId) {
      return byId;
    }
  }
  if (field.nestedClassName) {
    const classId = resolveClassId(registry, field.nestedClassName, factoryId);
    return (
      registry.schemas.find((schema) => Number(schema.factoryId) === Number(factoryId) && Number(schema.classId) === classId) ??
      registry.schemas.find((schema) => schema.className === field.nestedClassName)
    );
  }
  return undefined;
}

/**
 * JSON template with every field of a DTO, nested DTOs included (marked with `@classId`, and
 * `@factoryId` when it is not the project factory). Recursive references end with `null` / `[]`.
 */
export function generateTemplate(
  schema: DtoClassSchema,
  registry: DtoRegistryView | undefined,
  now: Date = new Date(),
): { [key: string]: JsonValue } {
  return templateOf(schema, registry, now, new Set(), true);
}

function templateOf(
  schema: DtoClassSchema,
  registry: DtoRegistryView | undefined,
  now: Date,
  path: Set<string>,
  root: boolean,
): { [key: string]: JsonValue } {
  const key = `${schema.factoryId}/${schema.classId}`;
  const nextPath = new Set(path).add(key);
  const result: { [key: string]: JsonValue } = {};
  if (!root) {
    result['@classId'] = schema.classId;
    if (registry && schema.factoryId !== registry.factoryId) {
      result['@factoryId'] = schema.factoryId;
    }
  }
  for (const field of [...schema.fields].sort((a, b) => a.order - b.order)) {
    const nested = FIELD_TYPE_INFO[field.fieldType].nestable ? nestedSchema(registry, schema.factoryId, field) : undefined;
    const nestedValue =
      nested && !nextPath.has(`${nested.factoryId}/${nested.classId}`) ? templateOf(nested, registry, now, nextPath, false) : undefined;
    result[field.fieldName] = sampleValue(field.fieldType, nestedValue, now);
  }
  return result;
}

function sampleValue(type: DtoFieldType, nested: JsonValue | undefined, now: Date): JsonValue {
  switch (type) {
    case 'NULLABLE_LONG':
    case 'NULLABLE_INT':
    case 'NULLABLE_DOUBLE':
    case 'RAW_INT':
    case 'RAW_LONG':
      return 0;
    case 'NULLABLE_STRING':
    case 'RAW_STRING':
    case 'RAW_BYTE_ARRAY':
      return '';
    case 'NULLABLE_DATE':
      return now.toISOString();
    case 'NULLABLE_BOOLEAN':
    case 'RAW_BOOLEAN':
      return false;
    case 'NULLABLE_BIG_DECIMAL':
      return '0';
    case 'NULLABLE_OBJECT':
      return nested ?? null;
    case 'NULLABLE_LIST':
      return nested === undefined ? [] : [nested];
    case 'NULLABLE_LONG_LIST':
    case 'NULLABLE_INTEGER_LIST':
      return [0];
  }
}

/** `SomeDTO (classId: 1)`; the factory is named when it is not the project factory. */
export function schemaLabel(schema: DtoClassSchema, registry: DtoRegistryView | undefined): string {
  const factory = registry && schema.factoryId !== registry.factoryId ? `factoryId: ${schema.factoryId}, ` : '';
  return `${schema.className} (${factory}classId: ${schema.classId})`;
}

/** Downloads JSON as a file (export of the registry or of one schema). */
export function downloadJson(fileName: string, content: unknown) {
  const blob = new Blob([`${JSON.stringify(content, null, 2)}\n`], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 1000);
}

/** One class id of the parsed factory: its constant(s), the class it creates and its schema. */
export interface FactoryRow {
  classId: number;
  constants: string[];
  className?: string;
  schema?: DtoClassSchema;
}

/**
 * Rows of the factory panel, one per class id. The schema status is matched strictly by number
 * (`factoryId` and `classId`), never by the spelling of the constant or class name, so a schema
 * added from the map browser or Smart Java Paste shows as registered right away.
 */
export function factoryRows(registry: DtoRegistryView): FactoryRow[] {
  const rows = new Map<number, FactoryRow>();
  const row = (classId: number) => {
    const id = Number(classId);
    const existing = rows.get(id) ?? { classId: id, constants: [] };
    rows.set(id, existing);
    return existing;
  };
  Object.entries(registry.factoryConstants).forEach(([name, id]) => row(id).constants.push(name));
  Object.entries(registry.factoryClasses).forEach(([name, id]) => {
    row(id).className ??= name;
  });
  for (const current of rows.values()) {
    current.schema = registry.schemas.find(
      (schema) => Number(schema.factoryId) === Number(registry.factoryId) && Number(schema.classId) === current.classId,
    );
  }
  return [...rows.values()].sort((a, b) => a.classId - b.classId);
}

