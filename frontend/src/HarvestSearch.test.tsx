import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import HarvestListings from './HarvestListings'
import {
  listHarvests,
  searchActiveFarmers,
  searchHarvests,
  updateHarvestStatus,
} from './harvestApi'
import type {
  HarvestFilterValues,
  HarvestListing,
  HarvestPage,
} from './harvestApi'

vi.mock('./harvestApi', () => ({
  listHarvests: vi.fn(),
  searchHarvests: vi.fn(),
  searchActiveFarmers: vi.fn(),
  createHarvest: vi.fn(),
  updateHarvestStatus: vi.fn(),
}))

const listMock = vi.mocked(listHarvests)
const searchMock = vi.mocked(searchHarvests)
const farmerSearchMock = vi.mocked(searchActiveFarmers)
const updateStatusMock = vi.mocked(updateHarvestStatus)

const listing: HarvestListing = {
  id: '56e21060-f3f5-4c77-aa7e-fdbd82ba791d',
  farmerId: '87bc0103-f7fb-432f-a219-292e928164c7',
  farmerName: 'Demo Farmer',
  district: 'Matale',
  cropName: 'Tomato',
  quantityKg: 125.5,
  expectedPricePerKg: 180,
  availableFrom: '2026-10-27',
  status: 'AVAILABLE',
  version: 0,
  createdAt: '2026-10-27T04:00:00Z',
  updatedAt: '2026-10-27T04:00:00Z',
}

const appliedFilters: HarvestFilterValues = {
  query: 'Tomato',
  district: 'Matale',
  status: 'AVAILABLE',
}

