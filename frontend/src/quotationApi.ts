import { request } from './http'

export type QuotationStatus =
  | 'DRAFT'
  | 'ISSUED'
  | 'ACCEPTED'
  | 'REJECTED'
  | 'EXPIRED'
  | 'CANCELLED'

export interface QuotationLine {
  id: string
  lineNumber: number
  harvestListingId: string
  farmerId: string
  farmerName: string
  cropName: string
  quantityKg: number
  farmerPricePerKg: number
  lineSubtotal: number
  priceConfirmed: boolean
}

export interface Quotation {
  id: string
  buyerRequestId: string
  createdBy: string
  buyerName: string
  buyerPhone: string
  district: string
  requiredBy: string
  deliveryAddress: string | null
  status: QuotationStatus
  currency: 'LKR'
  quantityKg: number
  produceSubtotal: number
  serviceFee: number
  deliveryFee: number
  total: number
  priceConfirmationNeeded: boolean
  version: number
  createdAt: string
  updatedAt: string
  issuedBy: string | null
  issuedAt: string | null
  expiresAt: string | null
  lines: QuotationLine[]
}

export interface CreateQuotationLine {
  harvestListingId: string
  quantityKg: number
  farmerPricePerKg: number
  priceConfirmed: boolean
}

export interface CreateQuotationDraft {
  buyerRequestId: string
  serviceFee: number
  deliveryFee: number
  deliveryAddress: string | null
  lines: CreateQuotationLine[]
}

export interface IssueQuotationRequest {
  version: number
  expiresAt: string
  confirmedLineIds: string[]
}

export function createQuotationDraft(
  draft: CreateQuotationDraft,
  signal?: AbortSignal,
): Promise<Quotation> {
  const body: CreateQuotationDraft = {
    buyerRequestId: draft.buyerRequestId,
    serviceFee: draft.serviceFee,
    deliveryFee: draft.deliveryFee,
    deliveryAddress: draft.deliveryAddress?.trim() || null,
    lines: draft.lines.map((line) => ({
      harvestListingId: line.harvestListingId,
      quantityKg: line.quantityKg,
      farmerPricePerKg: line.farmerPricePerKg,
      priceConfirmed: line.priceConfirmed,
    })),
  }

  return request<Quotation>('/api/v1/quotations', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(body),
    signal,
  })
}

export function getQuotation(
  id: string,
  signal?: AbortSignal,
): Promise<Quotation> {
  return request<Quotation>(
    `/api/v1/quotations/${encodeURIComponent(id)}`,
    { signal },
  )
}

export function issueQuotation(
  id: string,
  issue: IssueQuotationRequest,
  signal?: AbortSignal,
): Promise<Quotation> {
  const body: IssueQuotationRequest = {
    version: issue.version,
    expiresAt: issue.expiresAt,
    confirmedLineIds: [...issue.confirmedLineIds],
  }

  return request<Quotation>(
    `/api/v1/quotations/${encodeURIComponent(id)}/issue`,
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(body),
      signal,
    },
  )
}