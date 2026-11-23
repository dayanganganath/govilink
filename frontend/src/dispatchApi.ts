import { request } from './http'
import type { OrderStatus } from './orderApi'

export interface DispatchRequest {
  version: number
  reference: string
}

export interface DispatchDetails {
  id: string
  orderId: string
  status: OrderStatus
  orderVersion: number
  dispatchedBy: string
  dispatchedAt: string
  reference: string
}

export function dispatchOrder(
  orderId: string,
  payload: DispatchRequest,
): Promise<DispatchDetails> {
  return request<DispatchDetails>(
    `/api/v1/orders/${encodeURIComponent(orderId)}/dispatch`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        version: payload.version,
        reference: payload.reference.trim(),
      }),
    },
  )
}

export function getDispatchDetails(
  orderId: string,
  signal?: AbortSignal,
): Promise<DispatchDetails> {
  return request<DispatchDetails>(
    `/api/v1/orders/${encodeURIComponent(orderId)}/dispatch`,
    { signal },
  )
}