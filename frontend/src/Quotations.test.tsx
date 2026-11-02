import {
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import Quotations from './Quotations'
import { listBuyerRequests } from './buyerApi'
import type { BuyerRequest, BuyerRequestPage } from './buyerApi'
import { searchHarvests } from './harvestApi'
import type { HarvestListing, HarvestPage } from './harvestApi'
import {
  createQuotationDraft,
  getQuotation,
} from './quotationApi'
import type { Quotation } from './quotationApi'
import { ApiError } from './http'

vi.mock('./buyerApi', () => ({
  listBuyerRequests: vi.fn(),
}))

vi.mock('./harvestApi', () => ({
  searchHarvests: vi.fn(),
}))

vi.mock('./quotationApi', () => ({
  createQuotationDraft: vi.fn(),
  getQuotation: vi.fn(),
}))

const buyersMock = vi.mocked(listBuyerRequests)
const sourcesMock = vi.mocked(searchHarvests)
const createMock = vi.mocked(createQuotationDraft)
const getMock = vi.mocked(getQuotation)

const buyer: BuyerRequest = {
  id: 'buyer-1',
  buyerName: 'Demo Buyer',
  phone: 'TEST-B',
  cropName: 'Tomato',
  quantityKg: 500,
  district: 'Matale',
  requiredBy: '2099-01-01',
  maxPricePerKg: 220,
  status: 'OPEN',
  version: 0,
  createdAt: '',
  updatedAt: '',
}

const harvest: HarvestListing = {
  id: 'harvest-1',
  farmerId: 'farmer-1',
  farmerName: 'Demo Farmer',
  district: 'Matale',
  cropName: 'Tomato',
  quantityKg: 300,
  expectedPricePerKg: 180,
  availableFrom: '2098-01-01',
  status: 'AVAILABLE',
  version: 0,
  createdAt: '',
  updatedAt: '',
}

const second = {
  ...harvest,
  id: 'harvest-2',
  farmerId: 'farmer-2',
  farmerName: 'Second Farmer',
  quantityKg: 200,
}

const quote: Quotation = {
  id: 'quote-1',
  buyerRequestId: buyer.id,
  createdBy: 'staff-1',
  buyerName: buyer.buyerName,
  buyerPhone: buyer.phone,
  district: buyer.district,
  requiredBy: buyer.requiredBy,
  deliveryAddress: null,
  status: 'DRAFT',
  currency: 'LKR',
  quantityKg: 500,
  produceSubtotal: 90000,
  serviceFee: 4500,
  deliveryFee: 2500,
  total: 97000,
  priceConfirmationNeeded: true,
  version: 0,
  createdAt: '',
  updatedAt: '',
  lines: [
    {
      id: 'line-1',
      lineNumber: 1,
      harvestListingId: harvest.id,
      farmerId: harvest.farmerId,
      farmerName: harvest.farmerName,
      cropName: 'Tomato',
      quantityKg: 300,
      farmerPricePerKg: 180,
      lineSubtotal: 54000,
      priceConfirmed: true,
    },
    {
      id: 'line-2',
      lineNumber: 2,
      harvestListingId: second.id,
      farmerId: second.farmerId,
      farmerName: second.farmerName,
      cropName: 'Tomato',
      quantityKg: 200,
      farmerPricePerKg: 180,
      lineSubtotal: 36000,
      priceConfirmed: false,
    },
  ],
}

function buyers(
  content = [buyer],
  page = 0,
  hasNext = false,
): BuyerRequestPage {
  return {
    content,
    page,
    size: 10,
    totalElements: content.length,
    totalPages: hasNext ? 2 : 1,
    hasNext,
    hasPrevious: page > 0,
  }
}

function sources(
  content = [harvest, second],
  page = 0,
  hasNext = false,
): HarvestPage {
  return {
    content,
    page,
    size: 10,
    totalElements: content.length,
    totalPages: hasNext ? 2 : 1,
    hasNext,
    hasPrevious: page > 0,
  }
}

async function start() {
  const user = userEvent.setup()

  render(<Quotations />)

  await user.click(
    await screen.findByRole('button', {
      name: 'Prepare quote for Demo Buyer',
    }),
  )

  await screen.findByRole('button', {
    name: 'Add source Demo Farmer',
  })

  return user
}

async function fees(user: ReturnType<typeof userEvent.setup>) {
  await user.type(
    screen.getByLabelText('Service fee — LKR'),
    '4500',
  )
  await user.type(
    screen.getByLabelText('Delivery fee — LKR'),
    '2500',
  )
}

beforeEach(() => {
  vi.resetAllMocks()
  buyersMock.mockResolvedValue(buyers())
  sourcesMock.mockResolvedValue(sources())
  createMock.mockResolvedValue(quote)
  getMock.mockResolvedValue(quote)
})

describe('Quotations', () => {
  it('loads crop and district sources including partial quantities', async () => {
    await start()

    expect(sourcesMock).toHaveBeenCalledWith(
      0,
      {
        query: 'Tomato',
        district: 'Matale',
        status: 'AVAILABLE',
      },
      expect.any(AbortSignal),
    )

    expect(
      screen.getByRole('button', {
        name: 'Add source Demo Farmer',
      }),
    ).toBeEnabled()
  })

  it('creates a multi-source draft and displays server totals', async () => {
    const user = await start()

    await user.click(
      screen.getByRole('button', { name: 'Add source Demo Farmer' }),
    )
    await user.click(
      screen.getByRole('button', { name: 'Add source Second Farmer' }),
    )

    expect(screen.getByLabelText('Quantity kg — source 1'))
      .toHaveValue(300)
    expect(screen.getByLabelText('Quantity kg — source 2'))
      .toHaveValue(200)

    await user.click(
      screen.getByLabelText('Price agreed with farmer — source 1'),
    )
    await fees(user)
    await user.click(
      screen.getByRole('button', { name: 'Save draft quotation' }),
    )

    await waitFor(() => expect(createMock).toHaveBeenCalledOnce())

    expect(createMock).toHaveBeenCalledWith({
      buyerRequestId: buyer.id,
      serviceFee: 4500,
      deliveryFee: 2500,
      deliveryAddress: null,
      lines: [
        {
          harvestListingId: harvest.id,
          quantityKg: 300,
          farmerPricePerKg: 180,
          priceConfirmed: true,
        },
        {
          harvestListingId: second.id,
          quantityKg: 200,
          farmerPricePerKg: 180,
          priceConfirmed: false,
        },
      ],
    })

    expect(await screen.findByText('Quotation DRAFT'))
      .toBeInTheDocument()
    expect(screen.getByText(/97,000\.00/)).toBeInTheDocument()
    expect(screen.getByText('quote-1')).toBeInTheDocument()
  })

  it('requires explicit fees and prevents duplicate source selection', async () => {
    const user = await start()

    await user.click(
      screen.getByRole('button', { name: 'Add source Demo Farmer' }),
    )

    expect(
      screen.getByRole('button', { name: 'Add source Demo Farmer' }),
    ).toBeDisabled()

    await user.click(
      screen.getByRole('button', { name: 'Save draft quotation' }),
    )

    expect(screen.getByRole('alert'))
      .toHaveTextContent('Enter valid service and delivery fees')
    expect(createMock).not.toHaveBeenCalled()
  })

  it('rejects combined quantities above buyer demand', async () => {
    sourcesMock.mockResolvedValue(
      sources([harvest, { ...second, quantityKg: 300 }]),
    )

    const user = await start()

    await user.click(
      screen.getByRole('button', { name: 'Add source Demo Farmer' }),
    )
    await user.click(
      screen.getByRole('button', { name: 'Add source Second Farmer' }),
    )

    fireEvent.change(
      screen.getByLabelText('Quantity kg — source 2'),
      { target: { value: '300' } },
    )

    await fees(user)
    await user.click(
      screen.getByRole('button', { name: 'Save draft quotation' }),
    )

    expect(screen.getByRole('alert'))
      .toHaveTextContent('exceed the buyer request')
    expect(createMock).not.toHaveBeenCalled()
  })

  it('retains entered values after a rejected save', async () => {
    createMock.mockRejectedValue(
      new ApiError('The buyer request is closed.', 409),
    )

    const user = await start()

    await user.click(
      screen.getByRole('button', { name: 'Add source Demo Farmer' }),
    )
    await fees(user)
    await user.click(
      screen.getByRole('button', { name: 'Save draft quotation' }),
    )

    expect(await screen.findByRole('alert'))
      .toHaveTextContent('The buyer request is closed.')
    expect(screen.getByLabelText('Service fee — LKR'))
      .toHaveValue(4500)
    expect(screen.getByLabelText('Quantity kg — source 1'))
      .toHaveValue(300)
  })

  it('keeps selected sources while paging harvest results', async () => {
    sourcesMock
      .mockResolvedValueOnce(sources([harvest], 0, true))
      .mockResolvedValueOnce(sources([second], 1))

    const user = await start()

    await user.click(
      screen.getByRole('button', { name: 'Add source Demo Farmer' }),
    )
    await user.click(
      screen.getByRole('button', { name: 'Next sources' }),
    )

    await screen.findByRole('button', {
      name: 'Add source Second Farmer',
    })

    expect(screen.getByLabelText('Quantity kg — source 1'))
      .toHaveValue(300)

    expect(sourcesMock).toHaveBeenLastCalledWith(
      1,
      expect.objectContaining({ district: 'Matale' }),
      expect.any(AbortSignal),
    )
  })

  it('requires an agreed price for an unpriced listing', async () => {
    sourcesMock.mockResolvedValue(
      sources([{ ...harvest, expectedPricePerKg: null }]),
    )

    const user = await start()

    await user.click(
      screen.getByRole('button', { name: 'Add source Demo Farmer' }),
    )
    await fees(user)
    await user.click(
      screen.getByRole('button', { name: 'Save draft quotation' }),
    )

    expect(screen.getByRole('alert'))
      .toHaveTextContent('Check each source')
    expect(createMock).not.toHaveBeenCalled()
  })

  it('opens a saved quotation by ID', async () => {
    const user = userEvent.setup()

    render(<Quotations />)

    await user.type(
      screen.getByLabelText('Quotation ID'),
      'quote-1',
    )
    await user.click(
      screen.getByRole('button', { name: 'Open saved quotation' }),
    )

    expect(await screen.findByText('Quotation DRAFT'))
      .toBeInTheDocument()

    expect(getMock).toHaveBeenCalledWith(
      'quote-1',
      expect.any(AbortSignal),
    )
  })

  it('does not offer closed buyer requests', async () => {
    buyersMock.mockResolvedValue(
      buyers([{ ...buyer, status: 'CANCELLED' }]),
    )

    render(<Quotations />)

    expect(
      await screen.findByRole('button', {
        name: 'Prepare quote for Demo Buyer',
      }),
    ).toBeDisabled()
  })

  it('handles fractional quantities without floating point allocation errors', async () => {
    buyersMock.mockResolvedValue(
      buyers([{ ...buyer, quantityKg: 0.3 }]),
    )
    sourcesMock.mockResolvedValue(
      sources([
        { ...harvest, quantityKg: 0.1 },
        { ...second, quantityKg: 0.2 },
      ]),
    )

    const user = await start()

    await user.click(
      screen.getByRole('button', { name: 'Add source Demo Farmer' }),
    )
    await user.click(
      screen.getByRole('button', { name: 'Add source Second Farmer' }),
    )

    expect(screen.getByLabelText('Quantity kg — source 2'))
      .toHaveValue(0.2)

    await fees(user)
    await user.click(
      screen.getByRole('button', { name: 'Save draft quotation' }),
    )

    await waitFor(() => expect(createMock).toHaveBeenCalledOnce())

    expect(
      createMock.mock.calls[0][0].lines.map(
        (line) => line.quantityKg,
      ),
    ).toEqual([0.1, 0.2])
  })

  it('filters mismatched crops and sources available after the required date', async () => {
    sourcesMock.mockResolvedValue(sources([
      harvest,
      {
        ...second,
        id: 'wrong-crop',
        farmerName: 'Wrong Crop',
        cropName: 'Tomato Sauce',
      },
      {
        ...second,
        id: 'late',
        farmerName: 'Late Farmer',
        availableFrom: '2099-01-02',
      },
    ]))

    await start()

    expect(
      screen.queryByRole('button', {
        name: 'Add source Wrong Crop',
      }),
    ).not.toBeInTheDocument()

    expect(
      screen.queryByRole('button', {
        name: 'Add source Late Farmer',
      }),
    ).not.toBeInTheDocument()
  })

  it('prevents another create while a save is pending', async () => {
    createMock.mockImplementation(
      () => new Promise(() => {}),
    )

    const user = await start()

    await user.click(
      screen.getByRole('button', { name: 'Add source Demo Farmer' }),
    )
    await fees(user)
    await user.click(
      screen.getByRole('button', { name: 'Save draft quotation' }),
    )

    const button = await screen.findByRole('button', {
      name: 'Saving draft…',
    })

    await user.click(button)

    expect(button).toBeDisabled()
    expect(createMock).toHaveBeenCalledOnce()
  })

  it('retries a failed harvest load', async () => {
    sourcesMock
      .mockRejectedValueOnce(new Error('Sources unavailable'))
      .mockResolvedValueOnce(sources())

    const user = userEvent.setup()

    render(<Quotations />)

    await user.click(
      await screen.findByRole('button', {
        name: 'Prepare quote for Demo Buyer',
      }),
    )

    expect(await screen.findByRole('alert'))
      .toHaveTextContent('Sources unavailable')

    await user.click(
      screen.getByRole('button', { name: 'Retry sources' }),
    )

    expect(
      await screen.findByRole('button', {
        name: 'Add source Demo Farmer',
      }),
    ).toBeEnabled()
  })

  it('clears price confirmation after changing the price', async () => {
    const user = await start()

    await user.click(
      screen.getByRole('button', { name: 'Add source Demo Farmer' }),
    )

    const confirmed = screen.getByLabelText(
      'Price agreed with farmer — source 1',
    )

    await user.click(confirmed)

    fireEvent.change(
      screen.getByLabelText('Farmer price LKR/kg — source 1'),
      { target: { value: '190' } },
    )

    expect(confirmed).not.toBeChecked()
  })
})