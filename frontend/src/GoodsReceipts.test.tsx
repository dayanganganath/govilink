import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import GoodsReceipts from './GoodsReceipts'
import { ApiError } from './http'
import { getOrder, type ManagedOrder } from './orderApi'
import {
  getGoodsReceipt,
  listGoodsReceipts,
  recordGoodsReceipt,
  type CreateGoodsReceipt,
  type GoodsReceipt,
  type GoodsReceiptPage,
} from './goodsReceiptApi'

vi.mock('./goodsReceiptApi', () => ({
  getGoodsReceipt: vi.fn(),
  listGoodsReceipts: vi.fn(),
  recordGoodsReceipt: vi.fn(),
}))

vi.mock('./orderApi', () => ({
  getOrder: vi.fn(),
}))

const listMock = vi.mocked(listGoodsReceipts)
const recordMock = vi.mocked(recordGoodsReceipt)
const receiptMock = vi.mocked(getGoodsReceipt)
const orderMock = vi.mocked(getOrder)

const orderId = 'order-1'
const pendingKey = `govilink:goods-receipt:${orderId}`

// This component reads the order ID; refreshed orders are passed to its parent.
const order = {
  id: orderId,
  status: 'COLLECTION_IN_PROGRESS',
  version: 1,
} as ManagedOrder

const updatedOrder = {
  ...order,
  version: 2,
}

const page: GoodsReceiptPage = {
  content: [],
  page: 0,
  size: 20,
  totalElements: 0,
  totalPages: 0,
  hasNext: false,
  hasPrevious: false,
  orderStatus: 'COLLECTION_IN_PROGRESS',
  orderVersion: 1,
  reservations: [
    {
      id: 'reservation-1',
      farmerName: 'GoviLink Test Farmer',
      cropName: 'Tomato',
      quantityKg: 100,
      collectedQuantityKg: 0,
      remainingQuantityKg: 100,
      status: 'ACTIVE',
    },
  ],
}

const receipt: GoodsReceipt = {
  id: 'receipt-1',
  orderId,
  reservationId: 'reservation-1',
  quantityKg: 40,
  receivedAt: '2020-01-02T04:30:00.000Z',
  recordedAt: '2020-01-02T04:31:00.000Z',
  receivedBy: 'staff-1',
  reference: 'Farmer slip 42',
  orderStatus: 'COLLECTION_IN_PROGRESS',
  orderVersion: 2,
}

const pendingRequest: CreateGoodsReceipt = {
  requestId: '11111111-1111-4111-8111-111111111111',
  version: 1,
  reservationId: 'reservation-1',
  quantityKg: 40,
  receivedAt: '2020-01-02T04:30:00.000Z',
  reference: 'Farmer slip 42',
}

beforeEach(() => {
  vi.resetAllMocks()
  sessionStorage.clear()

  listMock.mockResolvedValue(page)
  recordMock.mockResolvedValue(receipt)
  receiptMock.mockResolvedValue(receipt)
  orderMock.mockResolvedValue(updatedOrder)
})

afterEach(() => {
  cleanup()
  sessionStorage.clear()
})

function openScreen() {
  const onChanged = vi.fn()
  const view = render(
    <GoodsReceipts order={order} onChanged={onChanged} />,
  )

  return {
    ...view,
    onChanged,
    user: userEvent.setup(),
  }
}

async function fillForm(
  user: ReturnType<typeof userEvent.setup>,
  quantity = '40',
) {
  const reservation = await screen.findByLabelText('Harvest reservation')

  await user.selectOptions(reservation, 'reservation-1')

  fireEvent.change(screen.getByLabelText('Received quantity — kg'), {
    target: { value: quantity },
  })

  fireEvent.change(
    screen.getByLabelText('Received date and time — Sri Lanka time'),
    {
      target: { value: '2020-01-02T10:00:00' },
    },
  )

  fireEvent.change(screen.getByLabelText('Goods receipt reference'), {
    target: { value: '  Farmer slip 42  ' },
  })
}

async function confirmAndSubmit(
  user: ReturnType<typeof userEvent.setup>,
) {
  await user.click(
    screen.getByRole('checkbox', {
      name: 'I confirm these goods and this quantity were received.',
    }),
  )

  await user.click(
    screen.getByRole('button', { name: 'Record goods receipt' }),
  )
}

