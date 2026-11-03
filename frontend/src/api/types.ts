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
  | 'DTO_SCHEMA_INVALID'
  | 'DTO_SCHEMA_NOT_FOUND'
  | 'DTO_SOURCE_NOT_RECOGNIZED'
  | 'HEALTHCHECK_IGNORE_NOT_FOUND'
  | 'INVALID_CURSOR'
  | 'INVALID_PREDICATE'
  | 'CONFIRMATION_REQUIRED'
  | 'UNSUPPORTED_STRUCTURE_TYPE'
  | 'LOG_FILE_NOT_FOUND'
  | 'LOG_FILE_UNREADABLE'
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
  | 'COMPACT'
  | 'IDENTIFIED_DTO';

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
  | 'IDENTIFIED_DTO'
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
  | 'DESERIALIZATION_ERROR'
  | 'DTO_SCHEMA_MISSING'
  | 'DTO_FIELD_READ_ERROR';

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

export type SearchMode = 'ALL' | 'KEY' | 'PREDICATE' | 'SORTED';

/** Order of the data browser; everything but NONE ranks a sample of keys by an EntryView metric. */
export const ENTRY_SORTS = ['NONE', 'HITS', 'COST', 'LAST_ACCESS', 'CREATION_TIME', 'KEY'] as const;
export type EntrySortBy = (typeof ENTRY_SORTS)[number];
export type SortDirection = 'DESC' | 'ASC';

