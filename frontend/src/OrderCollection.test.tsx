import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import OrderCollection from './OrderCollection'

import {
  getCollection,
  startCollection,
} from './collectionApi'
import type { CollectionDetails } from './collectionApi'

import { getOrder } from './orderApi'
import type { ManagedOrder } from './orderApi'

vi.mock('./collectionApi', () => ({
  getCollection: vi.fn(),
  startCollection: vi.fn(),
}))

vi.mock('./orderApi', () => ({
  getOrder: vi.fn(),
}))

const startMock = vi.mocked(startCollection)
const collectionMock = vi.mocked(getCollection)
const orderMock = vi.mocked(getOrder)

const order: ManagedOrder = {
  id: 'order-1',
  quotationId: 'quote-1',
  buyerRequestId: 'buyer-1',
  status: 'CONFIRMED',
  buyerContactName: 'Test Buyer',
  approvalChannel: 'PHONE',
  approvalReference: 'Buyer approved.',
  approvedAt: '2026-01-01T09:00:00Z',
  recordedAt: '2026-01-01T10:00:00Z',
  recordedBy: 'staff-1',
  currency: 'LKR',
  quantityKg: 100,
  produceSubtotal: 20000,
  serviceFee: 100,
  deliveryFee: 200,
  total: 20300,
  version: 2,
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

const inProgress: ManagedOrder = {
  ...order,
  status: 'COLLECTION_IN_PROGRESS',
  version: 3,
}

const details: CollectionDetails = {
  id: 'collection-1',
  orderId: 'order-1',
  status: 'COLLECTION_IN_PROGRESS',
  orderVersion: 3,
  startedBy: 'staff-2',
  startedAt: '2026-01-02T04:30:00Z',
  reference: 'Pickup arranged with farmer.',
}

async function fillForm(user: ReturnType<typeof userEvent.setup>) {
  await user.type(
    screen.getByLabelText('Collection arrangements or reference'),
    '  Pickup arranged with farmer.  ',
  )

  await user.click(screen.getByRole('checkbox', {
    name: 'I confirm collection arrangements are ready.',
  }))
}

beforeEach(() => {
  vi.resetAllMocks()
  startMock.mockResolvedValue(details)
  collectionMock.mockResolvedValue(details)
  orderMock.mockResolvedValue(inProgress)
})

describe('Order collection', () => {
  it('requires arrangements and explicit confirmation', async () => {
    const user = userEvent.setup()

    render(
      <OrderCollection order={order} onChanged={vi.fn()} />,
    )

    const button = screen.getByRole('button', {
      name: 'Start collection',
    })

    expect(button).toBeDisabled()

    await user.type(
      screen.getByLabelText('Collection arrangements or reference'),
      'Pickup arranged.',
    )

    expect(button).toBeDisabled()
    expect(startMock).not.toHaveBeenCalled()
  })

  it('starts using the reviewed version and displays the saved audit', async () => {
    const user = userEvent.setup()
    const onChanged = vi.fn()

    render(
      <OrderCollection order={order} onChanged={onChanged} />,
    )

    await fillForm(user)

    await user.click(screen.getByRole('button', {
      name: 'Start collection',
    }))

    expect(startMock).toHaveBeenCalledExactlyOnceWith(
      'order-1',
      {
        version: 2,
        reference: 'Pickup arranged with farmer.',
      },
    )

    expect(await screen.findByText('Collection started'))
      .toBeInTheDocument()

    expect(screen.getByRole('region', {
      name: 'Order collection',
    })).toHaveTextContent(
      'Collection reference: Pickup arranged with farmer.',
    )

    await waitFor(() => {
      expect(onChanged).toHaveBeenCalledWith(inProgress)
    })
  })

  it('loads the persisted collection record when reopened', async () => {
    render(
      <OrderCollection order={inProgress} onChanged={vi.fn()} />,
    )

    expect(await screen.findByText('Collection started'))
      .toBeInTheDocument()

    expect(collectionMock).toHaveBeenCalledWith(
      'order-1',
      expect.any(AbortSignal),
    )

    expect(screen.queryByRole('button', {
      name: 'Start collection',
    })).not.toBeInTheDocument()
  })

  it('preserves notes and requires a reload after failure', async () => {
    const user = userEvent.setup()
    const onChanged = vi.fn()

    startMock.mockRejectedValueOnce(
      new Error('Order has changed. Reload before starting collection.'),
    )

    orderMock.mockResolvedValueOnce({
      ...order,
      version: 3,
    })

    render(
      <OrderCollection order={order} onChanged={onChanged} />,
    )

    await fillForm(user)

    await user.click(screen.getByRole('button', {
      name: 'Start collection',
    }))

    expect(await screen.findByRole('alert'))
      .toHaveTextContent('Order has changed.')

    expect(screen.getByRole('button', {
      name: 'Start collection',
    })).toBeDisabled()

    await user.click(screen.getByRole('button', {
      name: 'Reload collection status',
    }))

    await waitFor(() => {
      expect(screen.getByRole('checkbox')).not.toBeChecked()
    })

    expect(screen.getByLabelText('Collection arrangements or reference'))
      .toHaveValue('  Pickup arranged with farmer.  ')

    expect(startMock).toHaveBeenCalledTimes(1)

    expect(onChanged).toHaveBeenCalledWith({
      ...order,
      version: 3,
    })
  })

  it('retains the successful start when refreshing the order fails', async () => {
    const user = userEvent.setup()

    orderMock.mockRejectedValueOnce(new Error('Connection lost'))

    render(
      <OrderCollection order={order} onChanged={vi.fn()} />,
    )

    await fillForm(user)

    await user.click(screen.getByRole('button', {
      name: 'Start collection',
    }))

    expect(await screen.findByText('Collection started'))
      .toBeInTheDocument()

    expect(await screen.findByText(
      'Collection started. Reload collection status to update the order display.',
    )).toBeInTheDocument()

    expect(screen.queryByRole('button', {
      name: 'Start collection',
    })).not.toBeInTheDocument()

    expect(startMock).toHaveBeenCalledTimes(1)
  })

  it('blocks collection when a reservation is no longer active', () => {
    render(
      <OrderCollection
        order={{
          ...order,
          reservations: order.reservations.map((reservation) => ({
            ...reservation,
            status: 'RELEASED',
          })),
        }}
        onChanged={vi.fn()}
      />,
    )

    expect(screen.getByRole('button', {
      name: 'Start collection',
    })).toBeDisabled()

    expect(startMock).not.toHaveBeenCalled()
  })
})