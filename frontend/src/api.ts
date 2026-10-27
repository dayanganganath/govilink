export interface Farmer {
  id: string
  fullName: string
  phone: string
  district: string
  village: string | null
  active: boolean
  version: number
  createdAt: string
  updatedAt: string
}

export interface FarmerPage {
  content: Farmer[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  hasNext: boolean
  hasPrevious: boolean
}

export interface CreateFarmerRequest {
  fullName: string
  phone: string
  district: string
  village: string
}

export interface UpdateFarmerRequest extends CreateFarmerRequest {
  version: number
}

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

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function readFieldErrors(body: unknown): Record<string, string> {
  if (!isObject(body) || !isObject(body.errors)) {
    return {}
  }

  return Object.fromEntries(
    Object.entries(body.errors).filter(
      (entry): entry is [string, string] => typeof entry[1] === 'string',
    ),
  )
}

function describeError(
  body: unknown,
  status: number,
  fieldErrors: Record<string, string>,
): string {
  const messages = Object.entries(fieldErrors).map(
    ([field, message]) => `${field}: ${message}`,
  )

  if (messages.length > 0) {
    return messages.join(' · ')
  }

  if (isObject(body) && typeof body.detail === 'string') {
    return body.detail
  }

  return `Request failed (${status}). Please try again.`
}

async function request<T>(
  path: string,
  options?: RequestInit,
): Promise<T> {
  const response = await fetch(path, options)
  const text = await response.text()
  let body: unknown = null

  try {
    body = text ? JSON.parse(text) : null
  } catch {
    if (response.ok) {
      throw new Error('Unexpected server response. Check the API proxy.')
    }
  }

  if (!response.ok) {
    const fieldErrors = readFieldErrors(body)

    throw new ApiError(
      describeError(body, response.status, fieldErrors),
      response.status,
      fieldErrors,
    )
  }

  if (body === null) {
    throw new Error('The server returned an empty response.')
  }

  return body as T
}

export function listFarmers(
  page: number,
  query: string,
  district: string,
  signal: AbortSignal,
): Promise<FarmerPage> {
  const parameters = new URLSearchParams({
    page: String(page),
    size: '10',
  })

  if (query) {
    parameters.set('q', query)
  }

  if (district) {
    parameters.set('district', district)
  }

  return request<FarmerPage>(
    `/api/v1/farmers/search?${parameters.toString()}`,
    { signal },
  )
}

export function createFarmer(
  farmer: CreateFarmerRequest,
): Promise<Farmer> {
  return request<Farmer>('/api/v1/farmers', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(farmer),
  })
}

export function getFarmer(
  id: string,
  signal?: AbortSignal,
): Promise<Farmer> {
  return request<Farmer>(
    `/api/v1/farmers/${encodeURIComponent(id)}`,
    { signal },
  )
}

export function updateFarmer(
  id: string,
  farmer: UpdateFarmerRequest,
): Promise<Farmer> {
  return request<Farmer>(
    `/api/v1/farmers/${encodeURIComponent(id)}`,
    {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(farmer),
    },
  )
}