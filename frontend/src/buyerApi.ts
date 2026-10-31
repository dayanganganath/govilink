import { ApiError } from './api'

export interface BuyerRequest {
  id: string
  buyerName: string
  phone: string
  cropName: string
  quantityKg: number
  district: string
  requiredBy: string
  maxPricePerKg: number | null
  status: 'OPEN' | 'FULFILLED' | 'CANCELLED'
  version: number
  createdAt: string
  updatedAt: string
}

export interface BuyerRequestPage {
  content: BuyerRequest[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  hasNext: boolean
  hasPrevious: boolean
}

export interface CreateBuyerRequest {
  buyerName: string
  phone: string
  cropName: string
  quantityKg: number
  district: string
  requiredBy: string
  maxPricePerKg: number | null
}

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
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
    const fieldErrors: Record<string, string> = {}

    if (isObject(body) && isObject(body.errors)) {
      for (const [field, value] of Object.entries(body.errors)) {
        if (typeof value === 'string') fieldErrors[field] = value
      }
    }

    const messages = Object.entries(fieldErrors)
      .map(([field, value]) => `${field}: ${value}`)

    const detail = isObject(body) && typeof body.detail === 'string'
      ? body.detail
      : `Request failed (${response.status}). Please try again.`

    throw new ApiError(
      messages.length ? messages.join(' · ') : detail,
      response.status,
      fieldErrors,
    )
  }

  if (body === null) {
    throw new Error('The server returned an empty response.')
  }

  return body as T
}

export function listBuyerRequests(
  page: number,
  signal: AbortSignal,
): Promise<BuyerRequestPage> {
  const parameters = new URLSearchParams({
    page: String(page),
    size: '10',
  })

  return request<BuyerRequestPage>(
    `/api/v1/buyer-requests?${parameters}`,
    { signal },
  )
}

export function createBuyerRequest(
  body: CreateBuyerRequest,
): Promise<BuyerRequest> {
  return request<BuyerRequest>('/api/v1/buyer-requests', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
}