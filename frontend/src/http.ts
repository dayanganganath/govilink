export class ApiError extends Error {
  readonly status: number
  readonly fieldErrors: Record<string, string>

  constructor(
    message: string,
    status: number,
    fieldErrors: Record<string, string> = {},
  ) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.fieldErrors = fieldErrors
  }
}

export const SESSION_EXPIRED_EVENT = 'govilink:session-expired'

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object'
    && value !== null
    && !Array.isArray(value)
}

async function readBody(response: Response): Promise<unknown> {
  const text = await response.text()

  if (!text) return null

  try {
    return JSON.parse(text) as unknown
  } catch {
    if (response.ok) {
      throw new Error(
        'Unexpected server response. Check the API proxy.',
      )
    }

    return null
  }
}

function responseError(
  response: Response,
  body: unknown,
): ApiError {
  const fieldErrors: Record<string, string> = {}

  if (isObject(body) && isObject(body.errors)) {
    for (const [field, value] of Object.entries(body.errors)) {
      if (typeof value === 'string') {
        fieldErrors[field] = value
      }
    }
  }

  const messages = Object.entries(fieldErrors)
    .map(([field, message]) => `${field}: ${message}`)

  const detail = isObject(body) && typeof body.detail === 'string'
    ? body.detail
    : `Request failed (${response.status}). Please try again.`

  return new ApiError(
    messages.length ? messages.join(' · ') : detail,
    response.status,
    fieldErrors,
  )
}

async function csrfHeaders(
  signal?: AbortSignal | null,
): Promise<Record<string, string>> {
  const response = await fetch('/api/v1/auth/csrf', {
    credentials: 'same-origin',
    cache: 'no-store',
    signal,
  })

  const body = await readBody(response)

  if (!response.ok) {
    if (response.status === 401) {
      window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT))
    }

    throw responseError(response, body)
  }

  if (
    !isObject(body)
    || body.headerName !== 'X-CSRF-TOKEN'
    || typeof body.token !== 'string'
    || !body.token
  ) {
    throw new Error(
      'Unable to obtain a valid security token. Please try again.',
    )
  }

  return { 'X-CSRF-TOKEN': body.token }
}

export async function request<T>(
  path: string,
  options: RequestInit = {},
): Promise<T> {
  if (!path.startsWith('/api/v1/')) {
    throw new Error('Only GoviLink API paths are supported.')
  }

  const method = (options.method ?? 'GET').toUpperCase()
  const headers = new Headers(options.headers)

  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    const securityHeaders = await csrfHeaders(options.signal)

    for (const [name, value] of Object.entries(securityHeaders)) {
      headers.set(name, value)
    }
  }

  const response = await fetch(path, {
    ...options,
    method,
    headers,
    credentials: 'same-origin',
    cache: 'no-store',
  })

  const body = await readBody(response)

  if (!response.ok) {
    if (
      response.status === 401
      && !path.startsWith('/api/v1/auth/')
    ) {
      window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT))
    }

    throw responseError(response, body)
  }

  if (response.status === 204) {
    return undefined as T
  }

  if (body === null) {
    throw new Error('The server returned an empty response.')
  }

  return body as T
}