function page(content: HarvestListing[] = [listing]): HarvestPage {
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

async function openScreen() {
  const user = userEvent.setup()
  render(<HarvestListings />)
  await screen.findByText('Tomato')
  return user
}

async function applyFilters(
  user: ReturnType<typeof userEvent.setup>,
) {
  await user.type(
    screen.getByRole('textbox', { name: 'Search crop' }),
    '  Tomato  ',
  )

  await user.type(
    screen.getByRole('textbox', { name: 'Filter by district' }),
    '  Matale  ',
  )

  await user.selectOptions(
    screen.getByRole('combobox', { name: 'Listing status' }),
    'AVAILABLE',
  )

  await user.click(
    screen.getByRole('button', { name: 'Search harvests' }),
  )

  await waitFor(() => {
    expect(searchMock).toHaveBeenCalled()
    expect(screen.getByRole('button', { name: 'Refresh listings' }))
      .toBeEnabled()
  })
}

beforeEach(() => {
  listMock.mockReset()
  searchMock.mockReset()
  farmerSearchMock.mockReset()
  updateStatusMock.mockReset()

  listMock.mockResolvedValue(page())
  searchMock.mockResolvedValue(page())

  farmerSearchMock.mockResolvedValue({
    content: [],
    page: 0,
    size: 10,
    totalElements: 0,
    totalPages: 0,
    hasNext: false,
    hasPrevious: false,
  })
})

describe('Harvest search', () => {
  it('submits trimmed filters on page zero and retains them on refresh', async () => {
    const user = await openScreen()

    await applyFilters(user)

    expect(searchMock).toHaveBeenCalledExactlyOnceWith(
      0,
      appliedFilters,
      expect.any(AbortSignal),
    )

    expect(screen.getByText(
      'Crop contains: Tomato · District: Matale · Status: AVAILABLE',
    )).toBeInTheDocument()

    await user.click(
      screen.getByRole('button', { name: 'Refresh listings' }),
    )

    await waitFor(() => {
      expect(searchMock).toHaveBeenCalledTimes(2)
    })

    expect(searchMock).toHaveBeenLastCalledWith(
      0,
      appliedFilters,
      expect.any(AbortSignal),
    )
  })

  it('retains filters on the next page and resets the page for a new search', async () => {
    const carrot: HarvestListing = {
      ...listing,
      id: '4ea567d9-e87c-4c15-b7be-994c1784a168',
      cropName: 'Carrot',
    }

    searchMock
      .mockResolvedValueOnce({
        ...page(),
        totalElements: 11,
        totalPages: 2,
        hasNext: true,
      })
      .mockResolvedValueOnce({
        ...page([carrot]),
        page: 1,
        totalElements: 11,
        totalPages: 2,
        hasPrevious: true,
      })
      .mockResolvedValueOnce(page([carrot]))

    const user = await openScreen()
    await applyFilters(user)

    await user.click(
      screen.getByRole('button', { name: /^next$/i }),
    )

    expect(await screen.findByText('Carrot')).toBeInTheDocument()

    expect(searchMock).toHaveBeenLastCalledWith(
      1,
      appliedFilters,
      expect.any(AbortSignal),
    )

    const cropInput = screen.getByRole('textbox', {
      name: 'Search crop',
    })

    await user.clear(cropInput)
    await user.type(cropInput, 'Carrot')

    await user.click(
      screen.getByRole('button', { name: 'Search harvests' }),
    )

    await waitFor(() => {
      expect(searchMock).toHaveBeenCalledTimes(3)
    })

    expect(searchMock).toHaveBeenLastCalledWith(
      0,
      { ...appliedFilters, query: 'Carrot' },
      expect.any(AbortSignal),
    )
  })

  it('clears the fields and returns to unfiltered listings', async () => {
    const user = await openScreen()
    await applyFilters(user)

    await user.click(
      screen.getByRole('button', { name: 'Clear filters' }),
    )

    await waitFor(() => {
      expect(listMock).toHaveBeenCalledTimes(2)
    })

    expect(listMock).toHaveBeenLastCalledWith(
      0,
      expect.any(AbortSignal),
    )

    expect(screen.getByRole('textbox', { name: 'Search crop' }))
      .toHaveValue('')

    expect(screen.getByRole('textbox', { name: 'Filter by district' }))
      .toHaveValue('')

    expect(screen.getByRole('combobox', { name: 'Listing status' }))
      .toHaveValue('')

    expect(screen.getByText('Showing all listings')).toBeInTheDocument()
  })

  it('retries a failed search using the same applied filters', async () => {
    searchMock
      .mockRejectedValueOnce(new Error('Search is temporarily unavailable.'))
      .mockResolvedValueOnce(page())

    const user = await openScreen()
    await applyFilters(user)

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Search is temporarily unavailable.',
    )

    await user.click(
      screen.getByRole('button', { name: 'Try again' }),
    )

    expect(await screen.findByText('Tomato')).toBeInTheDocument()

    expect(searchMock).toHaveBeenCalledTimes(2)

    expect(searchMock).toHaveBeenLastCalledWith(
      0,
      appliedFilters,
      expect.any(AbortSignal),
    )

    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('reloads available results after marking a matching listing sold', async () => {
    searchMock
      .mockResolvedValueOnce(page())
      .mockResolvedValueOnce(page([]))

    updateStatusMock.mockResolvedValue({
      ...listing,
      status: 'SOLD',
      version: 1,
    })

    vi.spyOn(window, 'confirm').mockReturnValue(true)

    const user = await openScreen()
    await applyFilters(user)

    await user.click(
      screen.getByRole('button', { name: 'Mark Tomato as sold' }),
    )

    expect(
      await screen.findByText('No matching harvest listings'),
    ).toBeInTheDocument()

    expect(updateStatusMock).toHaveBeenCalledExactlyOnceWith(
      listing.id,
      'SOLD',
      listing.version,
    )

    expect(searchMock).toHaveBeenCalledTimes(2)

    expect(searchMock).toHaveBeenLastCalledWith(
      0,
      appliedFilters,
      expect.any(AbortSignal),
    )

    expect(screen.getByText('0 of 0 listings')).toBeInTheDocument()

    expect(screen.getByRole('combobox', { name: 'Listing status' }))
      .toHaveValue('AVAILABLE')

    expect(screen.queryByRole('button', {
      name: 'Mark Tomato as sold',
    })).not.toBeInTheDocument()
  })
})