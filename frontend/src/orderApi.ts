import { request } from './http'

export type ApprovalChannel =
  | 'PHONE'
  | 'WHATSAPP'
  | 'EMAIL'
  | 'IN_PERSON'

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
  status:
    | 'CONFIRMED'
    | 'COLLECTION_IN_PROGRESS'
    | 'COLLECTED'
    | 'OUT_FOR_DELIVERY'
    | 'DELIVERED'
    | 'CANCELLED'
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

export function approveOrder(
  quotationId: string,
  approval: ApproveOrderRequest,
): Promise<ManagedOrder> {
  return request<ManagedOrder>(
    `/api/v1/quotations/${encodeURIComponent(quotationId)}/approve`,
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
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

export function getOrder(id: string): Promise<ManagedOrder> {
  return request<ManagedOrder>(
    `/api/v1/orders/${encodeURIComponent(id)}`,
  )
}