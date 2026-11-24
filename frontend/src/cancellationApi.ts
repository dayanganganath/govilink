import { request } from './http'
import type { OrderStatus } from './orderApi'

export interface CancelOrderRequest {
  version: number
  reason: string
}

export interface CancellationDetails {
  id: string
  orderId: string
  status: OrderStatus
  orderVersion: number
  previousStatus: OrderStatus
  cancelledBy: string
  cancelledAt: string
  reason: string
  releasedQuantityKg: number
}

export function cancelOrder(
  orderId: string,
  payload: CancelOrderRequest,
): Promise<CancellationDetails> {
  return request<CancellationDetails>(
    `/api/v1/orders/${encodeURIComponent(orderId)}/cancellation`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        version: payload.version,
        reason: payload.reason.trim(),
      }),
    },
  )
}

export function getCancellationDetails(
  orderId: string,
  signal?: AbortSignal,
): Promise<CancellationDetails> {
  return request<CancellationDetails>(
    `/api/v1/orders/${encodeURIComponent(orderId)}/cancellation`,
    { signal },
  )
}