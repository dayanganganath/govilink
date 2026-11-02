import { request } from './http'

export { ApiError } from './http'

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
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(farmer),
    },
  )
}