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

import OrderDelivery from './OrderDelivery'
import { ApiError } from './http'
import { getOrder, type ManagedOrder } from './orderApi'
import { getDispatchDetails } from './dispatchApi'
import {
  confirmDelivery,
  getDeliveryDetails,
  type DeliveryDetails,
} from './deliveryApi'

vi.mock('./orderApi', () => ({
  getOrder: vi.fn(),
}))

vi.mock('./dispatchApi', () => ({
  getDispatchDetails: vi.fn(),
}))

vi.mock('./deliveryApi', () => ({
  confirmDelivery: vi.fn(),
  getDeliveryDetails: vi.fn(),
}))

const orderMock = vi.mocked(getOrder)
const dispatchMock = vi.mocked(getDispatchDetails)
const confirmMock = vi.mocked(confirmDelivery)
const deliveryMock = vi.mocked(getDeliveryDetails)

const order: ManagedOrder = {
  id: 'order-1',
  quotationId: 'quote-1',
  buyerRequestId: 'buyer-1',
  status: 'OUT_FOR_DELIVERY',
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
  version: 8,
  reservations: [],
}

const deliveredOrder: ManagedOrder = {
  ...order,
  status: 'DELIVERED',
  version: 9,
}

const audit: DeliveryDetails = {
  id: 'delivery-1',
  orderId: order.id,
  status: 'DELIVERED',
  orderVersion: 9,
  receiverName: 'Test Buyer',
  deliveredAt: '2020-01-02T04:30:00.000Z',
  recordedAt: '2020-01-02T04:31:00.000Z',
  recordedBy: 'staff-1',
  reference: 'Delivery note 42',
}

beforeEach(() => {
  vi.resetAllMocks()

  dispatchMock.mockResolvedValue({
    id: 'dispatch-1',
    orderId: order.id,
    status: 'OUT_FOR_DELIVERY',
    orderVersion: 8,
    dispatchedBy: 'staff-1',
    dispatchedAt: '2020-01-02T04:00:00.000Z',
    reference: 'Driver pickup',
  })

  confirmMock.mockResolvedValue(audit)
  deliveryMock.mockResolvedValue(audit)
  orderMock.mockResolvedValue(deliveredOrder)
})

afterEach(cleanup)

function openScreen(current = order) {
  const onChanged = vi.fn()

  render(
    <OrderDelivery order={current} onChanged={onChanged} />,
  )

  return {
    user: userEvent.setup(),
    onChanged,
  }
}

async function fillForm(time = '2020-01-02T10:00:00') {
  const receiver = await screen.findByLabelText('Receiver name')

  fireEvent.change(receiver, {
    target: { value: '  Test Buyer  ' },
  })

  fireEvent.change(
    screen.getByLabelText(
      'Delivered date and time — Sri Lanka time',
    ),
    {
      target: { value: time },
    },
  )

  fireEvent.change(screen.getByLabelText('Delivery reference'), {
    target: { value: '  Delivery note 42  ' },
  })
}

async function confirmAndSubmit(
  user: ReturnType<typeof userEvent.setup>,
) {
  await user.click(
    screen.getByRole('checkbox', {
      name: 'I confirm the full order was received by the named receiver.',
    }),
  )

  await user.click(
    screen.getByRole('button', { name: 'Confirm delivery' }),
  )
}

describe('Order delivery', () => {
  it('requires explicit confirmation', async () => {
    const { user } = openScreen()

    await fillForm()

    const submit = screen.getByRole('button', {
      name: 'Confirm delivery',
    })

    expect(submit).toBeDisabled()

    await user.click(screen.getByRole('checkbox'))

    expect(submit).toBeEnabled()
    expect(confirmMock).not.toHaveBeenCalled()
  })

  it('submits the reviewed version and Sri Lanka delivery time', async () => {
    const { user, onChanged } = openScreen()

    await fillForm()
    await confirmAndSubmit(user)

    await waitFor(() => {
      expect(confirmMock).toHaveBeenCalledWith(order.id, {
        version: 8,
        receiverName: 'Test Buyer',
        deliveredAt: '2020-01-02T04:30:00.000Z',
        reference: 'Delivery note 42',
      })
    })

    expect(
      await screen.findByRole('heading', {
        name: 'Delivery confirmed',
      }),
    ).toBeVisible()

    await waitFor(() => {
      expect(onChanged).toHaveBeenCalledWith(deliveredOrder)
    })

    expect(confirmMock).toHaveBeenCalledTimes(1)
  })

  it('rejects a delivery time before dispatch', async () => {
    const { user } = openScreen()

    await fillForm('2020-01-02T09:00:00')
    await confirmAndSubmit(user)

    expect(
      await screen.findByText(
        'Delivery time cannot be earlier than dispatch.',
      ),
    ).toBeVisible()

    expect(confirmMock).not.toHaveBeenCalled()
  })

  it('requires a reload after failure and retains entered details', async () => {
    confirmMock.mockRejectedValueOnce(
      new ApiError('Order has changed.', 409),
    )

    orderMock.mockResolvedValue({
      ...order,
      version: 9,
    })

    const { user, onChanged } = openScreen()

    await fillForm()
    await confirmAndSubmit(user)

    expect(
      await screen.findByText('Order has changed.'),
    ).toBeVisible()

    expect(
      screen.getByRole('button', { name: 'Confirm delivery' }),
    ).toBeDisabled()

    expect(screen.getByLabelText('Receiver name'))
      .toHaveValue('  Test Buyer  ')

    expect(screen.getByLabelText('Delivery reference'))
      .toHaveValue('  Delivery note 42  ')

    await user.click(
      screen.getByRole('button', {
        name: 'Reload delivery status',
      }),
    )

    await waitFor(() => {
      expect(onChanged).toHaveBeenCalledWith({
        ...order,
        version: 9,
      })
    })

    expect(screen.getByRole('checkbox')).not.toBeChecked()
    expect(confirmMock).toHaveBeenCalledTimes(1)
  })

  it('retains saved delivery when refreshing the order fails', async () => {
    orderMock.mockRejectedValueOnce(
      new Error('Order refresh failed.'),
    )

    const { user } = openScreen()

    await fillForm()
    await confirmAndSubmit(user)

    expect(
      await screen.findByText(
        'Delivery confirmed. The order could not be refreshed. Reload delivery status to check the latest order details.',
      ),
    ).toBeVisible()

    expect(
      screen.getByRole('heading', {
        name: 'Delivery confirmed',
      }),
    ).toBeVisible()

    expect(
      screen.queryByRole('button', { name: 'Confirm delivery' }),
    ).not.toBeInTheDocument()

    expect(confirmMock).toHaveBeenCalledTimes(1)
  })

  it('loads saved details for an already delivered order', async () => {
    openScreen(deliveredOrder)

    expect(
      await screen.findByText('Delivery ID: delivery-1'),
    ).toBeVisible()

    expect(deliveryMock).toHaveBeenCalledWith(
      order.id,
      expect.any(AbortSignal),
    )

    expect(confirmMock).not.toHaveBeenCalled()
    expect(dispatchMock).not.toHaveBeenCalled()
  })

  it('hides delivery confirmation before dispatch', () => {
    openScreen({
      ...order,
      status: 'COLLECTED',
    })

    expect(
      screen.queryByRole('region', { name: 'Order delivery' }),
    ).not.toBeInTheDocument()

    expect(dispatchMock).not.toHaveBeenCalled()
    expect(confirmMock).not.toHaveBeenCalled()
  })
})