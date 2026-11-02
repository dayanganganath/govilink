import { request } from './http'

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