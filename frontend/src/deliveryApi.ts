import { request } from './http'
import type { OrderStatus } from './orderApi'

export interface ConfirmDeliveryRequest {
  version: number
  receiverName: string
  deliveredAt: string
  reference: string
}

export interface DeliveryDetails {
  id: string
  orderId: string
  status: OrderStatus
  orderVersion: number
  receiverName: string
  deliveredAt: string
  recordedBy: string
  recordedAt: string
  reference: string
}

export function confirmDelivery(
  orderId: string,
  payload: ConfirmDeliveryRequest,
): Promise<DeliveryDetails> {
  return request<DeliveryDetails>(
    `/api/v1/orders/${encodeURIComponent(orderId)}/delivery`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        version: payload.version,
        receiverName: payload.receiverName.trim(),
        deliveredAt: payload.deliveredAt,
        reference: payload.reference.trim(),
      }),
    },
  )
}

export function getDeliveryDetails(
  orderId: string,
  signal?: AbortSignal,
): Promise<DeliveryDetails> {
  return request<DeliveryDetails>(
    `/api/v1/orders/${encodeURIComponent(orderId)}/delivery`,
    { signal },
  )
}