import { useQueryClient } from '@tanstack/react-query';
import { useCallback } from 'react';
import { queryKeys } from '../../api/queries';
import type { UnresolvedObjectDTO } from '../../api/types';

/** An IdentifiedDataSerializable class that has no schema yet. */
export interface DtoTarget {
  factoryId: number;
  classId: number;
  /** Class name when the factory mapping knows it. */
  className?: string;
}

/** The stored (root) DTO of a value whose reading failed. */
export interface DtoRoot {
  factoryId: number;
  classId: number;
  className?: string;
}

/**
 * Why a dynamic DTO value could not be read:
 * - `missing`: the class `target` has no schema; `nested` when the root DTO itself is registered
 *   and only a DTO inside it is missing;
 * - `field`: every schema exists, but reading the class `schema` broke at one field (wrong type or
 *   order in the schema).
 */
export type DtoProblem =
  | { kind: 'missing'; target: DtoTarget; nested: boolean; root?: DtoRoot; fieldPath?: string }
  | {
      kind: 'field';
      schema: DtoRoot;
      fieldOrder: number;
      fieldName: string;
      fieldType: string;
      fieldError?: string;
      root?: DtoRoot;
      fieldPath?: string;
    };

const number = (value: unknown) => (typeof value === 'number' && Number.isInteger(value) ? value : undefined);
const text = (value: unknown) => (typeof value === 'string' && value !== '' ? value : undefined);

/** Storage key of a schema, the same as on the backend: `factoryId:classId`. */
export function schemaKey(factoryId: number, classId: number): string {
  return `${Number(factoryId)}:${Number(classId)}`;
}

function rootOf(metadata: Record<string, unknown>): DtoRoot | undefined {
  const factoryId = number(metadata.rootFactoryId);
  const classId = number(metadata.rootClassId);
  return factoryId === undefined || classId === undefined
    ? undefined
    : { factoryId, classId, className: text(metadata.rootClassName) };
}

/**
 * The exact problem of a value read through the dynamic DTO engine (`DTO_SCHEMA_MISSING`,
 * `DTO_FIELD_READ_ERROR`), or `undefined` for any other failure.
 */
export function dtoProblem(unresolved: UnresolvedObjectDTO | undefined): DtoProblem | undefined {
  if (!unresolved) {
    return undefined;
  }
  const metadata = unresolved.metadata ?? {};
  if (unresolved.errorCode === 'DTO_FIELD_READ_ERROR') {
    const factoryId = number(metadata.failedFactoryId);
    const classId = number(metadata.failedClassId);
    const fieldOrder = number(metadata.fieldOrder);
    if (factoryId === undefined || classId === undefined || fieldOrder === undefined) {
      return undefined;
    }
    return {
      kind: 'field',
      schema: { factoryId, classId, className: text(metadata.failedClassName) },
      fieldOrder,
      fieldName: text(metadata.fieldName) ?? `#${fieldOrder}`,
      fieldType: text(metadata.fieldType) ?? '?',
      fieldError: text(metadata.fieldError),
      root: rootOf(metadata),
      fieldPath: text(metadata.fieldPath),
    };
  }
  const target = unresolved.errorCode === 'DTO_SCHEMA_MISSING' ? missingDtoTarget(unresolved) : undefined;
  if (!target) {
    return undefined;
  }
  return {
    kind: 'missing',
    target,
    nested: metadata.nested === true,
    root: rootOf(metadata),
    fieldPath: text(metadata.fieldPath),
  };
}

/**
 * The DTO a schema would make readable: the missing (possibly nested) class of a
 * `DTO_SCHEMA_MISSING` value, or the class of an IdentifiedDataSerializable whose factory is not
 * registered at all (a schema helps there after a restart).
 */
export function missingDtoTarget(unresolved: UnresolvedObjectDTO | undefined): DtoTarget | undefined {
  if (!unresolved) {
    return undefined;
  }
  const metadata = unresolved.metadata ?? {};
  if (unresolved.errorCode === 'DTO_SCHEMA_MISSING') {
    const factoryId = number(metadata.missingFactoryId);
    const classId = number(metadata.missingClassId);
    if (factoryId === undefined || classId === undefined) {
      return undefined;
    }
    // "SomeDTO (factoryId=1, classId=3)" when the factory mapping knows the class
    const name = text(metadata.missingClassName) ?? unresolved.missingClass?.match(/^([A-Za-z_$][\w$]*) \(factoryId=/)?.[1];
    return { factoryId, classId, className: name };
  }
  if (unresolved.errorCode === 'DATA_SERIALIZABLE_FACTORY_MISSING' && metadata.identified === true) {
    const factoryId = number(metadata.factoryId);
    const classId = number(metadata.classId);
    return factoryId === undefined || classId === undefined ? undefined : { factoryId, classId };
  }
  return undefined;
}

/** `SomeDTO (classId: 1)`, or `classId: 1` without a name; the factory is named when it differs. */
export function dtoLabel(dto: DtoRoot | DtoTarget, projectFactoryId?: number): string {
  const factory = projectFactoryId !== undefined && dto.factoryId !== projectFactoryId ? `factoryId: ${dto.factoryId}, ` : '';
  return dto.className ? `${dto.className} (${factory}classId: ${dto.classId})` : `${factory}classId: ${dto.classId}`;
}

/** Route of the DTO Schema Studio that opens the schema dialog for a missing class. */
export function configureSchemaRoute(target: DtoTarget): string {
  const params = new URLSearchParams({ configure: '1', factoryId: String(target.factoryId), classId: String(target.classId) });
  if (target.className) {
    params.set('className', target.className);
  }
  return `/schemas?${params.toString()}`;
}

/** Route of the DTO Schema Studio that opens an existing schema for editing. */
export function editSchemaRoute(factoryId: number, classId: number): string {
  return `/schemas?edit=${factoryId}/${classId}`;
}

/**
 * Refreshes everything a schema change affects at once: the registry, every cached map page
 * (open entries are refetched, values of the class become readable or unreadable immediately), other
 * structures and the healthcheck report (the backend re-runs the checks after the change).
 */
export function useInvalidateSchemaData() {
  const queryClient = useQueryClient();
  return useCallback(
    () =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: queryKeys.schemas }),
        queryClient.invalidateQueries({ queryKey: queryKeys.allMaps }),
        queryClient.invalidateQueries({ queryKey: ['other'] }),
        queryClient.invalidateQueries({ queryKey: queryKeys.health }),
      ]),
    [queryClient],
  );
}
