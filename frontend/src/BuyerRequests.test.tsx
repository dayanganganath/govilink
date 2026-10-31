import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ApiError } from './api'
import BuyerRequests from './BuyerRequests'
import { createBuyerRequest, listBuyerRequests } from './buyerApi'
import type { BuyerRequest, BuyerRequestPage } from './buyerApi'

vi.mock('./buyerApi', () => ({
  createBuyerRequest: vi.fn(),
  listBuyerRequests: vi.fn(),
}))

const createMock = vi.mocked(createBuyerRequest)
const listMock = vi.mocked(listBuyerRequests)

function futureDate(): string {
  const date = new Date()
  date.setDate(date.getDate() + 7)

  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')

  return `${date.getFullYear()}-${month}-${day}`
}

const request: BuyerRequest = {
  id: '56e21060-f3f5-4c77-aa7e-fdbd82ba791d',
  buyerName: 'Demo Buyer',
  phone: 'TEST-BUYER',
  cropName: 'Tomato',
  quantityKg: 500,
  district: 'Matale',
  requiredBy: futureDate(),
  maxPricePerKg: 220,
  status: 'OPEN',
  version: 0,
  createdAt: '2026-10-31T04:00:00Z',
  updatedAt: '2026-10-31T04:00:00Z',
}

function page(content: BuyerRequest[] = [request]): BuyerRequestPage {
  return {
    content,
    page: 0,
    size: 10,
    totalElements: content.length,
    totalPages: content.length ? 1 : 0,
    hasNext: false,
    hasPrevious: false,
  }
}

async function fillForm(
  user: ReturnType<typeof userEvent.setup>,
  price: string,
) {
  await user.type(
    screen.getByRole('textbox', { name: /^buyer name/i }),
    '  Demo Buyer  ',
  )

  await user.type(
    screen.getByRole('textbox', { name: /^phone/i }),
    '  TEST-BUYER  ',
  )

  await user.type(
    screen.getByRole('textbox', { name: /^crop name/i }),
    '  Tomato  ',
  )

  await user.type(
    screen.getByRole('spinbutton', { name: /required quantity/i }),
    '500',
  )

  await user.type(
    screen.getByRole('textbox', { name: /^district/i }),
    '  Matale  ',
  )

  fireEvent.change(screen.getByLabelText(/required by/i), {
    target: { value: request.requiredBy },
  })

  if (price) {
    await user.type(
      screen.getByRole('spinbutton', { name: /maximum price/i }),
      price,
    )
  }
}

beforeEach(() => {
  createMock.mockReset()
  listMock.mockReset()
  listMock.mockResolvedValue(page())
})

