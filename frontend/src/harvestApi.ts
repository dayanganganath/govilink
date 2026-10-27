import { ApiError } from './api'
import type { FarmerPage } from './api'

export interface HarvestListing {
  id: string
  farmerId: string
  farmerName: string
  district: string
  cropName: string
  quantityKg: number
  expectedPricePerKg: number | null
  availableFrom: string
  status: 'AVAILABLE' | 'SOLD' | 'CANCELLED'
  version: number
  createdAt: string
  updatedAt: string
}

export type HarvestCloseStatus = 'SOLD' | 'CANCELLED'

export interface HarvestPage {
  content: HarvestListing[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  hasNext: boolean
  hasPrevious: boolean
}

export interface CreateHarvestRequest {
  farmerId: string
  cropName: string
  quantityKg: number
  expectedPricePerKg: number | null
  availableFrom: string
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

export function listHarvests(
  page: number,
  signal: AbortSignal,
): Promise<HarvestPage> {
  const parameters = new URLSearchParams({
    page: String(page),
    size: '10',
  })

  return request<HarvestPage>(
    `/api/v1/harvest-listings?${parameters}`,
    { signal },
  )
}

export function createHarvest(
  body: CreateHarvestRequest,
): Promise<HarvestListing> {
  return request<HarvestListing>('/api/v1/harvest-listings', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
}

export function updateHarvestStatus(
  id: string,
  status: HarvestCloseStatus,
  version: number,
): Promise<HarvestListing> {
  return request<HarvestListing>(
    `/api/v1/harvest-listings/${encodeURIComponent(id)}/status`,
    {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ status, version }),
    },
  )
}

export function searchActiveFarmers(
  query: string,
  page: number,
  signal: AbortSignal,
): Promise<FarmerPage> {
  const parameters = new URLSearchParams({
    active: 'true',
    page: String(page),
    size: '10',
  })

  if (query) parameters.set('q', query)

  return request<FarmerPage>(
    `/api/v1/farmers/search?${parameters}`,
    { signal },
  )
}