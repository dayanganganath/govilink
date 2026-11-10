import { request } from './http'
import type { OrderStatus } from './orderApi'

export interface CollectionDetails {
  id: string
  orderId: string
  status: OrderStatus
  orderVersion: number
  startedBy: string
  startedAt: string
  reference: string
}

export interface StartCollectionRequest {
  version: number
  reference: string
}

export function startCollection(
  orderId: string,
  details: StartCollectionRequest,
): Promise<CollectionDetails> {
  return request<CollectionDetails>(
    `/api/v1/orders/${encodeURIComponent(orderId)}/collection/start`,
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        version: details.version,
        reference: details.reference.trim(),
      }),
    },
  )
}

export function getCollection(
  orderId: string,
  signal?: AbortSignal,
): Promise<CollectionDetails> {
  return request<CollectionDetails>(
    `/api/v1/orders/${encodeURIComponent(orderId)}/collection`,
    { signal },
  )
}