describe('BuyerRequests', () => {
  it('loads and displays existing buyer requests', async () => {
    render(<BuyerRequests />)

    expect(await screen.findByText('Tomato')).toBeInTheDocument()
    expect(screen.getByText('Demo Buyer')).toBeInTheDocument()
    expect(screen.getByText('TEST-BUYER')).toBeInTheDocument()
    expect(screen.getByText('500 kg')).toBeInTheDocument()
    expect(screen.getByText('220.00')).toBeInTheDocument()
    expect(screen.getByText('OPEN')).toBeInTheDocument()

    expect(listMock).toHaveBeenCalledWith(
      0,
      expect.any(AbortSignal),
    )

    expect(createMock).not.toHaveBeenCalled()
  })

  it.each([
    { label: 'a maximum price', input: '220', expected: 220 },
    { label: 'no maximum price', input: '', expected: null },
  ])('creates a request with $label', async ({ input, expected }) => {
    createMock.mockResolvedValue({
      ...request,
      maxPricePerKg: expected,
    })

    const user = userEvent.setup()
    render(<BuyerRequests />)

    await screen.findByText('Tomato')
    await fillForm(user, input)

    await user.click(
      screen.getByRole('button', { name: 'Add request' }),
    )

    await waitFor(() => {
      expect(createMock).toHaveBeenCalledExactlyOnceWith({
        buyerName: 'Demo Buyer',
        phone: 'TEST-BUYER',
        cropName: 'Tomato',
        quantityKg: 500,
        district: 'Matale',
        requiredBy: request.requiredBy,
        maxPricePerKg: expected,
      })
    })

    expect(
      await screen.findByText(
        'Tomato request for Demo Buyer was added.',
      ),
    ).toBeInTheDocument()

    expect(screen.getByRole('textbox', { name: /^buyer name/i }))
      .toHaveValue('')

    expect(screen.getByRole('textbox', { name: /^crop name/i }))
      .toHaveValue('')

    expect(screen.getByRole('spinbutton', { name: /required quantity/i }))
      .toHaveValue(null)

    await waitFor(() => {
      expect(listMock).toHaveBeenCalledTimes(2)
    })

    expect(listMock).toHaveBeenLastCalledWith(
      0,
      expect.any(AbortSignal),
    )
  })

  it('preserves entered details and displays server field errors', async () => {
    createMock.mockRejectedValue(
      new ApiError('Request validation failed.', 400, {
        district: 'District is invalid.',
      }),
    )

    const user = userEvent.setup()
    render(<BuyerRequests />)

    await screen.findByText('Tomato')
    await fillForm(user, '220')

    await user.click(
      screen.getByRole('button', { name: 'Add request' }),
    )

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Request validation failed.',
    )

    expect(screen.getByText('District is invalid.'))
      .toBeInTheDocument()

    expect(screen.getByRole('textbox', { name: /^district/i }))
      .toHaveAttribute('aria-invalid', 'true')

    expect(screen.getByRole('textbox', { name: /^buyer name/i }))
      .toHaveValue('  Demo Buyer  ')

    expect(screen.getByRole('textbox', { name: /^crop name/i }))
      .toHaveValue('  Tomato  ')

    expect(screen.getByRole('spinbutton', { name: /required quantity/i }))
      .toHaveValue(500)

    expect(screen.getByRole('spinbutton', { name: /maximum price/i }))
      .toHaveValue(220)

    expect(screen.getByLabelText(/required by/i))
      .toHaveValue(request.requiredBy)

    expect(screen.getByRole('button', { name: 'Add request' }))
      .toBeEnabled()

    expect(listMock).toHaveBeenCalledTimes(1)
  })

  it('retries a failed list request', async () => {
    listMock
      .mockRejectedValueOnce(new Error('Could not load buyer requests.'))
      .mockResolvedValueOnce(page())

    const user = userEvent.setup()
    render(<BuyerRequests />)

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Could not load buyer requests.',
    )

    await user.click(
      screen.getByRole('button', { name: 'Try again' }),
    )

    expect(await screen.findByText('Tomato')).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(listMock).toHaveBeenCalledTimes(2)
  })

  it('loads the next page and replaces the displayed requests', async () => {
    const nextRequest: BuyerRequest = {
      ...request,
      id: '4ea567d9-e87c-4c15-b7be-994c1784a168',
      cropName: 'Carrot',
    }

    listMock
      .mockResolvedValueOnce({
        ...page(),
        totalElements: 11,
        totalPages: 2,
        hasNext: true,
      })
      .mockResolvedValueOnce({
        ...page([nextRequest]),
        page: 1,
        totalElements: 11,
        totalPages: 2,
        hasPrevious: true,
      })

    const user = userEvent.setup()
    render(<BuyerRequests />)

    await screen.findByText('Tomato')

    await user.click(
      screen.getByRole('button', { name: 'Next' }),
    )

    expect(await screen.findByText('Carrot')).toBeInTheDocument()
    expect(screen.queryByText('Tomato')).not.toBeInTheDocument()

    expect(listMock).toHaveBeenLastCalledWith(
      1,
      expect.any(AbortSignal),
    )

    expect(screen.getByRole('button', { name: 'Previous' }))
      .toBeEnabled()

    expect(screen.getByRole('button', { name: 'Next' }))
      .toBeDisabled()
  })

  it('shows an empty state when no requests exist', async () => {
    listMock.mockResolvedValue(page([]))

    render(<BuyerRequests />)

    expect(
      await screen.findByText('No buyer requests yet'),
    ).toBeInTheDocument()

    expect(screen.getByText('0 of 0 requests')).toBeInTheDocument()

    expect(screen.getByRole('button', { name: 'Previous' }))
      .toBeDisabled()

    expect(screen.getByRole('button', { name: 'Next' }))
      .toBeDisabled()
  })
})