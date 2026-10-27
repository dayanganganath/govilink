import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import HarvestListings from './HarvestListings'
import { ApiError } from './api'
import type { Farmer, FarmerPage } from './api'
import {
  createHarvest,
  listHarvests,
  searchActiveFarmers,
} from './harvestApi'
import type { HarvestListing, HarvestPage } from './harvestApi'

vi.mock('./harvestApi', () => ({
  createHarvest: vi.fn(),
  listHarvests: vi.fn(),
  searchActiveFarmers: vi.fn(),
}))

const createHarvestMock = vi.mocked(createHarvest)
const listHarvestsMock = vi.mocked(listHarvests)
const searchFarmersMock = vi.mocked(searchActiveFarmers)

const farmer: Farmer = {
  id: '87bc0103-f7fb-432f-a219-292e928164c7',
  fullName: 'Demo Farmer',
  phone: 'TEST-001',
  district: 'Matale',
  village: 'Demo Village',
  active: true,
  version: 0,
  createdAt: '2026-10-27T04:00:00Z',
  updatedAt: '2026-10-27T04:00:00Z',
}

const listing: HarvestListing = {
  id: '56e21060-f3f5-4c77-aa7e-fdbd82ba791d',
  farmerId: farmer.id,
  farmerName: farmer.fullName,
  district: farmer.district,
  cropName: 'Tomato',
  quantityKg: 125.5,
  expectedPricePerKg: 180,
  availableFrom: '2026-10-27',
  status: 'AVAILABLE',
  version: 0,
  createdAt: '2026-10-27T05:00:00Z',
  updatedAt: '2026-10-27T05:00:00Z',
}