export interface EntryPageDTO {
  mode: SearchMode;
  page: number;
  size: number;
  hasNext: boolean;
  nextCursor?: string;
  total?: number;
  entries: EntryDTO[];
  /** SORTED only: applied order. */
  sortBy?: EntrySortBy;
  sortDirection?: SortDirection;
  /** SORTED only: more keys matched than were ranked. */
  sampled?: boolean;
  /** SORTED only: entries the ranking was built from. */
  scannedCount?: number;
  scanLimit?: number;
  /** SORTED only: highest hit count of the ranked entries; absent when hits are not tracked. */
  maxHits?: number;
  /** SORTED only: false when the map has per-entry statistics disabled. */
  hitsAvailable?: boolean;
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
  sortBy?: EntrySortBy;
  sortDirection?: SortDirection;
  scanLimit?: number;
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
  keyFactoryId?: number;
  keyClassId?: number;
  valueFactoryId?: number;
  valueClassId?: number;
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
  /** Version of the admin service itself. */
  appVersion?: string;
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

// ---------------------------------------------------------------- dynamic DTO schemas

/** Serialization helpers of a DTO field, in the order of `DtoFieldType.java`. */
export const DTO_FIELD_TYPES = [
  'NULLABLE_LONG',
  'NULLABLE_STRING',
  'NULLABLE_DATE',
  'NULLABLE_OBJECT',
  'NULLABLE_BOOLEAN',
  'NULLABLE_INT',
  'NULLABLE_DOUBLE',
  'NULLABLE_BIG_DECIMAL',
  'NULLABLE_LIST',
  'NULLABLE_LONG_LIST',
  'NULLABLE_INTEGER_LIST',
  'RAW_INT',
  'RAW_LONG',
  'RAW_BOOLEAN',
  'RAW_STRING',
  'RAW_BYTE_ARRAY',
] as const;

export type DtoFieldType = (typeof DTO_FIELD_TYPES)[number];

export interface DtoFieldSchema {
  order: number;
  fieldName: string;
  fieldType: DtoFieldType;
  nestedClassId?: number | null;
  nestedClassName?: string | null;
}

export interface DtoClassSchema {
  factoryId: number;
  classId: number;
  className: string;
  fields: DtoFieldSchema[];
  rawJavaSource?: string | null;
}

export interface DtoRegistryView {
  factoryId: number;
  factoryConstants: Record<string, number>;
  factoryClasses: Record<string, number>;
  activeFactoryIds: number[];
  pendingFactoryIds: number[];
  /** Factory id → DataSerializableFactory class of the client (configured or found in lib/). */
  libraryFactories: Record<string, string>;
  file: string;
  schemas: DtoClassSchema[];
}

/** Content of `dto-schemas.json` (export / import). */
export interface DtoSchemaRegistryFile {
  formatVersion?: number;
  factoryId?: number;
  factoryConstants?: Record<string, number>;
  factoryClasses?: Record<string, number>;
  schemas: DtoClassSchema[];
}

export interface FactoryParseResult {
  factoryId: number;
  constants: Record<string, number>;
  classes: Record<string, number>;
  warnings: string[];
}

export interface DtoParseResult {
  schema: DtoClassSchema;
  /** Method the field list was taken from. */
  source: 'readData' | 'writeData';
  complete: boolean;
  warnings: string[];
}

export interface DtoSaveResult {
  schema: DtoClassSchema;
  /** The factory id is not served by the running client yet: restart the service. */
  restartRequired: boolean;
  /** Keys (`factoryId:classId`) of other schemas whose nested fields now point to this class. */
  linkedSchemas?: string[];
  /** Things to check, e.g. the same class registered under another class id. */
  warnings?: string[];
}

// ---------------------------------------------------------------- healthcheck

export type HealthSeverity = 'CRITICAL' | 'WARNING' | 'INFO';
export const HEALTH_SEVERITIES: HealthSeverity[] = ['CRITICAL', 'WARNING', 'INFO'];

export type HealthTargetType = 'CLUSTER' | 'IMAP' | 'IQUEUE' | 'DTO_SCHEMA';
export const HEALTH_TARGET_TYPES: HealthTargetType[] = ['CLUSTER', 'IMAP', 'IQUEUE', 'DTO_SCHEMA'];

export type HealthStatus = 'HEALTHY' | 'DEGRADED' | 'CRITICAL';

export type HealthCheckType =
  | 'CLUSTER_SINGLE_MEMBER'
  | 'MAP_UNBOUNDED_NO_TTL'
  | 'MAP_ZOMBIE_WRITE_ONLY'
  | 'MAP_BACKUP_DISABLED_OR_BROKEN'
  | 'MAP_HIGH_LATENCY'
  | 'QUEUE_BACKLOG_GROWTH'
  | 'DTO_UNREGISTERED_SCHEMA';

export const HEALTH_CHECK_TYPES: HealthCheckType[] = [
  'CLUSTER_SINGLE_MEMBER',
  'MAP_UNBOUNDED_NO_TTL',
  'MAP_ZOMBIE_WRITE_ONLY',
  'MAP_BACKUP_DISABLED_OR_BROKEN',
  'MAP_HIGH_LATENCY',
  'QUEUE_BACKLOG_GROWTH',
  'DTO_UNREGISTERED_SCHEMA',
];

export interface HealthCheckIssue {
  /** Deterministic key, e.g. `MAP_UNBOUNDED_NO_TTL:user_sessions`. */
  issueId: string;
  checkType: HealthCheckType;
  severity: HealthSeverity;
  targetType: HealthTargetType;
  targetName: string;
  currentValue: string;
  /** Translation key below `healthcheck.recommendations`. */
  recommendationCode: string;
  detectedAt: string;
  details: Record<string, unknown>;
}

export interface HealthCheckIgnore {
  /** Exact issue id, or `CHECK_TYPE:*` for the whole rule. */
  issueId: string;
  reason?: string;
  createdAt?: string;
  createdBy?: string;
}

export interface HealthCheckReport {
  healthScore: number;
  status: HealthStatus;
  lastRunTime?: string;
  durationMillis: number;
  clusterAvailable: boolean;
  error?: string;
  counts: { critical: number; warning: number; info: number; ignored: number };
  activeIssues: HealthCheckIssue[];
  ignoredIssues: HealthCheckIssue[];
  ignores: HealthCheckIgnore[];
  checkedMaps: number;
  sampledMaps: number;
  /** Period of the background runs, 0 when they are disabled. */
  intervalMillis: number;
}

/** One `.java` file of a batch import (`POST /api/schemas/batch-import-java`). */
export interface JavaFileSource {
  /** File name, or the relative path when a folder was selected. */
  fileName: string;
  content: string;
}

/** A file skipped on purpose by the batch import. */
export interface IgnoredFile {
  fileName: string;
  className?: string;
  /** `Not a Hazelcast IdentifiedDataSerializable DTO`, `Not registered in DataSerializableFactory`, ... */
  reason: string;
  /** What made the decision, e.g. `enum`, `abstract class`, `no readData/writeData`. */
  detail?: string;
}

/** A problem in a valid DTO of the batch (unrecognized field, or the DTO could not be imported). */
export interface BatchImportError {
  fileName: string;
  className?: string;
  message: string;
  /** The schema was imported nevertheless (review it). */
  imported: boolean;
}

export interface BatchImportResult {
  totalFiles: number;
  importedCount: number;
  imported: DtoClassSchema[];
  /** Keys (`factoryId:classId`) of imported schemas that replaced an existing one. */
  updatedKeys: string[];
  /** Keys of other schemas whose nested fields now point to an imported class. */
  linkedSchemas: string[];
  factoryUpdated: boolean;
  factoryFile?: string;
  factoryId?: number;
  factoryConstantCount: number;
  ignoredCount: number;
  ignored: IgnoredFile[];
  errors: BatchImportError[];
}

// ------------------------------------------------------------------ log viewer

export const LOG_LEVELS = ['ERROR', 'WARN', 'INFO', 'DEBUG'] as const;
export type LogLevel = (typeof LOG_LEVELS)[number];
/** A level keeps its own records and the more severe ones; ALL also keeps lines without a level. */
export type LogLevelFilter = 'ALL' | LogLevel;

export interface LogFile {
  /** Absolute path; send it back as `file`. */
  id: string;
  name: string;
  configuredPath: string;
  exists: boolean;
  readable: boolean;
  size?: number;
  lastModified?: number;
  /** The admin service's own log (logging.file.name). */
  applicationLog: boolean;
}

export interface LogFiles {
  files: LogFile[];
  configuredPaths: string[];
  defaultLines: number;
  maxLines: number;
}

export interface LogLine {
  /** 1-based; counted from the end (-1 = last line) when the file is too large to count. */
  number: number;
  text: string;
  /** Level of the record the line belongs to (stack trace lines inherit it). */
  level?: LogLevel;
  /** The line continues the record of an earlier line. */
  continuation: boolean;
}

export interface LogTail {
  file: LogFile;
  lines: LogLine[];
  requestedLines: number;
  totalLines?: number;
  scannedBytes: number;
  reachedStart: boolean;
  scanLimitReached: boolean;
  level: LogLevelFilter;
  search?: string;
}

export interface LogTailQuery {
  file?: string;
  lines?: number;
  level?: LogLevelFilter;
  search?: string;
}

export type LiveEventSource = 'LOG' | 'MEMBERSHIP' | 'LIFECYCLE' | 'MIGRATION' | 'DISTRIBUTED_OBJECT';

export interface LiveEvent {
  seq: number;
  timestamp: number;
  level: LogLevel;
  source: LiveEventSource;
  logger?: string;
  thread?: string;
  message?: string;
  stackTrace?: string;
}

export interface LiveEvents {
  events: LiveEvent[];
  /** Newest sequence number of the buffer; ask for events `after` it next time. */
  lastSeq: number;
  buffered: number;
  capacity: number;
  dropped: number;
}
