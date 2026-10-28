/**
 * TypeScript mirrors of the backend DTOs (package io.github.noproblem2000.tsarevnacache.api.dto).
 */

export type ErrorCode =
  | 'AUTH_REQUIRED'
  | 'AUTH_INVALID_CREDENTIALS'
  | 'LOGIN_RATE_LIMITED'
  | 'ACCESS_DENIED'
  | 'CSRF_TOKEN_INVALID'
  | 'VALIDATION_FAILED'
  | 'VALUE_REQUIRED'
  | 'VALUE_CONVERSION_FAILED'
  | 'INVALID_JSON'
  | 'CLASS_NOT_FOUND'
  | 'CLASS_NOT_ALLOWED'
  | 'CLASS_INSTANTIATION_FAILED'
  | 'SCHEMA_REQUIRED'
  | 'SCHEMA_INVALID'
  | 'INVALID_KEY_REF'
  | 'INVALID_CURSOR'
  | 'INVALID_PREDICATE'
  | 'CONFIRMATION_REQUIRED'
  | 'UNSUPPORTED_STRUCTURE_TYPE'
  | 'MAP_NOT_FOUND'
  | 'STRUCTURE_NOT_FOUND'
  | 'ENTRY_NOT_FOUND'
  | 'ITEM_NOT_FOUND'
  | 'NOT_FOUND'
  | 'METHOD_NOT_ALLOWED'
  | 'CLUSTER_UNAVAILABLE'
  | 'CLUSTER_TIMEOUT'
  | 'INTERNAL_ERROR'
  /** Client side only: the backend could not be reached. */
  | 'NETWORK_ERROR'
  /** Client side only: unexpected response. */
  | 'UNKNOWN';

export interface ApiErrorResponse {
  timestamp: string;
  status: number;
  code: ErrorCode;
  message: string;
  path: string;
  details?: Record<string, unknown>;
}

export interface AuthUser {
  username: string;
  roles: string[];
}

// ---------------------------------------------------------------- inspector

export type DataType =
  | 'STRING'
  | 'INTEGER'
  | 'LONG'
  | 'SHORT'
  | 'BYTE'
  | 'DOUBLE'
  | 'FLOAT'
  | 'BOOLEAN'
  | 'UUID'
  | 'BIG_DECIMAL'
  | 'BIG_INTEGER'
  | 'JSON'
  | 'CLASS'
  | 'COMPACT';

export const DATA_TYPES: DataType[] = [
  'STRING', 'INTEGER', 'LONG', 'SHORT', 'BYTE', 'DOUBLE', 'FLOAT', 'BOOLEAN', 'UUID',
  'BIG_DECIMAL', 'BIG_INTEGER', 'JSON', 'CLASS', 'COMPACT',
];

/** Key types offered in search boxes. */
export const KEY_TYPES: DataType[] = ['STRING', 'LONG', 'INTEGER', 'UUID', 'JSON', 'CLASS'];

export type ValueKind =
  | 'NULL'
  | 'STRING'
  | 'NUMBER'
  | 'BOOLEAN'
  | 'UUID'
  | 'JSON'
  | 'COMPACT'
  | 'PORTABLE'
  | 'OBJECT'
  | 'COLLECTION'
  | 'MAP'
  | 'ARRAY'
  | 'BYTES'
  | 'DATE_TIME'
  | 'UNRESOLVED';

export type DeserializationErrorCode =
  | 'CLASS_NOT_FOUND'
  | 'INCOMPATIBLE_CLASS'
  | 'DATA_SERIALIZABLE_FACTORY_MISSING'
  | 'PORTABLE_FACTORY_MISSING'
  | 'CUSTOM_SERIALIZER_MISSING'
  | 'COMPACT_SCHEMA_UNAVAILABLE'
  | 'DESERIALIZATION_ERROR';

export type FieldKind = string;

export interface RecordFieldDTO {
  name: string;
  kind: FieldKind;
  schema?: RecordSchemaDTO;
}

export interface RecordSchemaDTO {
  format: 'COMPACT' | 'PORTABLE';
  typeName: string;
  factoryId?: number;
  classId?: number;
  version?: number;
  fields: RecordFieldDTO[];
}

export interface UnresolvedObjectDTO {
  deserialized: false;
  errorCode: DeserializationErrorCode;
  reason?: string;
  missingClass?: string;
  typeId: number;
  serializer?: string;
  serializedSize?: number;
  entryCost?: number;
  metadata: Record<string, unknown>;
}

export type JsonValue = string | number | boolean | null | JsonValue[] | { [key: string]: JsonValue };

export interface InspectedValue {
  deserialized: boolean;
  kind: ValueKind;
  javaType?: string;
  dataType?: DataType;
  /** Full JSON value; omitted in list views and for unresolved objects. */
  value?: JsonValue;
  preview?: string;
  truncated: boolean;
  schema?: RecordSchemaDTO;
  serializedSize?: number;
  unresolved?: UnresolvedObjectDTO;
  warning?: 'JSON_CONVERSION_FAILED';
  metadata?: Record<string, unknown>;
}

// ---------------------------------------------------------------- maps

export interface EntryMetadataDTO {
  cost?: number;
  creationTime?: number;
  expirationTime?: number;
  hits?: number;
  lastAccessTime?: number;
  lastStoredTime?: number;
  lastUpdateTime?: number;
  version?: number;
  ttl?: number;
  maxIdle?: number;
}

export interface EntryDTO {
  keyRef: string;
  key: InspectedValue;
  value: InspectedValue;
  metadata?: EntryMetadataDTO;
}

export type SearchMode = 'ALL' | 'KEY' | 'PREDICATE';