describe('Goods receipts', () => {
  it('requires explicit confirmation before recording received goods', async () => {
    const { user } = openScreen()

    await fillForm(user)

    const submit = screen.getByRole('button', {
      name: 'Record goods receipt',
    })

    expect(submit).toBeDisabled()
    expect(recordMock).not.toHaveBeenCalled()

    await user.click(
      screen.getByRole('checkbox', {
        name: 'I confirm these goods and this quantity were received.',
      }),
    )

    expect(submit).toBeEnabled()

    fireEvent.change(screen.getByLabelText('Received quantity — kg'), {
      target: { value: '30' },
    })

    expect(submit).toBeDisabled()
  })

  it('records the reviewed version and converts Sri Lanka time to UTC', async () => {
    const { user, onChanged } = openScreen()

    await fillForm(user)
    await confirmAndSubmit(user)

    await waitFor(() => {
      expect(recordMock).toHaveBeenCalledWith(orderId, {
        requestId: expect.any(String),
        version: 1,
        reservationId: 'reservation-1',
        quantityKg: 40,
        receivedAt: '2020-01-02T04:30:00.000Z',
        reference: 'Farmer slip 42',
      })
    })

    expect(await screen.findByText('Receipt saved: receipt-1')).toBeVisible()

    await waitFor(() => {
      expect(onChanged).toHaveBeenCalledWith(updatedOrder)
    })

    expect(sessionStorage.getItem(pendingKey)).toBeNull()
    expect(recordMock).toHaveBeenCalledTimes(1)
  })

  it('rejects a quantity above the remaining allocation', async () => {
    const { user } = openScreen()

    await fillForm(user, '100.001')
    await confirmAndSubmit(user)

    expect(
      await screen.findByText(
        'Received quantity cannot exceed the remaining allocation.',
      ),
    ).toBeVisible()

    expect(recordMock).not.toHaveBeenCalled()
  })

  it('preserves an uncertain request after remount and retries the same payload', async () => {
    recordMock.mockRejectedValueOnce(new Error('Network connection lost.'))

    const first = openScreen()

    await fillForm(first.user)
    await confirmAndSubmit(first.user)

    expect(
      await screen.findByRole('heading', {
        name: 'Receipt awaiting confirmation',
      }),
    ).toBeVisible()

    await waitFor(() => {
      expect(
        screen.getByRole('button', { name: 'Retry same receipt' }),
      ).toBeEnabled()
    })

    const originalPayload = recordMock.mock.calls[0][1]
    const stored = sessionStorage.getItem(pendingKey)

    expect(stored).not.toBeNull()
    expect(JSON.parse(stored!)).toEqual(originalPayload)

    first.unmount()

    const second = openScreen()

    await second.user.click(
      await screen.findByRole('button', { name: 'Retry same receipt' }),
    )

    await waitFor(() => {
      expect(recordMock).toHaveBeenCalledTimes(2)
    })

    expect(recordMock.mock.calls[1]).toEqual([
      orderId,
      originalPayload,
    ])

    expect(await screen.findByText('Receipt saved: receipt-1')).toBeVisible()
    expect(sessionStorage.getItem(pendingKey)).toBeNull()
  })

  it('recovers a saved receipt without submitting another POST', async () => {
    sessionStorage.setItem(pendingKey, JSON.stringify(pendingRequest))

    const { user } = openScreen()

    await user.click(
      await screen.findByRole('button', { name: 'Check receipt result' }),
    )

    await waitFor(() => {
      expect(receiptMock).toHaveBeenCalledWith(
        orderId,
        pendingRequest.requestId,
      )
    })

    expect(await screen.findByText('Receipt saved: receipt-1')).toBeVisible()
    expect(recordMock).not.toHaveBeenCalled()
    expect(sessionStorage.getItem(pendingKey)).toBeNull()
  })

  it('keeps the same pending request when a result check returns 404', async () => {
    sessionStorage.setItem(pendingKey, JSON.stringify(pendingRequest))
    receiptMock.mockRejectedValueOnce(
      new ApiError('Receipt not found.', 404),
    )

    const { user } = openScreen()

    await user.click(
      await screen.findByRole('button', { name: 'Check receipt result' }),
    )

    expect(
      await screen.findByText(
        'No saved receipt was found yet. Retry the same receipt request to resolve it safely.',
      ),
    ).toBeVisible()

    expect(
      JSON.parse(sessionStorage.getItem(pendingKey)!),
    ).toEqual(pendingRequest)

    expect(
      screen.queryByRole('button', { name: 'Record goods receipt' }),
    ).not.toBeInTheDocument()

    await waitFor(() => {
      expect(
        screen.getByRole('button', { name: 'Retry same receipt' }),
      ).toBeEnabled()
    })

    await user.click(
      screen.getByRole('button', { name: 'Retry same receipt' }),
    )

    await waitFor(() => {
      expect(recordMock).toHaveBeenCalledWith(orderId, pendingRequest)
    })
  })

  it('reloads after a conflict and requires fresh confirmation', async () => {
    recordMock.mockRejectedValueOnce(
      new ApiError('Order version changed.', 409),
    )

    const { user } = openScreen()

    await fillForm(user)
    await confirmAndSubmit(user)

    expect(
      await screen.findByText(
        'Order version changed. Review the refreshed balances before submitting again.',
      ),
    ).toBeVisible()

    await waitFor(() => {
      expect(listMock).toHaveBeenCalledTimes(2)
    })

    expect(
      await screen.findByRole('button', {
        name: 'Record goods receipt',
      }),
    ).toBeDisabled()

    expect(sessionStorage.getItem(pendingKey)).toBeNull()
    expect(recordMock).toHaveBeenCalledTimes(1)
  })

  it('shows completed balances and history without another receipt form', async () => {
    listMock.mockResolvedValue({
      ...page,
      content: [receipt],
      totalElements: 1,
      totalPages: 1,
      orderStatus: 'COLLECTED',
      orderVersion: 3,
      reservations: [
        {
          ...page.reservations[0],
          collectedQuantityKg: 100,
          remainingQuantityKg: 0,
          status: 'CONSUMED',
        },
      ],
    })

    openScreen()

    expect(
      await screen.findByText(
        'Collection complete. All reserved quantities have been received.',
      ),
    ).toBeVisible()

    expect(screen.getByText('consumed')).toBeVisible()

    expect(
      screen.getByRole('table', { name: 'Goods receipt history' }),
    ).toHaveTextContent('receipt-1')

    expect(
      screen.queryByRole('button', { name: 'Record goods receipt' }),
    ).not.toBeInTheDocument()

    expect(recordMock).not.toHaveBeenCalled()
  })
})