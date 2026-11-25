import { useState } from 'react'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import OrderCancellation from './OrderCancellation'
import { getOrder, type ManagedOrder } from './orderApi'
import {
  cancelOrder,
  getCancellationDetails,
  type CancellationDetails,
} from './cancellationApi'

vi.mock('./orderApi', () => ({
  getOrder: vi.fn(),
}))

vi.mock('./cancellationApi', () => ({
  cancelOrder: vi.fn(),
  getCancellationDetails: vi.fn(),
}))

const orderMock = vi.mocked(getOrder)
const cancelMock = vi.mocked(cancelOrder)
const auditMock = vi.mocked(getCancellationDetails)

const order: ManagedOrder = {
  id: 'order-1',
  quotationId: 'quote-1',
  buyerRequestId: 'buyer-1',
  status: 'CONFIRMED',
  buyerContactName: 'Test Buyer',
  approvalChannel: 'PHONE',
  approvalReference: 'Buyer confirmed.',
  approvedAt: '2026-01-01T04:00:00Z',
  recordedAt: '2026-01-01T04:01:00Z',
  recordedBy: 'staff-1',
  currency: 'LKR',
  quantityKg: 100,
  produceSubtotal: 20000,
  serviceFee: 100,
  deliveryFee: 200,
  total: 20300,
  version: 3,
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

const cancelled: ManagedOrder = {
  ...order,
  status: 'CANCELLED',
  version: 4,
  reservations: [{
    ...order.reservations[0],
    status: 'RELEASED',
  }],
}

const audit: CancellationDetails = {
  id: 'cancellation-1',
  orderId: order.id,
  status: 'CANCELLED',
  orderVersion: 4,
  previousStatus: 'CONFIRMED',
  cancelledBy: 'staff-1',
  cancelledAt: '2026-01-02T04:00:00Z',
  reason: 'Buyer withdrew the order.',
  releasedQuantityKg: 100,
}

function Harness({ initial = order }: { initial?: ManagedOrder }) {
  const [current, setCurrent] = useState(initial)

  return <OrderCancellation order={current} onChanged={setCurrent} />
}

beforeEach(() => {
  vi.resetAllMocks()
  cancelMock.mockResolvedValue(audit)
  orderMock.mockResolvedValue(cancelled)
  auditMock.mockResolvedValue(audit)
})

afterEach(cleanup)

async function fillForm() {
  const user = userEvent.setup()

  await user.type(
    screen.getByLabelText('Cancellation reason'),
    '  Buyer withdrew the order.  ',
  )

  await user.click(screen.getByRole('checkbox'))

  return user
}

describe('Order cancellation', () => {
  it('requires a reason and explicit confirmation', async () => {
    const user = userEvent.setup()
    render(<Harness />)

    const button = screen.getByRole('button', { name: 'Cancel order' })

    expect(button).toBeDisabled()

    await user.type(
      screen.getByLabelText('Cancellation reason'),
      'Buyer withdrew the order.',
    )

    expect(button).toBeDisabled()

    await user.click(screen.getByRole('checkbox'))

    expect(button).toBeEnabled()
    expect(cancelMock).not.toHaveBeenCalled()
  })

  it('submits the reviewed version and shows the saved audit', async () => {
    render(<Harness />)
    const user = await fillForm()

    await user.click(screen.getByRole('button', { name: 'Cancel order' }))

    await waitFor(() => expect(cancelMock).toHaveBeenCalledWith('order-1', {
      version: 3,
      reason: 'Buyer withdrew the order.',
    }))

    expect(await screen.findByText('cancellation-1')).toBeInTheDocument()
    expect(screen.getByText('100 kg')).toBeInTheDocument()
    expect(screen.getByText('Buyer withdrew the order.')).toBeInTheDocument()
    expect(screen.queryByLabelText('Cancellation reason')).not.toBeInTheDocument()
  })

  it('clears confirmation when the reason changes', async () => {
    render(<Harness />)
    const user = await fillForm()

    await user.type(screen.getByLabelText('Cancellation reason'), ' Updated.')

    expect(screen.getByRole('checkbox')).not.toBeChecked()
    expect(screen.getByRole('button', { name: 'Cancel order' })).toBeDisabled()
  })

  it('preserves the reason and requires a reload after failure', async () => {
    cancelMock.mockRejectedValueOnce(new Error('Order has changed.'))
    orderMock.mockResolvedValueOnce({ ...order, version: 5 })

    render(<Harness />)
    const user = await fillForm()

    await user.click(screen.getByRole('button', { name: 'Cancel order' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Order has changed.')
    expect(screen.getByLabelText('Cancellation reason'))
      .toHaveValue('  Buyer withdrew the order.  ')
    expect(screen.getByLabelText('Cancellation reason')).toBeDisabled()

    await user.click(screen.getByRole('button', {
      name: 'Reload cancellation status',
    }))

    await waitFor(() => {
      expect(screen.getByLabelText('Cancellation reason')).toBeEnabled()
    })

    expect(screen.getByRole('checkbox')).not.toBeChecked()

    await user.click(screen.getByRole('checkbox'))
    await user.click(screen.getByRole('button', { name: 'Cancel order' }))

    await waitFor(() => expect(cancelMock).toHaveBeenLastCalledWith('order-1', {
      version: 5,
      reason: 'Buyer withdrew the order.',
    }))
  })

  it('retains a successful cancellation if refreshing the order fails', async () => {
    orderMock.mockRejectedValueOnce(new Error('Network unavailable.'))

    render(<Harness />)
    const user = await fillForm()

    await user.click(screen.getByRole('button', { name: 'Cancel order' }))

    expect(await screen.findByText('cancellation-1')).toBeInTheDocument()

    await waitFor(() => {
      expect(screen.getByRole('status')).toHaveTextContent(
        'Order cancelled and stock released.',
      )
    })

    expect(screen.queryByLabelText('Cancellation reason')).not.toBeInTheDocument()
    expect(cancelMock).toHaveBeenCalledTimes(1)
  })

  it('loads audit details for an already cancelled order', async () => {
    render(<Harness initial={cancelled} />)

    expect(await screen.findByText('cancellation-1')).toBeInTheDocument()
    expect(auditMock).toHaveBeenCalledWith(
      'order-1',
      expect.any(AbortSignal),
    )
    expect(cancelMock).not.toHaveBeenCalled()
    expect(screen.queryByLabelText('Cancellation reason')).not.toBeInTheDocument()
  })

  it('shows a receipt rejection and blocks another submission', async () => {
    cancelMock.mockRejectedValueOnce(
      new Error('This order has received goods and cannot be cancelled.'),
    )

    render(<Harness initial={{ ...order, status: 'COLLECTION_IN_PROGRESS' }} />)
    const user = await fillForm()

    await user.click(screen.getByRole('button', { name: 'Cancel order' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'This order has received goods',
    )
    expect(screen.getByRole('button', { name: 'Cancel order' })).toBeDisabled()
    expect(cancelMock).toHaveBeenCalledTimes(1)
  })

  it('hides cancellation controls for delivered orders', () => {
    render(<Harness initial={{ ...order, status: 'DELIVERED' }} />)

    expect(screen.queryByRole('region', {
      name: 'Order cancellation',
    })).not.toBeInTheDocument()

    expect(auditMock).not.toHaveBeenCalled()
    expect(cancelMock).not.toHaveBeenCalled()
  })
})