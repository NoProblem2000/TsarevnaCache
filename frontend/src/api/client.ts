import type { ApiErrorResponse, ErrorCode } from './types';

const CSRF_COOKIE = 'XSRF-TOKEN';
const CSRF_HEADER = 'X-XSRF-TOKEN';

/** Event dispatched on window whenever the backend answers 401 (session expired / signed out). */
export const UNAUTHORIZED_EVENT = 'tsarevna:unauthorized';

/**
 * Error thrown for every failed API call. `code` maps to the translation key `errors.<code>`.
 */
export class ApiError extends Error {
  readonly status: number;
  readonly code: ErrorCode;
  readonly details: Record<string, unknown>;

  constructor(status: number, code: ErrorCode, message: string, details: Record<string, unknown> = {}) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.details = details;
  }
}

export type QueryParams = Record<string, string | number | boolean | undefined | null>;

interface RequestOptions {
  query?: QueryParams;
  body?: unknown;
  /** Do not broadcast {@link UNAUTHORIZED_EVENT} on 401 (used by the login/session probe). */
  silentUnauthorized?: boolean;
}

function readCookie(name: string): string | undefined {
  return document.cookie
    .split(';')
    .map((part) => part.trim())
    .find((part) => part.startsWith(`${name}=`))
    ?.substring(name.length + 1);
}

let csrfRequest: Promise<void> | null = null;

/** Makes sure the CSRF cookie exists (the backend sets it on GET /api/auth/csrf). */
async function ensureCsrfToken(): Promise<string | undefined> {
  if (!readCookie(CSRF_COOKIE)) {
    csrfRequest ??= fetch('/api/auth/csrf', { credentials: 'same-origin' })
      .then(() => undefined)
      .finally(() => {
        csrfRequest = null;
      });
    await csrfRequest;
  }
  const token = readCookie(CSRF_COOKIE);
  return token ? decodeURIComponent(token) : undefined;
}

function buildUrl(path: string, query?: QueryParams): string {
  if (!query) {
    return path;
  }
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query)) {
    if (value !== undefined && value !== null && value !== '') {
      params.append(key, String(value));
    }
  }
  const search = params.toString();
  return search ? `${path}?${search}` : path;
}

async function toApiError(response: Response): Promise<ApiError> {
  try {
    const body = (await response.json()) as Partial<ApiErrorResponse>;
    if (body && typeof body.code === 'string') {
      return new ApiError(response.status, body.code as ErrorCode, body.message ?? response.statusText, body.details ?? {});
    }
  } catch {
    // not a JSON error body
  }
  return new ApiError(response.status, 'UNKNOWN', response.statusText || `HTTP ${response.status}`);
}

/**
 * Performs an API call with the session cookie and, for state changing methods, the CSRF header.
 * A rejected CSRF token is refreshed and the call retried once.
 */
export async function request<T>(method: string, path: string, options: RequestOptions = {}, retried = false): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }
  if (method !== 'GET' && method !== 'HEAD') {
    const token = await ensureCsrfToken();
    if (token) {
      headers[CSRF_HEADER] = token;
    }
  }

  let response: Response;
  try {
    response = await fetch(buildUrl(path, options.query), {
      method,
      headers,
      credentials: 'same-origin',
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
    });
  } catch (error) {
    throw new ApiError(0, 'NETWORK_ERROR', error instanceof Error ? error.message : 'Network error');
  }

  if (response.ok) {
    if (response.status === 204) {
      return undefined as T;
    }
    const text = await response.text();
    return (text ? JSON.parse(text) : undefined) as T;
  }

  const error = await toApiError(response);
  if (error.code === 'CSRF_TOKEN_INVALID' && !retried) {
    document.cookie = `${CSRF_COOKIE}=; Max-Age=0; path=/`;
    return request<T>(method, path, options, true);
  }
  if (response.status === 401 && !options.silentUnauthorized) {
    window.dispatchEvent(new CustomEvent(UNAUTHORIZED_EVENT));
  }
  throw error;
}

export const api = {
  get: <T>(path: string, query?: QueryParams, silentUnauthorized = false) =>
    request<T>('GET', path, { query, silentUnauthorized }),
  post: <T>(path: string, body?: unknown, query?: QueryParams, silentUnauthorized = false) =>
    request<T>('POST', path, { body, query, silentUnauthorized }),
  delete: <T>(path: string, query?: QueryParams) => request<T>('DELETE', path, { query }),
};