function harvestPage(
  content: HarvestListing[] = [listing],
): HarvestPage {
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

function farmerPage(): FarmerPage {
  return {
    content: [farmer],
    page: 0,
    size: 10,
    totalElements: 1,
    totalPages: 1,
    hasNext: false,
    hasPrevious: false,
  }
}

async function fillForm(
  user: ReturnType<typeof userEvent.setup>,
  price: string,
) {
  const farmerSelect = await screen.findByRole('combobox', {
    name: /^farmer/i,
  })

  await user.selectOptions(farmerSelect, farmer.id)

  await user.type(
    screen.getByRole('textbox', { name: /crop name/i }),
    '  Tomato  ',
  )

  await user.type(
    screen.getByRole('spinbutton', { name: /quantity/i }),
    '125.5',
  )

  if (price) {
    await user.type(
      screen.getByRole('spinbutton', { name: /expected price/i }),
      price,
    )
  }

  fireEvent.change(screen.getByLabelText(/available from/i), {
    target: { value: '2026-10-27' },
  })
}

beforeEach(() => {
  createHarvestMock.mockReset()
  listHarvestsMock.mockReset()
  searchFarmersMock.mockReset()

  listHarvestsMock.mockResolvedValue(harvestPage())
  searchFarmersMock.mockResolvedValue(farmerPage())
})

describe('HarvestListings', () => {
  it('loads listings and requires selecting a farmer before saving', async () => {
    render(<HarvestListings />)

    expect(await screen.findByText('Tomato')).toBeInTheDocument()

    await screen.findByRole('combobox', { name: /^farmer/i })

    expect(screen.getByText('125.5 kg')).toBeInTheDocument()
    expect(screen.getByText('180.00')).toBeInTheDocument()

    expect(screen.getByRole('button', { name: 'Add listing' }))
      .toBeDisabled()

    expect(listHarvestsMock).toHaveBeenCalledWith(
      0,
      expect.any(AbortSignal),
    )

    expect(searchFarmersMock).toHaveBeenCalledWith(
      '',
      0,
      expect.any(AbortSignal),
    )

    expect(createHarvestMock).not.toHaveBeenCalled()
  })

  it.each([
    { label: 'a provided price', input: '180', expected: 180 },
    { label: 'an empty optional price', input: '', expected: null },
  ])('saves correctly with $label', async ({ input, expected }) => {
    const created: HarvestListing = {
      ...listing,
      expectedPricePerKg: expected,
    }

    createHarvestMock.mockResolvedValue(created)

    const user = userEvent.setup()
    render(<HarvestListings />)

    await fillForm(user, input)

    await user.click(
      screen.getByRole('button', { name: 'Add listing' }),
    )

    await waitFor(() => {
      expect(createHarvestMock).toHaveBeenCalledExactlyOnceWith({
        farmerId: farmer.id,
        cropName: 'Tomato',
        quantityKg: 125.5,
        expectedPricePerKg: expected,
        availableFrom: '2026-10-27',
      })
    })

    expect(
      await screen.findByText(
        'Tomato listing for Demo Farmer was added.',
      ),
    ).toBeInTheDocument()

    expect(screen.getByRole('textbox', { name: /crop name/i }))
      .toHaveValue('')

    expect(screen.getByRole('spinbutton', { name: /quantity/i }))
      .toHaveValue(null)

    expect(screen.getByRole('combobox', { name: /^farmer/i }))
      .toHaveValue(farmer.id)

    await waitFor(() => {
      expect(listHarvestsMock).toHaveBeenCalledTimes(2)
    })

    expect(listHarvestsMock).toHaveBeenLastCalledWith(
      0,
      expect.any(AbortSignal),
    )
  })

  it('preserves entered details when saving fails', async () => {
    createHarvestMock.mockRejectedValue(
      new ApiError('The selected farmer is no longer active.', 409),
    )

    const user = userEvent.setup()
    render(<HarvestListings />)

    await fillForm(user, '180')

    await user.click(
      screen.getByRole('button', { name: 'Add listing' }),
    )

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'The selected farmer is no longer active.',
    )

    expect(screen.getByRole('textbox', { name: /crop name/i }))
      .toHaveValue('  Tomato  ')

    expect(screen.getByRole('spinbutton', { name: /quantity/i }))
      .toHaveValue(125.5)

    expect(screen.getByRole('spinbutton', { name: /expected price/i }))
      .toHaveValue(180)

    expect(screen.getByLabelText(/available from/i))
      .toHaveValue('2026-10-27')

    expect(screen.getByRole('combobox', { name: /^farmer/i }))
      .toHaveValue(farmer.id)

    expect(screen.getByRole('button', { name: 'Add listing' }))
      .toBeEnabled()

    expect(createHarvestMock).toHaveBeenCalledTimes(1)
    expect(listHarvestsMock).toHaveBeenCalledTimes(1)
  })

  it('retries a failed listing request and displays the results', async () => {
    listHarvestsMock
      .mockRejectedValueOnce(new Error('Could not connect to the server.'))
      .mockResolvedValueOnce(harvestPage())

    const user = userEvent.setup()
    render(<HarvestListings />)

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Could not connect to the server.',
    )

    await user.click(
      screen.getByRole('button', { name: 'Try again' }),
    )

    expect(await screen.findByText('Tomato')).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()

    expect(listHarvestsMock).toHaveBeenCalledTimes(2)
  })

  it('requests the next page and replaces the displayed listings', async () => {
    const secondListing: HarvestListing = {
      ...listing,
      id: '4ea567d9-e87c-4c15-b7be-994c1784a168',
      cropName: 'Carrot',
    }

    const firstPage: HarvestPage = {
      ...harvestPage(),
      totalElements: 11,
      totalPages: 2,
      hasNext: true,
    }

    const secondPage: HarvestPage = {
      ...harvestPage([secondListing]),
      page: 1,
      totalElements: 11,
      totalPages: 2,
      hasPrevious: true,
    }

    listHarvestsMock
      .mockResolvedValueOnce(firstPage)
      .mockResolvedValueOnce(secondPage)

    const user = userEvent.setup()
    render(<HarvestListings />)

    await screen.findByText('Tomato')

    await user.click(
      screen.getByRole('button', { name: /^next$/i }),
    )

    expect(await screen.findByText('Carrot')).toBeInTheDocument()
    expect(screen.queryByText('Tomato')).not.toBeInTheDocument()

    expect(listHarvestsMock).toHaveBeenLastCalledWith(
      1,
      expect.any(AbortSignal),
    )

    expect(screen.getByRole('button', { name: /^previous$/i }))
      .toBeEnabled()

    expect(screen.getByRole('button', { name: /^next$/i }))
      .toBeDisabled()
  })
})