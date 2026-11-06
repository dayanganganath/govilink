import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import BuyerApproval from './BuyerApproval'
import { approveOrder } from './orderApi'
import type { ManagedOrder } from './orderApi'
import { getQuotation } from './quotationApi'
import type { Quotation } from './quotationApi'

vi.mock('./orderApi', () => ({
  approveOrder: vi.fn(),
}))

vi.mock('./quotationApi', () => ({
  getQuotation: vi.fn(),
}))

const approveMock = vi.mocked(approveOrder)
const getMock = vi.mocked(getQuotation)

const quotation: Quotation = {
  id: 'quote-1',
  buyerRequestId: 'buyer-1',
  createdBy: 'staff-1',
  buyerName: 'Test Buyer',
  buyerPhone: 'TEST-BUYER',
  district: 'Matale',
  requiredBy: '2099-01-10',
  deliveryAddress: 'Test address',
  status: 'ISSUED',
  currency: 'LKR',
  quantityKg: 100,
  produceSubtotal: 20000,
  serviceFee: 1000,
  deliveryFee: 2000,
  total: 23000,
  priceConfirmationNeeded: false,
  version: 3,
  createdAt: '2020-01-01T00:00:00Z',
  updatedAt: '2020-01-01T00:00:00Z',
  issuedBy: 'staff-1',
  issuedAt: '2020-01-01T00:00:00Z',
  expiresAt: '2099-01-09T00:00:00Z',
  lines: [],
}

const order: ManagedOrder = {
  id: 'order-1',
  quotationId: quotation.id,
  buyerRequestId: quotation.buyerRequestId,
  status: 'CONFIRMED',
  buyerContactName: 'Test Buyer',
  approvalChannel: 'WHATSAPP',
  approvalReference: 'Message confirmed',
  approvedAt: '2020-01-02T04:30:00.000Z',
  recordedAt: '2020-01-02T05:00:00Z',
  recordedBy: 'staff-1',
  currency: 'LKR',
  quantityKg: 100,
  produceSubtotal: 20000,
  serviceFee: 1000,
  deliveryFee: 2000,
  total: 23000,
  version: 0,
  reservations: [],
}

async function fillForm(
  user: ReturnType<typeof userEvent.setup>,
  time = '2020-01-02T10:00',
) {
  await user.type(
    screen.getByLabelText('Buyer contact name'),
    '  Test Buyer  ',
  )

  await user.selectOptions(
    screen.getByLabelText('Approval channel'),
    'WHATSAPP',
  )

  await user.type(
    screen.getByLabelText('Approval reference or notes'),
    '  Message confirmed  ',
  )

  fireEvent.change(
    screen.getByLabelText(
      'Buyer approval date and time — Sri Lanka time',
    ),
    { target: { value: time } },
  )

  await user.click(screen.getByRole('checkbox'))
}

beforeEach(() => {
  vi.resetAllMocks()
})

describe('Buyer approval', () => {
  it('requires explicit confirmation before submitting', () => {
    render(
      <BuyerApproval quotation={quotation} onChanged={vi.fn()} />,
    )

    expect(screen.getByRole('button', {
      name: 'Confirm order and reserve stock',
    })).toBeDisabled()

    expect(approveMock).not.toHaveBeenCalled()
  })

  it('submits the reviewed version and Sri Lanka approval time', async () => {
    const user = userEvent.setup()
    const onChanged = vi.fn()
    const accepted: Quotation = {
      ...quotation,
      status: 'ACCEPTED',
      version: 4,
    }

    approveMock.mockResolvedValueOnce(order)
    getMock.mockResolvedValueOnce(accepted)

    render(
      <BuyerApproval quotation={quotation} onChanged={onChanged} />,
    )

    await fillForm(user)

    await user.click(screen.getByRole('button', {
      name: 'Confirm order and reserve stock',
    }))

    expect(approveMock).toHaveBeenCalledExactlyOnceWith(
      'quote-1',
      {
        quotationVersion: 3,
        buyerContactName: 'Test Buyer',
        channel: 'WHATSAPP',
        reference: 'Message confirmed',
        approvedAt: '2020-01-02T04:30:00.000Z',
      },
    )

    expect(await screen.findByText('Order confirmed'))
      .toBeInTheDocument()

    expect(screen.getByText('order-1')).toBeInTheDocument()

    await waitFor(() => {
      expect(onChanged).toHaveBeenCalledWith(accepted)
    })
  })

  it('rejects an approval time before issuance', async () => {
    const user = userEvent.setup()

    render(
      <BuyerApproval quotation={quotation} onChanged={vi.fn()} />,
    )

    await fillForm(user, '2019-01-01T10:00')

    await user.click(screen.getByRole('button', {
      name: 'Confirm order and reserve stock',
    }))

    expect(screen.getByRole('alert')).toHaveTextContent(
      'Approval time cannot be before the quotation was issued.',
    )

    expect(approveMock).not.toHaveBeenCalled()
  })

  it('blocks another submission until a failed approval is reloaded', async () => {
    const user = userEvent.setup()

    approveMock.mockRejectedValueOnce(
      new Error('Available stock has changed.'),
    )

    getMock.mockResolvedValueOnce(quotation)

    render(
      <BuyerApproval quotation={quotation} onChanged={vi.fn()} />,
    )

    await fillForm(user)

    await user.click(screen.getByRole('button', {
      name: 'Confirm order and reserve stock',
    }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Available stock has changed.',
    )

    expect(screen.getByRole('button', {
      name: 'Confirm order and reserve stock',
    })).toBeDisabled()

    await user.click(screen.getByRole('button', {
      name: 'Reload approval quotation',
    }))

    await waitFor(() => {
      expect(screen.getByRole('checkbox')).not.toBeChecked()
    })

    expect(screen.getByLabelText('Buyer contact name'))
      .toHaveValue('  Test Buyer  ')

    expect(approveMock).toHaveBeenCalledTimes(1)
  })

  it('keeps the confirmed order visible if refreshing the quote fails', async () => {
    const user = userEvent.setup()

    approveMock.mockResolvedValueOnce(order)
    getMock.mockRejectedValueOnce(new Error('Connection lost'))

    render(
      <BuyerApproval quotation={quotation} onChanged={vi.fn()} />,
    )

    await fillForm(user)

    await user.click(screen.getByRole('button', {
      name: 'Confirm order and reserve stock',
    }))

    expect(await screen.findByText('order-1')).toBeInTheDocument()

    expect(await screen.findByText(
      /Order confirmed. Reload the quotation/,
    )).toBeInTheDocument()

    expect(approveMock).toHaveBeenCalledTimes(1)
  })

  it('does not show another approval form for an accepted quotation', () => {
    render(
      <BuyerApproval
        quotation={{ ...quotation, status: 'ACCEPTED' }}
        onChanged={vi.fn()}
      />,
    )

    expect(screen.getByText('Buyer approval recorded'))
      .toBeInTheDocument()

    expect(screen.queryByRole('button', {
      name: 'Confirm order and reserve stock',
    })).not.toBeInTheDocument()
  })
})