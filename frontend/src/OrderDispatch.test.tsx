import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {
  afterEach,
  beforeEach,
  describe,
  expect,
  it,
  vi,
} from 'vitest'

import OrderDispatch from './OrderDispatch'
import { ApiError } from './http'
import { getOrder, type ManagedOrder } from './orderApi'
import {
  dispatchOrder,
  getDispatchDetails,
  type DispatchDetails,
} from './dispatchApi'

vi.mock('./orderApi', () => ({
  getOrder: vi.fn(),
}))

vi.mock('./dispatchApi', () => ({
  dispatchOrder: vi.fn(),
  getDispatchDetails: vi.fn(),
}))

const dispatchMock = vi.mocked(dispatchOrder)
const detailsMock = vi.mocked(getDispatchDetails)
const orderMock = vi.mocked(getOrder)

const order: ManagedOrder = {
  id: 'order-1',
  quotationId: 'quote-1',
  buyerRequestId: 'buyer-1',
  status: 'COLLECTED',
  buyerContactName: 'Test Buyer',
  approvalChannel: 'PHONE',
  approvalReference: 'Buyer approved',
  approvedAt: '2020-01-01T04:00:00Z',
  recordedAt: '2020-01-01T04:01:00Z',
  recordedBy: 'staff-1',
  currency: 'LKR',
  quantityKg: 100,
  produceSubtotal: 20000,
  serviceFee: 100,
  deliveryFee: 200,
  total: 20300,
  version: 7,
  reservations: [
    {
      id: 'reservation-1',
      quotationLineId: 'line-1',
      harvestListingId: 'harvest-1',
      farmerName: 'Test Farmer',
      cropName: 'Tomato',
      quantityKg: 100,
      status: 'CONSUMED',
    },
  ],
}

const dispatchedOrder: ManagedOrder = {
  ...order,
  status: 'OUT_FOR_DELIVERY',
  version: 8,
}

const audit: DispatchDetails = {
  id: 'dispatch-1',
  orderId: order.id,
  status: 'OUT_FOR_DELIVERY',
  orderVersion: 8,
  dispatchedBy: 'staff-1',
  dispatchedAt: '2020-01-02T04:30:00Z',
  reference: 'Driver pickup 42',
}

beforeEach(() => {
  vi.resetAllMocks()
  dispatchMock.mockResolvedValue(audit)
  detailsMock.mockResolvedValue(audit)
  orderMock.mockResolvedValue(dispatchedOrder)
})

afterEach(cleanup)

function openScreen(current = order) {
  const onChanged = vi.fn()

  render(
    <OrderDispatch
      order={current}
      onChanged={onChanged}
    />,
  )

  return {
    onChanged,
    user: userEvent.setup(),
  }
}

async function fillAndConfirm(
  user: ReturnType<typeof userEvent.setup>,
) {
  fireEvent.change(screen.getByLabelText('Dispatch reference'), {
    target: { value: '  Driver pickup 42  ' },
  })

  await user.click(
    screen.getByRole('checkbox', {
      name: 'I confirm the collected goods were handed over for delivery.',
    }),
  )
}

describe('Order dispatch', () => {
  it('requires a reference and explicit handover confirmation', async () => {
    const { user } = openScreen()

    const submit = screen.getByRole('button', {
      name: 'Dispatch order',
    })

    expect(submit).toBeDisabled()

    fireEvent.change(screen.getByLabelText('Dispatch reference'), {
      target: { value: 'Driver pickup 42' },
    })

    expect(submit).toBeDisabled()

    await user.click(
      screen.getByRole('checkbox', {
        name: 'I confirm the collected goods were handed over for delivery.',
      }),
    )

    expect(submit).toBeEnabled()
    expect(dispatchMock).not.toHaveBeenCalled()
  })

  it('sends the reviewed version and displays the saved audit', async () => {
    const { user, onChanged } = openScreen()

    await fillAndConfirm(user)
    await user.click(
      screen.getByRole('button', { name: 'Dispatch order' }),
    )

    await waitFor(() => {
      expect(dispatchMock).toHaveBeenCalledWith(order.id, {
        version: 7,
        reference: 'Driver pickup 42',
      })
    })

    expect(
      await screen.findByRole('heading', {
        name: 'Dispatch recorded',
      }),
    ).toBeVisible()

    expect(screen.getByText('Dispatch ID: dispatch-1')).toBeVisible()

    await waitFor(() => {
      expect(onChanged).toHaveBeenCalledWith(dispatchedOrder)
    })

    expect(dispatchMock).toHaveBeenCalledTimes(1)
    expect(
      screen.queryByRole('button', { name: 'Dispatch order' }),
    ).not.toBeInTheDocument()
  })

  it('blocks another submission after failure until a reload succeeds', async () => {
    dispatchMock.mockRejectedValueOnce(
      new ApiError('Order has changed.', 409),
    )

    orderMock.mockResolvedValue({
      ...order,
      version: 8,
    })

    const { user, onChanged } = openScreen()

    await fillAndConfirm(user)
    await user.click(
      screen.getByRole('button', { name: 'Dispatch order' }),
    )

    expect(
      await screen.findByText('Order has changed.'),
    ).toBeVisible()

    expect(
      screen.getByRole('button', { name: 'Dispatch order' }),
    ).toBeDisabled()

    expect(screen.getByLabelText('Dispatch reference'))
      .toHaveValue('  Driver pickup 42  ')

    await user.click(
      screen.getByRole('button', {
        name: 'Reload dispatch status',
      }),
    )

    await waitFor(() => {
      expect(onChanged).toHaveBeenCalledWith({
        ...order,
        version: 8,
      })
    })

    expect(
      screen.getByRole('checkbox'),
    ).not.toBeChecked()

    expect(dispatchMock).toHaveBeenCalledTimes(1)
  })

  it('keeps a successful dispatch visible when order refresh fails', async () => {
    orderMock.mockRejectedValueOnce(
      new Error('Order refresh failed.'),
    )

    const { user } = openScreen()

    await fillAndConfirm(user)
    await user.click(
      screen.getByRole('button', { name: 'Dispatch order' }),
    )

    expect(
      await screen.findByText(
        'Dispatch recorded. The order could not be refreshed. Reload dispatch status to check the latest order details.',
      ),
    ).toBeVisible()

    expect(
      screen.getByRole('heading', { name: 'Dispatch recorded' }),
    ).toBeVisible()

    expect(
      screen.queryByRole('button', { name: 'Dispatch order' }),
    ).not.toBeInTheDocument()

    expect(dispatchMock).toHaveBeenCalledTimes(1)
  })

  it('loads the saved audit for an order already out for delivery', async () => {
    openScreen(dispatchedOrder)

    expect(
      await screen.findByText('Dispatch ID: dispatch-1'),
    ).toBeVisible()

    expect(detailsMock).toHaveBeenCalledWith(
      order.id,
      expect.any(AbortSignal),
    )

    expect(dispatchMock).not.toHaveBeenCalled()
  })

  it('does not show dispatch controls before collection is complete', () => {
    openScreen({
      ...order,
      status: 'COLLECTION_IN_PROGRESS',
    })

    expect(
      screen.queryByRole('region', { name: 'Order dispatch' }),
    ).not.toBeInTheDocument()

    expect(dispatchMock).not.toHaveBeenCalled()
    expect(detailsMock).not.toHaveBeenCalled()
  })
})