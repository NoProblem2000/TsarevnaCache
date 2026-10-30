import { api } from './client';
import type {
  AuthUser,
  BatchImportResult,
  ClusterOverviewDTO,
  DtoClassSchema,
  DtoParseResult,
  DtoRegistryView,
  DtoSaveResult,
  DtoSchemaRegistryFile,
  EntryDTO,
  EntryPageDTO,
  EntryQuery,
  FactoryParseResult,
  HealthCheckReport,
  JavaFileSource,
  KeySpec,
  MapStatsDTO,
  OtherStructureDTO,
  PutEntryRequest,
  StructuresDTO,
} from './types';

/** Prometheus exporter of the healthcheck (text format 0.0.4). */
export const PROMETHEUS_PATH = '/api/healthcheck/prometheus';

const mapPath = (mapName: string) => `/api/maps/${encodeURIComponent(mapName)}`;
const otherPath = (typePath: string, name: string) =>
  `/api/other/${encodeURIComponent(typePath)}/${encodeURIComponent(name)}`;

const keyQuery = (key: KeySpec) => ({
  keyRef: key.keyRef,
  key: key.keyRef ? undefined : key.key,
  keyType: key.keyRef ? undefined : key.keyType,
  keyClassName: key.keyRef ? undefined : key.keyClassName,
});

/** Typed wrappers for every backend endpoint. */
export const endpoints = {
  auth: {
    me: () => api.get<AuthUser>('/api/auth/me', undefined, true),
    login: (username: string, password: string) =>
      api.post<AuthUser>('/api/auth/login', { username, password }, undefined, true),
    logout: () => api.post<void>('/api/auth/logout'),
  },
  cluster: {
    overview: () => api.get<ClusterOverviewDTO>('/api/cluster/overview'),
    structures: (includeInternal = false) => api.get<StructuresDTO>('/api/structures', { includeInternal }),
  },
  maps: {
    stats: (mapName: string) => api.get<MapStatsDTO>(`${mapPath(mapName)}/stats`),
    entries: (mapName: string, query: EntryQuery) =>
      api.get<EntryPageDTO>(`${mapPath(mapName)}/entries`, { ...query }),
    entry: (mapName: string, key: KeySpec) => api.get<EntryDTO>(`${mapPath(mapName)}/entry`, keyQuery(key)),
    put: (mapName: string, request: PutEntryRequest) => api.post<EntryDTO>(`${mapPath(mapName)}/entry`, request),
    remove: (mapName: string, key: KeySpec) => api.delete<void>(`${mapPath(mapName)}/entry`, keyQuery(key)),
    evictAll: (mapName: string) => api.post<void>(`${mapPath(mapName)}/evict`),
    clear: (mapName: string, confirm: string) => api.delete<void>(`${mapPath(mapName)}/clear`, { confirm }),
  },
  other: {
    view: (typePath: string, name: string, limit = 50) =>
      api.get<OtherStructureDTO>(otherPath(typePath, name), { limit }),
    removeItem: (typePath: string, name: string, ref?: string, keyRef?: string) =>
      api.delete<void>(`${otherPath(typePath, name)}/item`, { ref, keyRef }),
    clear: (typePath: string, name: string, confirm: string) =>
      api.delete<void>(`${otherPath(typePath, name)}/clear`, { confirm }),
  },
  healthcheck: {
    report: () => api.get<HealthCheckReport>('/api/healthcheck/report'),
    run: () => api.post<HealthCheckReport>('/api/healthcheck/run'),
    ignore: (issueId: string, reason: string) => api.post<HealthCheckReport>('/api/healthcheck/ignore', { issueId, reason }),
    // query variant: issue ids may contain characters that are rejected inside a path
    unignore: (issueId: string) => api.delete<HealthCheckReport>('/api/healthcheck/ignore', { issueId }),
    prometheus: () => api.getText(PROMETHEUS_PATH),
  },
  schemas: {
    list: () => api.get<DtoRegistryView>('/api/schemas'),
    parseFactory: (source: string, save = true) =>
      api.post<FactoryParseResult>('/api/schemas/parse-factory', { source }, { save }),
    parseDto: (source: string) => api.post<DtoParseResult>('/api/schemas/parse-dto', { source }),
    save: (schema: DtoClassSchema) => api.post<DtoSaveResult>('/api/schemas', schema),
    remove: (factoryId: number, classId: number) => api.delete<void>(`/api/schemas/${factoryId}/${classId}`),
    exportFile: () => api.get<DtoSchemaRegistryFile>('/api/schemas/export'),
    importFile: (content: DtoSchemaRegistryFile, mode: 'replace' | 'merge') =>
      api.post<DtoRegistryView>('/api/schemas/import', content, { mode }),
    batchImportJava: (files: JavaFileSource[]) => api.post<BatchImportResult>('/api/schemas/batch-import-java', files),
  },
};
