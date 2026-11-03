import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {
  beforeEach,
  describe,
  expect,
  it,
  vi,
} from 'vitest'

import QuotationIssue from './QuotationIssue'
import { getQuotation, issueQuotation } from './quotationApi'
import type { Quotation } from './quotationApi'

vi.mock('./quotationApi', () => ({
  getQuotation: vi.fn(),
  issueQuotation: vi.fn(),
}))

const issueMock = vi.mocked(issueQuotation)
const getMock = vi.mocked(getQuotation)

const draft: Quotation = {
  id: 'quote-1',
  buyerRequestId: 'buyer-1',
  createdBy: 'staff-1',
  buyerName: 'Test Buyer',
  buyerPhone: 'TEST-BUYER',
  district: 'Matale',
  requiredBy: '2099-01-10',
  deliveryAddress: null,
  status: 'DRAFT',
  currency: 'LKR',
  quantityKg: 500,
  produceSubtotal: 90000,
  serviceFee: 100,
  deliveryFee: 200,
  total: 90300,
  priceConfirmationNeeded: true,
  version: 2,
  createdAt: '2099-01-01T00:00:00Z',
  updatedAt: '2099-01-01T00:00:00Z',
  issuedBy: null,
  issuedAt: null,
  expiresAt: null,
  lines: [
    {
      id: 'line-1',
      lineNumber: 1,
      harvestListingId: 'harvest-1',
      farmerId: 'farmer-1',
      farmerName: 'First Farmer',
      cropName: 'Tomato',
      quantityKg: 200,
      farmerPricePerKg: 180,
      lineSubtotal: 36000,
      priceConfirmed: true,
    },
    {
      id: 'line-2',
      lineNumber: 2,
      harvestListingId: 'harvest-2',
      farmerId: 'farmer-2',
      farmerName: 'Second Farmer',
      cropName: 'Tomato',
      quantityKg: 300,
      farmerPricePerKg: 180,
      lineSubtotal: 54000,
      priceConfirmed: false,
    },
  ],
}

const issued: Quotation = {
  ...draft,
  status: 'ISSUED',
  version: 3,
  priceConfirmationNeeded: false,
  issuedBy: 'staff-2',
  issuedAt: '2099-01-01T10:00:00Z',
  expiresAt: '2099-01-02T04:30:00.000Z',
  lines: draft.lines.map((line) => ({
    ...line,
    priceConfirmed: true,
  })),
}

function expiryInput(): HTMLInputElement {
  return screen.getByLabelText(
    'Expiry date and time — Sri Lanka time',
  )
}

async function fillReview(
  user: ReturnType<typeof userEvent.setup>,
  expiry = '2099-01-02T10:00',
) {
  await user.click(screen.getByRole('checkbox', {
    name: /I confirm the price with First Farmer/,
  }))

  await user.click(screen.getByRole('checkbox', {
    name: /I confirm the price with Second Farmer/,
  }))

  await user.type(expiryInput(), expiry)
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('Quotation issue form', () => {
  it('requires a fresh confirmation for every farmer price', async () => {
    const user = userEvent.setup()

    render(
      <QuotationIssue
        quotation={draft}
        onChanged={vi.fn()}
      />,
    )

    const button = screen.getByRole('button', {
      name: 'Issue quotation',
    })

    for (const checkbox of screen.getAllByRole('checkbox')) {
      expect(checkbox).not.toBeChecked()
    }

    await user.type(expiryInput(), '2099-01-02T10:00')

    await user.click(screen.getByRole('checkbox', {
      name: /First Farmer/,
    }))

    expect(button).toBeDisabled()

    await user.click(screen.getByRole('checkbox', {
      name: /Second Farmer/,
    }))

    expect(button).toBeEnabled()
    expect(issueMock).not.toHaveBeenCalled()
  })

  it('issues the reviewed version using Sri Lanka time', async () => {
    const user = userEvent.setup()
    const onChanged = vi.fn()

    issueMock.mockResolvedValueOnce(issued)

    render(
      <QuotationIssue
        quotation={draft}
        onChanged={onChanged}
      />,
    )

    await fillReview(user)

    await user.click(screen.getByRole('button', {
      name: 'Issue quotation',
    }))

    expect(issueMock).toHaveBeenCalledExactlyOnceWith(
      'quote-1',
      {
        version: 2,
        expiresAt: '2099-01-02T04:30:00.000Z',
        confirmedLineIds: ['line-1', 'line-2'],
      },
    )

    await waitFor(() => {
      expect(onChanged).toHaveBeenCalledWith(issued)
    })
  })

  it('rejects an expiry after the required date', async () => {
    const user = userEvent.setup()

    render(
      <QuotationIssue
        quotation={draft}
        onChanged={vi.fn()}
      />,
    )

    await fillReview(user, '2099-01-11T10:00')

    await user.click(screen.getByRole('button', {
      name: 'Issue quotation',
    }))

    expect(screen.getByRole('alert')).toHaveTextContent(
      'Expiry cannot be after the buyer’s required date.',
    )

    expect(issueMock).not.toHaveBeenCalled()
  })

  it('rejects a past expiry', async () => {
    const user = userEvent.setup()

    render(
      <QuotationIssue
        quotation={draft}
        onChanged={vi.fn()}
      />,
    )

    await fillReview(user, '2000-01-01T10:00')

    await user.click(screen.getByRole('button', {
      name: 'Issue quotation',
    }))

    expect(screen.getByRole('alert')).toHaveTextContent(
      'Expiry must be in the future.',
    )

    expect(issueMock).not.toHaveBeenCalled()
  })

  it('requires a reload after a failed issue and clears the review', async () => {
    const user = userEvent.setup()
    const onChanged = vi.fn()

    issueMock.mockRejectedValueOnce(
      new Error('Quotation has changed. Reload and review it again.'),
    )

    const latest: Quotation = {
      ...draft,
      version: 3,
    }

    getMock.mockResolvedValueOnce(latest)

    render(
      <QuotationIssue
        quotation={draft}
        onChanged={onChanged}
      />,
    )

    await fillReview(user)

    await user.click(screen.getByRole('button', {
      name: 'Issue quotation',
    }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Quotation has changed.',
    )

    expect(screen.getByRole('button', {
      name: 'Issue quotation',
    })).toBeDisabled()

    expect(issueMock).toHaveBeenCalledTimes(1)

    await user.click(screen.getByRole('button', {
      name: 'Reload quotation',
    }))

    await waitFor(() => {
      expect(onChanged).toHaveBeenCalledWith(latest)
    })

    expect(getMock).toHaveBeenCalledExactlyOnceWith('quote-1')

    for (const checkbox of screen.getAllByRole('checkbox')) {
      expect(checkbox).not.toBeChecked()
    }

    expect(expiryInput()).toHaveValue('')
  })

  it('shows issuance details without an issue form for an issued quote', () => {
    render(
      <QuotationIssue
        quotation={issued}
        onChanged={vi.fn()}
      />,
    )

    expect(screen.queryByRole('button', {
      name: 'Issue quotation',
    })).not.toBeInTheDocument()

    expect(screen.getByText(/Issued:/)).toBeInTheDocument()
    expect(screen.getByText(/Expires:/)).toBeInTheDocument()

    expect(screen.getByText(
      /Issuing a quotation does not reserve stock/,
    )).toBeInTheDocument()
  })
})