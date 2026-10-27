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

function describeError(body: unknown, status: number): string {
  if (typeof body === 'object' && body !== null) {
    const problem = body as Record<string, unknown>

    if (typeof problem.errors === 'object' && problem.errors !== null) {
      const messages = Object.entries(problem.errors)
        .filter((entry) => typeof entry[1] === 'string')
        .map(([field, message]) => `${field}: ${message}`)

      if (messages.length > 0) return messages.join(' · ')
    }

    if (typeof problem.detail === 'string') return problem.detail
  }

  return `Request failed (${status}). Check that the backend is running.`
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
    throw new Error(describeError(body, response.status))
  }

  if (body === null) throw new Error('The server returned an empty response.')

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

  if (query) parameters.set('q', query)
  if (district) parameters.set('district', district)

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
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(farmer),
  })
}