export interface EntryPageDTO {
  mode: SearchMode;
  page: number;
  size: number;
  hasNext: boolean;
  nextCursor?: string;
  total?: number;
  entries: EntryDTO[];
}

export interface EntryQuery {
  page?: number;
  size?: number;
  cursor?: string;
  searchKey?: string;
  keyType?: DataType;
  keyClassName?: string;
  keyRef?: string;
  sqlPredicate?: string;
}

export interface KeySpec {
  keyRef?: string;
  key?: string;
  keyType?: DataType;
  keyClassName?: string;
}

export interface PutEntryRequest {
  key?: JsonValue;
  keyType?: DataType;
  keyClassName?: string;
  keySchema?: RecordSchemaDTO;
  keyRef?: string;
  value: JsonValue;
  valueType: DataType;
  valueClassName?: string;
  valueSchema?: RecordSchemaDTO;
  ttlSeconds?: number;
}

export interface StatsStatusDTO {
  available: boolean;
  collectedAt?: number;
  members: number;
  errors?: string[];
}

export interface MapStatsMember {
  uuid: string;
  address: string;
  ownedEntryCount: number;
  backupEntryCount: number;
  ownedEntryMemoryCost: number;
  backupEntryMemoryCost: number;
  hits: number;
  getOperationCount: number;
  putOperationCount: number;
  setOperationCount: number;
  removeOperationCount: number;
}

export interface NearCacheStats {
  ownedEntryCount: number;
  ownedEntryMemoryCost: number;
  hits: number;
  misses: number;
  ratio: number;
  evictions: number;
  expirations: number;
  invalidations: number;
}

export interface MapStatsDTO {
  name: string;
  ownedEntryCount: number;
  backupEntryCount: number;
  backupCount: number;
  ownedEntryMemoryCost: number;
  ownedEntryMemoryCostFormatted: string;
  backupEntryMemoryCost: number;
  backupEntryMemoryCostFormatted: string;
  heapCost: number;
  heapCostFormatted: string;
  merkleTreesCost: number;
  hits: number;
  getOperationCount: number;
  putOperationCount: number;
  setOperationCount: number;
  removeOperationCount: number;
  numberOfOtherOperations: number;
  numberOfEvents: number;
  evictionCount: number;
  expirationCount: number;
  lockedEntryCount: number;
  dirtyEntryCount: number;
  queryCount: number;
  indexedQueryCount: number;
  totalGetLatency: number;
  totalPutLatency: number;
  totalSetLatency: number;
  totalRemoveLatency: number;
  maxGetLatency: number;
  maxPutLatency: number;
  maxSetLatency: number;
  maxRemoveLatency: number;
  creationTime?: number;
  lastAccessTime?: number;
  lastUpdateTime?: number;
  nearCache?: NearCacheStats;
  members: MapStatsMember[];
  stats: StatsStatusDTO;
}

// ---------------------------------------------------------------- cluster & structures

export type StructureType =
  | 'MAP'
  | 'REPLICATED_MAP'
  | 'MULTI_MAP'
  | 'QUEUE'
  | 'SET'
  | 'LIST'
  | 'TOPIC'
  | 'RELIABLE_TOPIC'
  | 'FLAKE_ID_GENERATOR'
  | 'OTHER';

export const STRUCTURE_TYPES: StructureType[] = [
  'MAP', 'REPLICATED_MAP', 'MULTI_MAP', 'QUEUE', 'SET', 'LIST', 'TOPIC', 'RELIABLE_TOPIC',
  'FLAKE_ID_GENERATOR', 'OTHER',
];

/** Types with a data view at /api/other/{path}/{name}. */
export const BROWSABLE_OTHER_TYPES: Partial<Record<StructureType, string>> = {
  QUEUE: 'queue',
  SET: 'set',
  LIST: 'list',
  MULTI_MAP: 'multimap',
  REPLICATED_MAP: 'replicatedmap',
};

export interface MemberDTO {
  uuid: string;
  address: string;
  host: string;
  port: number;
  liteMember: boolean;
  master: boolean;
  version?: string;
  usedHeap?: number;
  maxHeap?: number;
  uptimeMillis?: number;
}

export interface MapMemory {
  name: string;
  ownedEntryCount: number;
  ownedEntryMemoryCost: number;
  backupEntryMemoryCost: number;
}

export interface ClusterTotals {
  distributedObjects: number;
  mapCount: number;
  mapEntries: number;
  ownedEntryMemoryCost: number;
  ownedEntryMemoryCostFormatted: string;
  backupEntryMemoryCost: number;
  backupEntryMemoryCostFormatted: string;
}

export interface ClusterOverviewDTO {
  connected: boolean;
  clusterName: string;
  clientVersion: string;
  clusterVersion?: string;
  members: MemberDTO[];
  totals: ClusterTotals;
  structureCounts: Record<StructureType, number>;
  topMaps: MapMemory[];
  stats: StatsStatusDTO;
}

export interface StructureDTO {
  name: string;
  type: StructureType;
  serviceName: string;
  internal: boolean;
  size?: number;
  ownedEntryMemoryCost?: number;
  backupEntryMemoryCost?: number;
}

export interface StructuresDTO {
  total: number;
  groups: Record<StructureType, StructureDTO[]>;
  stats: StatsStatusDTO;
}

export interface OtherStructureItem {
  ref?: string;
  value: InspectedValue;
  keyRef?: string;
  key?: InspectedValue;
}

export interface OtherStructureDTO {
  type: StructureType;
  name: string;
  size: number;
  limit: number;
  itemsAvailable: boolean;
  items: OtherStructureItem[];
}
