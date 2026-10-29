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

const number = (value: unknown) => (typeof value === 'number' && Number.isInteger(value) ? value : undefined);

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
    const name = unresolved.missingClass?.match(/^([A-Za-z_$][\w$]*) \(factoryId=/)?.[1];
    return { factoryId, classId, className: name };
  }
  if (unresolved.errorCode === 'DATA_SERIALIZABLE_FACTORY_MISSING' && metadata.identified === true) {
    const factoryId = number(metadata.factoryId);
    const classId = number(metadata.classId);
    return factoryId === undefined || classId === undefined ? undefined : { factoryId, classId };
  }
  return undefined;
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
 * Refreshes everything a schema change affects: the registry and every cached map / structure
 * page (values of the class become readable or unreadable immediately).
 */
export function useInvalidateSchemaData() {
  const queryClient = useQueryClient();
  return useCallback(
    () =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: queryKeys.schemas }),
        queryClient.invalidateQueries({ queryKey: queryKeys.allMaps }),
        queryClient.invalidateQueries({ queryKey: ['other'] }),
      ]),
    [queryClient],
  );
}
