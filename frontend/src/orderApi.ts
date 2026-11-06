import { request } from './http'

export type ApprovalChannel =
  | 'PHONE'
  | 'WHATSAPP'
  | 'EMAIL'
  | 'IN_PERSON'

export type OrderStatus =
  | 'CONFIRMED'
  | 'COLLECTION_IN_PROGRESS'
  | 'COLLECTED'
  | 'OUT_FOR_DELIVERY'
  | 'DELIVERED'
  | 'CANCELLED'

export interface ApproveOrderRequest {
  quotationVersion: number
  buyerContactName: string
  channel: ApprovalChannel
  reference: string
  approvedAt: string
}

export interface OrderReservation {
  id: string
  quotationLineId: string
  harvestListingId: string
  farmerName: string
  cropName: string
  quantityKg: number
  status: 'ACTIVE' | 'RELEASED' | 'CONSUMED'
}

export interface ManagedOrder {
  id: string
  quotationId: string
  buyerRequestId: string
  status: OrderStatus
  buyerContactName: string
  approvalChannel: ApprovalChannel
  approvalReference: string
  approvedAt: string
  recordedAt: string
  recordedBy: string
  currency: 'LKR'
  quantityKg: number
  produceSubtotal: number
  serviceFee: number
  deliveryFee: number
  total: number
  version: number
  reservations: OrderReservation[]
}

export interface OrderSummary {
  id: string
  quotationId: string
  buyerName: string
  district: string
  requiredBy: string
  status: OrderStatus
  currency: 'LKR'
  quantityKg: number
  total: number
  recordedAt: string
}

export interface OrdersPage {
  content: OrderSummary[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  hasNext: boolean
  hasPrevious: boolean
}

export function approveOrder(
  quotationId: string,
  approval: ApproveOrderRequest,
): Promise<ManagedOrder> {
  return request<ManagedOrder>(
    `/api/v1/quotations/${encodeURIComponent(quotationId)}/approve`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        quotationVersion: approval.quotationVersion,
        buyerContactName: approval.buyerContactName.trim(),
        channel: approval.channel,
        reference: approval.reference.trim(),
        approvedAt: approval.approvedAt,
      }),
    },
  )
}

export function getOrder(
  id: string,
  signal?: AbortSignal,
): Promise<ManagedOrder> {
  return request<ManagedOrder>(
    `/api/v1/orders/${encodeURIComponent(id)}`,
    { signal },
  )
}

export function listOrders(
  page = 0,
  status: OrderStatus | '' = '',
  signal?: AbortSignal,
): Promise<OrdersPage> {
  const query = new URLSearchParams({
    page: String(page),
    size: '20',
  })

  if (status) query.set('status', status)

  return request<OrdersPage>(
    `/api/v1/orders?${query}`,
    { signal },
  )
}