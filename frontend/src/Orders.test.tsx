import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import Orders from './Orders'
import { getOrder, listOrders } from './orderApi'
import type { ManagedOrder, OrdersPage } from './orderApi'

vi.mock('./orderApi', () => ({
  listOrders: vi.fn(),
  getOrder: vi.fn(),
}))

const listMock = vi.mocked(listOrders)
const getMock = vi.mocked(getOrder)

const page: OrdersPage = {
  content: [{
    id: 'order-1',
    quotationId: 'quote-1',
    buyerName: 'Test Buyer',
    district: 'Matale',
    requiredBy: '2099-01-10',
    status: 'CONFIRMED',
    currency: 'LKR',
    quantityKg: 100,
    total: 23000,
    recordedAt: '2026-01-01T10:00:00Z',
  }],
  page: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
  hasNext: false,
  hasPrevious: false,
}

const order: ManagedOrder = {
  id: 'order-1',
  quotationId: 'quote-1',
  buyerRequestId: 'buyer-1',
  status: 'CONFIRMED',
  buyerContactName: 'Test Buyer',
  approvalChannel: 'PHONE',
  approvalReference: 'Buyer approved by phone.',
  approvedAt: '2026-01-01T09:00:00Z',
  recordedAt: '2026-01-01T10:00:00Z',
  recordedBy: 'staff-1',
  currency: 'LKR',
  quantityKg: 100,
  produceSubtotal: 20000,
  serviceFee: 1000,
  deliveryFee: 2000,
  total: 23000,
  version: 0,
  reservations: [{
    id: 'reservation-1',
    quotationLineId: 'line-1',
    harvestListingId: 'harvest-1',
    farmerName: 'Test Farmer',
    cropName: 'Tomato',
    quantityKg: 100,
    status: 'ACTIVE',
  }],
}

beforeEach(() => {
  vi.resetAllMocks()
  listMock.mockResolvedValue(page)
  getMock.mockResolvedValue(order)
})

describe('Orders workspace', () => {
  it('loads orders and opens saved reservation details', async () => {
    const user = userEvent.setup()

    render(<Orders />)

    await user.click(await screen.findByRole('button', {
      name: 'View order for Test Buyer',
    }))

    expect(await screen.findByText('Test Farmer')).toBeInTheDocument()
    expect(screen.getByText('Reference: Buyer approved by phone.')).toBeInTheDocument()
    expect(screen.getByRole('table', {
      name: 'Order stock reservations',
    })).toHaveTextContent('active')

    expect(getMock).toHaveBeenCalledWith(
      'order-1',
      expect.any(AbortSignal),
    )
  })

  it('retains the status filter when paging and refreshing', async () => {
    const user = userEvent.setup()

    listMock.mockResolvedValue({
      ...page,
      totalElements: 21,
      totalPages: 2,
      hasNext: true,
    })

    render(<Orders />)

    await screen.findByRole('table', { name: 'Managed orders' })

    await user.selectOptions(
      screen.getByLabelText('Order status'),
      'CONFIRMED',
    )

    await waitFor(() => {
      expect(listMock).toHaveBeenLastCalledWith(
        0, 'CONFIRMED', expect.any(AbortSignal),
      )
    })

    await user.click(await screen.findByRole('button', {
      name: 'Next orders',
    }))

    await waitFor(() => {
      expect(listMock).toHaveBeenLastCalledWith(
        1, 'CONFIRMED', expect.any(AbortSignal),
      )
    })

    await user.click(await screen.findByRole('button', {
      name: 'Refresh orders',
    }))

    await waitFor(() => {
      expect(listMock).toHaveBeenLastCalledWith(
        1, 'CONFIRMED', expect.any(AbortSignal),
      )
    })
  })

  it('retries a failed list request', async () => {
    const user = userEvent.setup()

    listMock.mockRejectedValueOnce(new Error('Connection unavailable'))

    render(<Orders />)

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Connection unavailable',
    )

    await user.click(screen.getByRole('button', {
      name: 'Retry orders',
    }))

    expect(await screen.findByRole('table', {
      name: 'Managed orders',
    })).toBeInTheDocument()
  })

  it('opens an order by ID without requiring a list selection', async () => {
    const user = userEvent.setup()

    render(<Orders />)

    await user.type(
      screen.getByLabelText('Saved order ID'),
      '  order-1  ',
    )

    await user.click(screen.getByRole('button', {
      name: 'Open saved order',
    }))

    expect(await screen.findByText('Test Farmer')).toBeInTheDocument()

    expect(getMock).toHaveBeenCalledWith(
      'order-1',
      expect.any(AbortSignal),
    )
  })
})