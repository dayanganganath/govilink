import { request } from './http'
import type { OrderStatus } from './orderApi'

export interface CreateGoodsReceipt {
  requestId: string
  version: number
  reservationId: string
  quantityKg: number
  receivedAt: string
  reference: string
}

export interface GoodsReceipt {
  id: string
  orderId: string
  reservationId: string
  quantityKg: number
  receivedAt: string
  recordedAt: string
  receivedBy: string
  reference: string
  orderStatus: OrderStatus
  orderVersion: number
}

export interface ReceiptReservation {
  id: string
  farmerName: string
  cropName: string
  quantityKg: number
  collectedQuantityKg: number
  remainingQuantityKg: number
  status: 'ACTIVE' | 'RELEASED' | 'CONSUMED'
}

export interface GoodsReceiptPage {
  content: GoodsReceipt[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  hasNext: boolean
  hasPrevious: boolean
  orderStatus: OrderStatus
  orderVersion: number
  reservations: ReceiptReservation[]
}

export function recordGoodsReceipt(
  orderId: string,
  receipt: CreateGoodsReceipt,
): Promise<GoodsReceipt> {
  return request<GoodsReceipt>(
    `/api/v1/orders/${encodeURIComponent(orderId)}/receipts`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(receipt),
    },
  )
}

export function listGoodsReceipts(
  orderId: string,
  page = 0,
  signal?: AbortSignal,
): Promise<GoodsReceiptPage> {
  const query = new URLSearchParams({
    page: String(page),
    size: '20',
  })

  return request<GoodsReceiptPage>(
    `/api/v1/orders/${encodeURIComponent(orderId)}/receipts?${query}`,
    { signal },
  )
}

export function getGoodsReceipt(
  orderId: string,
  receiptId: string,
): Promise<GoodsReceipt> {
  return request<GoodsReceipt>(
    `/api/v1/orders/${encodeURIComponent(orderId)}/receipts/${encodeURIComponent(receiptId)}`,
  )
}